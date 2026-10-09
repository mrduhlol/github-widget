package com.example.githubwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import com.example.githubwidget.data.Repository
import com.example.githubwidget.widget.RefreshWorker
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
class ContributionWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        paint(context, ids)
        Repository.init(context)
        if (Repository.hasUser) {
            RefreshWorker.schedule(context, Repository.refreshHours.value)
            if (Repository.isStale()) RefreshWorker.runOnce(context)
        }
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
        CoroutineScope(Dispatchers.Default).launch {
            try {
                WidgetUpdater.update(context, ids)
            } finally {
                pending.finish()
            }
        }
    }
}
