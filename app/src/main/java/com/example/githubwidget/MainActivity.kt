package com.example.githubwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val input = findViewById<EditText>(R.id.input_username)
        val status = findViewById<TextView>(R.id.text_status)
        val save = findViewById<Button>(R.id.btn_save)
        val refresh = findViewById<Button>(R.id.btn_refresh)
        val pin = findViewById<Button>(R.id.btn_add_widget)
        val card = findViewById<MaterialCardView>(R.id.card_preview)
        val previewTitle = findViewById<TextView>(R.id.preview_title)
        val previewSubtitle = findViewById<TextView>(R.id.preview_subtitle)
        val previewGraph = findViewById<ImageView>(R.id.preview_graph)
        val previewStatus = findViewById<TextView>(R.id.preview_status)

        input.setText(Prefs.getUsername(this))
        updateStatus(status)

        fun loadPreview(username: String) {
            if (username.isBlank()) {
                card.visibility = View.GONE
                return
            }
            card.visibility = View.VISIBLE
            previewTitle.text = "@$username"
            previewSubtitle.text = "Loading contributions…"
            previewStatus.text = ""
            previewGraph.setImageDrawable(null)
            Thread {
                try {
                    val result = GithubApi.fetch(username)
                    val bmp = GraphRenderer.render(result.days, scale = 2.5f)
                    runOnUiThread {
                        previewTitle.text = "@${result.username}"
                        val today = result.days.lastOrNull()
                        previewSubtitle.text =
                            "Today: ${today?.count ?: 0} • ${result.totalLastYear} in last year"
                        previewGraph.setImageBitmap(bmp)
                        previewStatus.text = "This is exactly what the widget shows."
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        previewSubtitle.text = "Couldn't load contributions"
                        previewStatus.text = (e.message ?: "Network error").take(120)
                    }
                }
            }.start()
        }

        // On open: if a username was saved before, show its contributions immediately.
        // If first install (blank), the input is focused and preview stays hidden
        // until the user enters a name.
        val saved = Prefs.getUsername(this)
        if (saved.isNotBlank()) loadPreview(saved)

        save.setOnClickListener {
            val u = input.text.toString().trim().trimStart('@')
            if (u.isEmpty()) {
                Toast.makeText(this, "Enter a GitHub username", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Prefs.setUsername(this, u)
            updateStatus(status)
            Prefs.requestRefresh(this)
            loadPreview(u)
            Toast.makeText(this, "Saved @$u — showing contributions…", Toast.LENGTH_SHORT).show()
        }

        refresh.setOnClickListener {
            val u = Prefs.getUsername(this).ifBlank { input.text.toString().trim().trimStart('@') }
            if (u.isBlank()) {
                Toast.makeText(this, "Enter a GitHub username first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            loadPreview(u)
            Prefs.requestRefresh(this)
            Toast.makeText(this, "Refreshing…", Toast.LENGTH_SHORT).show()
        }

        // Android 8+: offer one-tap pin if the launcher supports it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(AppWidgetManager::class.java)
            val provider = ComponentName(this, ContributionWidgetProvider::class.java)
            pin.isEnabled = mgr.isRequestPinAppWidgetSupported
            pin.setOnClickListener {
                if (mgr.isRequestPinAppWidgetSupported) {
                    mgr.requestPinAppWidget(provider, null, null)
                } else {
                    Toast.makeText(this, "Launcher doesn't support pinning", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            pin.isEnabled = false
            pin.text = "Add widget via homescreen (long-press)"
        }
    }

    private fun updateStatus(status: TextView) {
        val u = Prefs.getUsername(this)
        status.text = if (u.isBlank()) {
            "No username set.\n\n1. Enter your GitHub username above\n2. Tap Save\n3. Long-press homescreen → Widgets → GitHub Contributions"
        } else {
            "Tracking: @$u\n\nTo put it on your homescreen:\nLong-press homescreen → Widgets → GitHub Contributions"
        }
    }
}
