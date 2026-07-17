package eu.kanade.tachiyomi.ui.mod.updatewatch.worker

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.workDataOf
import eu.kanade.tachiyomi.util.system.workManager
import java.util.concurrent.TimeUnit

object UpdateWatchRefreshScheduler {
    private const val WORK_NAME = "UpdateWatchRefresh"
    private const val MANUAL_WORK_NAME = "UpdateWatchRefreshManual"

    fun setupTask(context: Context) {
        if (!isMainProcess(context)) return

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
