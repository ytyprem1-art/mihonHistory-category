package eu.kanade.tachiyomi.ui.mod.updatewatch.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import eu.kanade.tachiyomi.util.system.workManager
import java.util.concurrent.TimeUnit

object UpdateWatchRefreshScheduler {
    private const val WORK_NAME = "UpdateWatchRefresh"
    private const val MANUAL_WORK_NAME = "UpdateWatchRefreshManual"

    fun setupTask(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<UpdateWatchRefreshWorker>(
            2, TimeUnit.HOURS,
            30, TimeUnit.MINUTES,
        )
            .addTag(WORK_NAME)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
            .build()

        context.workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<UpdateWatchRefreshWorker>()
            .addTag(MANUAL_WORK_NAME)
            .build()

        context.workManager.enqueueUniqueWork(
            MANUAL_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
