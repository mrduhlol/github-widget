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
import com.example.githubwidget.data.ReviewInbox
import java.util.concurrent.TimeUnit

/** Background refresh: downloads fresh data and repaints the widgets. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Repository.init(applicationContext)
        if (!Repository.hasUser) return Result.success()
        val inboxIds = WidgetUpdater.reviewInboxIds(applicationContext)
        val inboxOk = if (inboxIds.isNotEmpty()) {
            ReviewInbox.refresh(applicationContext, Repository.username.value)
        } else {
            true
        }
        val outcome = Repository.refreshWithOutcome(updateWidgets = false)
        when {
            outcome == Repository.RefreshOutcome.UPDATED || Repository.data.value == null -> {
                WidgetUpdater.updateAll(applicationContext)
            }
            else -> {
                val toUpdate = (WidgetUpdater.streakIds(applicationContext).toSet() + inboxIds.toSet()).toIntArray()
                WidgetUpdater.update(applicationContext, toUpdate)
            }
        }
        val transientFailure = outcome == Repository.RefreshOutcome.TRANSIENT_FAILURE || !inboxOk
        return if (transientFailure && runAttemptCount < MAX_RETRY_ATTEMPTS) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    companion object {
        private const val PERIODIC = "refresh_periodic"
        private const val ONCE = "refresh_once"
        internal const val MAX_RETRY_ATTEMPTS = 3

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

        internal fun runOncePolicy(replace: Boolean): ExistingWorkPolicy =
            if (replace) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP

        /** Refresh as soon as the network allows (e.g. a widget was just added). */
        fun runOnce(context: Context, replace: Boolean = false) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONCE,
                runOncePolicy(replace),
                OneTimeWorkRequestBuilder<RefreshWorker>().setConstraints(online).build(),
            )
        }

        fun cancel(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(PERIODIC)
            wm.cancelUniqueWork(ONCE)
        }
    }
}
