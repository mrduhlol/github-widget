package com.example.githubwidget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.githubwidget.data.Repository
import com.example.githubwidget.ui.AppRoot
import com.example.githubwidget.ui.theme.GhTheme
import com.example.githubwidget.widget.RefreshWorker

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Repository.init(this)
        setContent {
            GhTheme {
                AppRoot()
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
