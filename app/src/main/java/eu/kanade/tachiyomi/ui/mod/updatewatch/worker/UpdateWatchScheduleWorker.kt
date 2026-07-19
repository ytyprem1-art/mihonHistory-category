package eu.kanade.tachiyomi.ui.mod.updatewatch.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class UpdateWatchScheduleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val scheduledAt = inputData.getLong(UpdateWatchRefreshScheduler.KEY_SCHEDULED_AT, 0L).takeIf { it > 0 }
        UpdateWatchDiagnosticsManager.logEvent("Launcher started", scheduledAt)
        UpdateWatchRefreshScheduler.launchRunNow(applicationContext, scheduledAt)
        return Result.success()
    }
}
