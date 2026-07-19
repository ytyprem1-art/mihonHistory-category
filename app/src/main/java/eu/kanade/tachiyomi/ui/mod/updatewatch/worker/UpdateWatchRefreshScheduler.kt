package eu.kanade.tachiyomi.ui.mod.updatewatch.worker

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf
import eu.kanade.tachiyomi.ui.mod.updatewatch.helper.UpdateWatchRefreshHelper
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.history.interactor.ManageUpdateWatch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit
import kotlin.random.Random

object UpdateWatchRefreshScheduler {
    const val WORK_NAME_SCHEDULE = "UpdateWatchRefreshSchedule"
    const val WORK_NAME_RUN = "UpdateWatchRefreshRun"
    const val MANUAL_WORK_NAME = "UpdateWatchRefreshManual"
    const val KEY_SCHEDULED_AT = "scheduled_at"

    private val schedulerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun setupTask(context: Context, skipRunCheck: Boolean = false) {
        if (!isMainProcess(context)) return

        schedulerScope.launch {
            if (!skipRunCheck) {
                // If a refresh is currently running or retrying, don't schedule a new one.
                // Let the running worker call setupTask(skipRunCheck = true) when it finishes.
                val workInfos = context.workManager.getWorkInfosForUniqueWork(WORK_NAME_RUN).get()
                val isActive = workInfos.any { !it.state.isFinished }
                if (isActive) {
                    UpdateWatchDiagnosticsManager.logEvent("Schedule/Launcher ignored: a run is already active")
                    return@launch
                }
            }

            val manageUpdateWatch: ManageUpdateWatch = Injekt.get()
            val chapterRepository: ChapterRepository = Injekt.get()

            val trackedManga = manageUpdateWatch.subscribeAll().first()
            if (trackedManga.none { it.backgroundRefreshEnabled && !it.isPaused }) {
                context.workManager.cancelUniqueWork(WORK_NAME_SCHEDULE)
                UpdateWatchDiagnosticsManager.logEvent("Schedule cancelled: no active Auto Refresh manga")
                return@launch
            }

            val latestChapterDates = trackedManga.associate { tracking ->
                val chapters = chapterRepository.getChapterByMangaId(tracking.mangaId)
                val latest = chapters.filter { it.dateUpload > 0 }.maxByOrNull { it.dateUpload }
                tracking.mangaId to (latest?.dateUpload ?: 0L)
            }

            val earliest = UpdateWatchRefreshHelper.getEarliestNextEligibleAt(trackedManga, latestChapterDates)
            val now = System.currentTimeMillis()

            if (earliest == null) {
                context.workManager.cancelUniqueWork(WORK_NAME_SCHEDULE)
                UpdateWatchDiagnosticsManager.logEvent("Schedule cancelled: no future eligible timestamp")
                return@launch
            }

            // IDEMPOTENCY CHECK
            val diagPrefs = Injekt.get<UpdateWatchDiagnosticsPreferences>()
            val lastEarliest = diagPrefs.lastEarliestEligibleAt.get()
            val lastScheduled = diagPrefs.lastScheduledAt.get()

            val existingWork = context.workManager.getWorkInfosForUniqueWork(WORK_NAME_SCHEDULE).get()
                .firstOrNull { !it.state.isFinished }

            if (existingWork != null && lastEarliest == earliest && lastScheduled > now) {
                UpdateWatchDiagnosticsManager.logEvent("Schedule kept: existing target is valid (Target: ${UpdateWatchDiagnosticsManager.formatTimestamp(lastScheduled)})")
                return@launch
            }

            val marginMillis = Random.nextLong(5, 9) * 60 * 1000L
            val delayMillis = UpdateWatchRefreshHelper.calculateRescheduleDelay(earliest, now, marginMillis)
            if (delayMillis == null) return@launch

            val scheduledAt = now + delayMillis
            val oldTarget = if (lastScheduled > 0) " (Old target: ${UpdateWatchDiagnosticsManager.formatTimestamp(lastScheduled)})" else ""
            UpdateWatchDiagnosticsManager.logEvent(
                eventName = if (skipRunCheck) "Schedule created (self-reschedule)" else "Schedule replaced$oldTarget",
                scheduledAt = scheduledAt
            )

            diagPrefs.lastEarliestEligibleAt.set(earliest)
            diagPrefs.lastScheduledAt.set(scheduledAt)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = OneTimeWorkRequestBuilder<UpdateWatchScheduleWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .addTag(WORK_NAME_SCHEDULE)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                .setInputData(workDataOf(KEY_SCHEDULED_AT to scheduledAt))
                .build()

            context.workManager.enqueueUniqueWork(
                WORK_NAME_SCHEDULE,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }

    /**
     * Enqueues the actual refresh worker. Called by UpdateWatchScheduleWorker.
     */
    fun launchRunNow(context: Context, scheduledAt: Long? = null) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = OneTimeWorkRequestBuilder<UpdateWatchRefreshWorker>()
            .addTag(WORK_NAME_RUN)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
            .apply {
                if (scheduledAt != null) {
                    setInputData(workDataOf(KEY_SCHEDULED_AT to scheduledAt))
                }
            }
            .build()

        context.workManager.enqueueUniqueWork(
            WORK_NAME_RUN,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun runNow(context: Context, simulationMode: Int = UpdateWatchRefreshWorker.SIM_NONE) {
        val request = OneTimeWorkRequestBuilder<UpdateWatchRefreshWorker>()
            .addTag(MANUAL_WORK_NAME)
            .setInputData(workDataOf(UpdateWatchRefreshWorker.KEY_SIMULATION_MODE to simulationMode))
            .build()

        context.workManager.enqueueUniqueWork(
            MANUAL_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun isMainProcess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageName == Application.getProcessName()
        } else {
            val pid = android.os.Process.myPid()
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.runningAppProcesses?.any { it.pid == pid && it.processName == context.packageName } == true
        }
    }
}
