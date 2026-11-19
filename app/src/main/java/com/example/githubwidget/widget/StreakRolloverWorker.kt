package com.example.githubwidget.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.githubwidget.data.Repository
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

/** Repaints from the calendar at UTC rollover, even if the device is offline. */
class StreakRolloverWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        Repository.init(applicationContext)
        val ids = WidgetUpdater.streakIds(applicationContext)
        if (!Repository.hasUser || ids.isEmpty()) return Result.success()
        WidgetUpdater.update(applicationContext, ids)
        RefreshWorker.runOnce(applicationContext)
        return Result.success()
    }

    companion object {
        internal fun nextBoundary(now: Instant): Instant =
            now.atOffset(ZoneOffset.UTC).toLocalDate().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)

        internal fun schedule(context: Context) {
            val now = Instant.now()
            val next = nextBoundary(now)
            val request = OneTimeWorkRequestBuilder<StreakRolloverWorker>()
                .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("streak_rollover_${next.epochSecond}",
                ExistingWorkPolicy.KEEP, request)
        }
    }
}
