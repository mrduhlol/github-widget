package com.example.githubwidget

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import com.example.githubwidget.design.WidgetLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.githubwidget.data.Repository
import com.example.githubwidget.ui.AppRoot
import com.example.githubwidget.ui.theme.GhTheme
import com.example.githubwidget.widget.RefreshWorker

class MainActivity : ComponentActivity() {
    private var widgetOpenRequest by mutableIntStateOf(0)

    companion object {
        const val EXTRA_WIDGET_LAYOUT = "widget_layout"
    }

    private fun openWidget(intent: Intent?) {
        val layout = WidgetLayout.entries.firstOrNull { it.name == intent?.getStringExtra(EXTRA_WIDGET_LAYOUT) } ?: return
        Repository.updateDesign { it.copy(layout = layout) }
        widgetOpenRequest++
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openWidget(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Repository.init(this)
        openWidget(intent)
        setContent {
            GhTheme {
                AppRoot(widgetOpenRequest)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Opening the app is the user's way of saying "show me the latest".
        if (Repository.hasUser) {
            if (Repository.isStale(maxAgeMinutes = 10)) Repository.refreshAsync()
            RefreshWorker.schedule(this, Repository.refreshHours.value)
        }
    }
}
