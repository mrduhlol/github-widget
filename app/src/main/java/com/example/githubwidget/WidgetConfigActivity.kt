package com.example.githubwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import com.example.githubwidget.data.Repository
import com.example.githubwidget.widget.WidgetUpdater

/**
 * Launched by the home screen when a widget is added (Android 11 and older)
 * or when the user picks "Customize" on a widget.
 *
 * There's nothing to fill in here: the widget is accepted right away and
 * the app opens if there's something to do (sign in, or customize).
 */
class WidgetConfigActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        Repository.init(this)
        val reconfigure = WidgetUpdater.wasDrawn(this, id)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        WidgetUpdater.update(this, intArrayOf(id))
        if (!Repository.hasUser || reconfigure) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }
}
