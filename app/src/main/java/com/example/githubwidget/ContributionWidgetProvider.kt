package com.example.githubwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.example.githubwidget.data.Repository
import com.example.githubwidget.widget.RefreshWorker
import com.example.githubwidget.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
        if (intent.action == ACTION_PINNED) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val mgr = AppWidgetManager.getInstance(context)
            val provider = intent.component ?: return
            val ids = if (mgr.getAppWidgetInfo(id)?.provider == provider) intArrayOf(id)
                else mgr.getAppWidgetIds(provider)
            if (ids.isNotEmpty()) onUpdate(context, mgr, ids)
        }
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        paint(context, ids)
        scheduleRefresh(context, this is ReviewInboxWidgetProvider || this is StreakWidgetProvider)
    }

    /** Redraw at the new size when the user resizes the widget. */
    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle) {
        paint(context, intArrayOf(id))
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        WidgetUpdater.forget(context, ids)
    }

    private fun paint(context: Context, ids: IntArray) {
        val pending = goAsync()
        val appContext = context.applicationContext
        renderScope.launch {
            try {
                WidgetUpdater.update(appContext, ids)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val renderScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        internal fun scheduleRefresh(context: Context, force: Boolean) {
            Repository.init(context)
            if (!Repository.hasUser) return
            RefreshWorker.schedule(context, Repository.refreshHours.value)
            if (force || Repository.isStale()) RefreshWorker.runOnce(context)
        }

        internal const val ACTION_PINNED = "com.example.githubwidget.action.PINNED"
    }
}
