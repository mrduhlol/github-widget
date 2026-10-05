package com.example.githubwidget

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.io.File

/** Settings: data, look transfer, reset, privacy, about. */
class SettingsActivity : AppCompatActivity() {

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        try {
            val info = packageManager.getPackageInfo(packageName, 0)
            findViewById<TextView>(R.id.set_version).text =
                "GH-widgets v${info.versionName} (${info.versionCode})"
        } catch (_: Exception) {
            // Version label keeps its static fallback.
        }

        findViewById<MaterialButton>(R.id.set_refresh).setOnClickListener {
            val u = Prefs.getUsername(this)
            if (u.isBlank()) {
                Toast.makeText(this, "Set a GitHub username first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Prefs.requestRefresh(this)
            Toast.makeText(this, "Refreshing widgets…", Toast.LENGTH_SHORT).show()
        }

        findViewById<MaterialButton>(R.id.set_clear_cache).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear cached data?")
                .setMessage("The saved contribution graph is removed. Widgets show a loading state until the next refresh.")
                .setPositiveButton("Clear") { _, _ ->
                    getSharedPreferences("gh_widget_cache", MODE_PRIVATE).edit().clear().apply()
                    Prefs.requestRefresh(this)
                    Toast.makeText(this, "Cache cleared", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        findViewById<MaterialButton>(R.id.set_export).setOnClickListener {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, Studio.exportLook(this@SettingsActivity))
            }
            startActivity(Intent.createChooser(intent, "Share look"))
        }

        findViewById<MaterialButton>(R.id.set_import).setOnClickListener {
            val field = EditText(this).apply { hint = "Paste a shared look" }
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val pad = dp(20)
                setPadding(pad, dp(8), pad, dp(8))
                addView(field)
            }
            AlertDialog.Builder(this)
                .setTitle("Import look")
                .setView(box)
                .setPositiveButton("Import") { _, _ ->
                    val err = Studio.importLook(this, field.text.toString())
                    if (err == null) {
                        Prefs.requestRepaint(this)
                        Toast.makeText(this, "Look imported", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, err, Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        findViewById<MaterialButton>(R.id.set_reset_look).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset look?")
                .setMessage("Widget style, theme, background and graph settings return to defaults. Your username and saved data are kept.")
                .setPositiveButton("Reset") { _, _ ->
                    WidgetPrefs.resetToDefaults(this)
                    Studio.resetBackground(this)
                    Studio.resetGraph(this)
                    Studio.setCustomLabel(this, "")
                    Studio.setShowActiveDays(this, false)
                    Prefs.requestRepaint(this)
                    Toast.makeText(this, "Look reset", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        findViewById<MaterialButton>(R.id.set_reset_all).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset everything?")
                .setMessage("Clears your username, look, per-widget setups and cached data. Drawings are kept.")
                .setPositiveButton("Reset everything") { _, _ ->
                    resetEverything()
                    Toast.makeText(this, "Everything reset", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        fun openUrl(url: String) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (_: Exception) {
                Toast.makeText(this, "Couldn't open the link.", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<MaterialButton>(R.id.set_repo).setOnClickListener {
            openUrl("https://github.com/mrduhlol/github-widget")
        }
        findViewById<MaterialButton>(R.id.set_issue).setOnClickListener {
            openUrl("https://github.com/mrduhlol/github-widget/issues/new")
        }
    }

    private fun resetEverything() {
        for (name in listOf("github_widget_prefs", "gh_widget_style", "gh_studio", "gh_widget_cache")) {
            getSharedPreferences(name, MODE_PRIVATE).edit().clear().apply()
        }
        try {
            val dir = File(applicationInfo.dataDir, "shared_prefs")
            dir.listFiles { f -> f.name.startsWith("gh_widget_") }
                ?.forEach { it.delete() }
        } catch (_: Exception) {
            // Per-widget files are best-effort; globals already reset.
        }
        Prefs.requestRefresh(this)
    }
}
