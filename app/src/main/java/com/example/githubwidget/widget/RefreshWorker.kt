package com.example.githubwidget.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.githubwidget.data.Repository
import java.util.concurrent.TimeUnit

/** Background refresh: downloads fresh data and repaints the widgets. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Repository.init(applicationContext)
        if (!Repository.hasUser) return Result.success()
        Repository.refresh()
        // Failures keep the cached graph on screen; the next run tries again.
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "refresh_periodic"
        private const val ONCE = "refresh_once"

        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context, hours: Int, replace: Boolean = false) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(hours.toLong(), TimeUnit.HOURS)
                .setConstraints(online)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC,
                if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** Refresh as soon as the network allows (e.g. a widget was just added). */
        fun runOnce(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONCE,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<RefreshWorker>().setConstraints(online).build(),
            )
        }
    }
}
