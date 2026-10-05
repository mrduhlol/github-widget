package com.example.githubwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
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
import java.text.NumberFormat
import java.util.Locale

/** V2 home: who you are, your graph, and where to go next. */
class MainActivity : AppCompatActivity() {

    private var tintBmp: android.graphics.Bitmap? = null

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun fmt(n: Int): String = NumberFormat.getInstance(Locale.US).format(n)

    private fun sampleTint(weeks: Int) {
        tintBmp?.let { if (!it.isRecycled) it.recycle() }
        tintBmp = null
        if (!Studio.imageThroughGraph(this)) return
        if (Studio.getBgType(this) != Studio.BG_IMAGE) return
        val uri = Studio.getImageUri(this)
        if (uri.isEmpty()) return
        tintBmp = CardRenderer.sampleImage(this, uri, weeks, 7, Studio.SCALE_FILL)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val user = findViewById<TextView>(R.id.home_user)
        val updated = findViewById<TextView>(R.id.home_updated)
        val onboard = findViewById<MaterialCardView>(R.id.home_onboard)
        val onboardInput = findViewById<EditText>(R.id.home_username)
        val onboardStart = findViewById<Button>(R.id.home_start)
        val card = findViewById<MaterialCardView>(R.id.home_card)
        val bg = findViewById<ImageView>(R.id.home_bg)
        val subtitle = findViewById<TextView>(R.id.home_subtitle)
        val total = findViewById<TextView>(R.id.home_total)
        val graph = findViewById<ImageView>(R.id.home_graph)
        val streak = findViewById<TextView>(R.id.home_streak)
        val week = findViewById<TextView>(R.id.home_week)
        val active = findViewById<TextView>(R.id.home_active)

        fun theme(): GraphTheme =
            Themes.resolve(Prefs.getThemeId(this), Prefs.getCustomColor(this))

        fun paintCard() {
            card.radius = dp(WidgetPrefs.getCorners(this)).toFloat()
            Thread {
                try {
                    val bmp = CardRenderer.card(
                        this, 1024, 512,
                        dp(WidgetPrefs.getCorners(this)).toFloat() * 3f,
                        Color.parseColor("#30363D")
                    )
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        bg.setImageBitmap(bmp)
                        bg.visibility = View.VISIBLE
                        card.setCardBackgroundColor(Color.TRANSPARENT)
                    }
                } catch (_: Exception) {
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        bg.visibility = View.GONE
                        card.setCardBackgroundColor(Themes.cardColor(Prefs.getOpacity(this)))
                    }
                }
            }.start()
        }

        fun showResult(result: ContributionsResult, ago: String) {
            val t = theme()
            val weeks = WidgetPrefs.rangeWeeks(WidgetPrefs.getRange(this))
            sampleTint(weeks)
            graph.setImageBitmap(
                GraphRenderer.render(
                    result.days,
                    scale = 2.5f,
                    colors = Studio.effectiveLevels(this, t),
                    maxWeeks = weeks,
                    showMonthLabels = true,
                    cornerRadius = WidgetPrefs.shapeRadiusFactor(WidgetPrefs.getShape(this)),
                    gapScale = WidgetPrefs.getSpacing(this),
                    cellScale = Studio.getCellSize(this),
                    tint = tintBmp,
                    tintAmount = 0.35f
                )
            )
            graph.contentDescription =
                "@${result.username}: ${fmt(result.totalLastYear)} contributions in the last year."
            user.text = "@${result.username}"
            updated.text = "Updated $ago"
            val a = Analytics.compute(result.days, result.totalLastYear)
            val today = result.days.lastOrNull()?.count ?: 0
            subtitle.text = "Today: $today • ${a.currentStreak}d streak"
            total.text = fmt(result.totalLastYear)
            total.setTextColor(t.accent)
            streak.text = "${a.currentStreak}"
            streak.setTextColor(t.accent)
            week.text = fmt(a.thisWeek)
            active.text = fmt(a.activeDays)
            paintCard()
        }

        fun load(username: String) {
            if (username.isBlank()) {
                onboard.visibility = View.VISIBLE
                card.visibility = View.GONE
                user.text = "GH-widgets"
                updated.text = ""
                return
            }
            onboard.visibility = View.GONE
            card.visibility = View.VISIBLE

            val cached = Cache.load(this)
            val hasCache = cached != null && cached.first.username.equals(username, ignoreCase = true)
            if (hasCache) {
                showResult(cached!!.first, TimeAgo.format(cached.second))
                subtitle.text = "${subtitle.text} • refreshing…"
            } else {
                user.text = "@$username"
                updated.text = ""
                subtitle.text = "Loading contributions…"
                graph.setImageDrawable(null)
                total.text = "–"
                streak.text = "–"
                week.text = "–"
                active.text = "–"
                paintCard()
            }

            Thread {
                try {
                    val result = GithubApi.fetch(username)
                    Cache.save(this, result)
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        showResult(result, TimeAgo.format(System.currentTimeMillis()))
                        Prefs.requestRepaint(this)
                    }
                } catch (e: UserNotFoundException) {
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        if (!hasCache) {
                            subtitle.text = "User not found"
                            updated.text = e.message ?: ""
                        } else {
                            updated.text = "${e.message} Showing saved data."
                        }
                    }
                } catch (_: Exception) {
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        if (!hasCache) {
                            subtitle.text = "No connection"
                            updated.text = "Connect to the internet and tap Refresh."
                        } else {
                            val ts = Cache.load(this)?.second ?: 0L
                            updated.text = "Couldn't refresh — showing data from ${TimeAgo.format(ts)}."
                        }
                    }
                }
            }.start()
        }

        fun saveUsername(raw: String): Boolean {
            val u = raw.trim().trimStart('@')
            if (u.isEmpty()) {
                Toast.makeText(this, "Enter a GitHub username", Toast.LENGTH_SHORT).show()
                return false
            }
            Prefs.setUsername(this, u)
            Prefs.requestRefresh(this)
            load(u)
            return true
        }

        onboardStart.setOnClickListener {
            if (saveUsername(onboardInput.text.toString())) {
                Toast.makeText(this, "Graph loading…", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.home_studio).setOnClickListener {
            startActivity(Intent(this, StudioActivity::class.java))
        }
        findViewById<Button>(R.id.home_activity).setOnClickListener {
            startActivity(Intent(this, ActivityActivity::class.java))
        }
        findViewById<Button>(R.id.home_share).setOnClickListener {
            shareFromHome()
        }
        findViewById<Button>(R.id.home_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.home_refresh).setOnClickListener {
            val u = Prefs.getUsername(this)
            if (u.isBlank()) {
                Toast.makeText(this, "Enter a GitHub username first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            load(u)
            Prefs.requestRefresh(this)
            Toast.makeText(this, "Refreshing…", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.home_switch).setOnClickListener {
            onboard.visibility = View.VISIBLE
            onboardInput.setText(Prefs.getUsername(this))
            onboardInput.requestFocus()
        }

        val pin = findViewById<Button>(R.id.home_add_widget)
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

        load(Prefs.getUsername(this))
    }

    override fun onResume() {
        super.onResume()
        // The Studio may have changed the look while we were away.
        val u = Prefs.getUsername(this)
        if (u.isNotBlank()) {
            val cached = Cache.load(this)
            if (cached != null && cached.first.username.equals(u, ignoreCase = true)) {
                // Repaint through the normal path.
                recreate()
            }
        }
    }

    private fun shareFromHome() {
        val username = Prefs.getUsername(this)
        val cached = Cache.load(this)
        if (username.isBlank() || cached == null ||
            !cached.first.username.equals(username, ignoreCase = true)
        ) {
            Toast.makeText(this, "Load your graph first", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "Generating card…", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val t = Themes.resolve(Prefs.getThemeId(this), Prefs.getCustomColor(this))
                val bmp = ShareCard.render(
                    cached.first, t,
                    ShareCard.Options(
                        dark = true,
                        showUsername = true,
                        showTotal = WidgetPrefs.showTotal(this),
                        showStreak = WidgetPrefs.showStreak(this)
                    ),
                    cornerRadius = WidgetPrefs.shapeRadiusFactor(WidgetPrefs.getShape(this)),
                    gapScale = WidgetPrefs.getSpacing(this),
                    customLevels = Studio.effectiveLevels(this, t)
                )
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    ShareCard.share(this, bmp)
                    bmp.recycle()
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    Toast.makeText(this, "Couldn't generate the card.", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}
