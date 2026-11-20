package com.example.githubwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityManager
import com.example.githubwidget.data.Repository
import com.example.githubwidget.widget.RefreshWorker
import com.example.githubwidget.widget.WidgetKind
import com.example.githubwidget.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The home-screen widget. Paints instantly from the local cache, then lets
 * [RefreshWorker] fetch fresh data in the background.
 *
 * The class name is kept from v2 so widgets already placed keep working.
 */
open class ContributionWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH -> {
                Repository.init(context)
                RefreshWorker.runOnce(context)
            }
            ACTION_CONTRIBUTION_TAP -> onContributionTap(context, intent)
            ACTION_REPLAY -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                val pending = goAsync()
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        WidgetUpdater.replay(context, id)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    private fun onContributionTap(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider != WidgetKind.CONTRIBUTIONS.component(context)) return
        val now = SystemClock.uptimeMillis()
        val previous = pendingTaps.remove(id)
        val accessibility = context.getSystemService(AccessibilityManager::class.java)
        if (accessibility.isTouchExplorationEnabled || (previous != null && now - previous < tapTimeout)) {
            // Launch immediately while the user-initiated widget PendingIntent allows it.
            context.startActivity(Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_WIDGET_STATS, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
            return
        }
        pendingTaps[id] = now
        val app = context.applicationContext
        // Finish handling the click so Android can deliver the next click during the tap window.
        Handler(Looper.getMainLooper()).postAtTime({
            if (pendingTaps[id] == now) {
                pendingTaps.remove(id)
                app.sendBroadcast(Intent(app, ContributionWidgetProvider::class.java)
                    .setAction(ACTION_REPLAY)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
            }
        }, now + tapTimeout)
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        paint(context, ids)
        Repository.init(context)
        if (Repository.hasUser) {
            RefreshWorker.schedule(context, Repository.refreshHours.value)
            if (this is ReviewInboxWidgetProvider || this is StreakWidgetProvider || Repository.isStale()) {
                RefreshWorker.runOnce(context)
            }
        }
    }

    /** Redraw at the new size when the user resizes the widget. */
    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle) {
        paint(context, intArrayOf(id))
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { pendingTaps.remove(it) }
        WidgetUpdater.forget(context, ids)
    }

    private fun paint(context: Context, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                WidgetUpdater.update(context, ids)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.example.githubwidget.action.REFRESH"
        internal const val ACTION_CONTRIBUTION_TAP = "com.example.githubwidget.action.CONTRIBUTION_TAP"
        private const val ACTION_REPLAY = "com.example.githubwidget.action.REPLAY_CONTRIBUTIONS"
        private val tapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()
        private val pendingTaps = mutableMapOf<Int, Long>()
    }
}
