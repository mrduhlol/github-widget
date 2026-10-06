package com.example.githubwidget

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.checkbox.MaterialCheckBox
import java.text.NumberFormat
import java.util.Locale

class ActivityActivity : AppCompatActivity() {

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun fmt(n: Int): String = NumberFormat.getInstance(Locale.US).format(n)

    private fun fmt1(d: Double): String =
        if (d >= 10) "${d.toInt()}" else String.format(Locale.US, "%.1f", d)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_activity)

        val title = findViewById<TextView>(R.id.an_title)
        val updated = findViewById<TextView>(R.id.an_updated)
        val total = findViewById<TextView>(R.id.an_total)
        val week = findViewById<TextView>(R.id.an_week)
        val month = findViewById<TextView>(R.id.an_month)
        val avg = findViewById<TextView>(R.id.an_avg)
        val active = findViewById<TextView>(R.id.an_active)
        val best = findViewById<TextView>(R.id.an_best)
        val streak = findViewById<TextView>(R.id.an_streak)
        val longest = findViewById<TextView>(R.id.an_longest)
        val milestone = findViewById<TextView>(R.id.an_milestone)
        val rowsWeekday = findViewById<LinearLayout>(R.id.rows_weekday)
        val rowsInsights = findViewById<LinearLayout>(R.id.rows_insights)
        val rowsAchievements = findViewById<LinearLayout>(R.id.rows_achievements)
        val share = findViewById<Button>(R.id.btn_share)

        val username = Prefs.getUsername(this)
        if (username.isBlank()) {
            title.text = "Activity"
            updated.text = "Set a username in GH-widgets first."
            share.visibility = View.GONE
            showInsights(rowsInsights, listOf("Open GH-widgets, enter your GitHub username and tap Save."))
            return
        }

        val cached = Cache.load(this)
        if (cached == null || !cached.first.username.equals(username, ignoreCase = true)) {
            title.text = "Activity — @$username"
            updated.text = "No data yet."
            share.visibility = View.GONE
            showInsights(
                rowsInsights,
                listOf("Connect to the internet and open GH-widgets once to fetch your data.")
            )
            return
        }

        val (result, ts) = cached
        title.text = "Activity — @${result.username}"
        updated.text = "Updated ${TimeAgo.format(ts)}"
        paintAnalytics(result, total, week, month, avg, active, best, streak, longest, milestone)
        showWeekdays(rowsWeekday, result)
        val analytics = Analytics.compute(result.days, result.totalLastYear)
        showRecords(findViewById(R.id.rows_records), result)
        showInsights(rowsInsights, Analytics.insights(analytics))
        showAchievements(rowsAchievements, result.totalLastYear, analytics)

        share.setOnClickListener { openShareOptions(result) }
    }

    private fun paintAnalytics(
        result: ContributionsResult,
        total: TextView,
        week: TextView,
        month: TextView,
        avg: TextView,
        active: TextView,
        best: TextView,
        streak: TextView,
        longest: TextView,
        milestone: TextView
    ) {
        val a = Analytics.compute(result.days, result.totalLastYear)
        total.text = fmt(a.total)
        week.text = fmt(a.thisWeek)
        month.text = fmt(a.thisMonth)
        avg.text = fmt1(a.avgPerActiveDay)
        active.text = fmt(a.activeDays)
        best.text = fmt(Stats.compute(result.days).bestCount)
        streak.text = if (a.currentStreak > 0) "${a.currentStreak} days" else "—"
        longest.text = if (a.longestStreak > 0) "${a.longestStreak} days" else "—"

        val reached = Achievements.milestoneReached(a.longestStreak)
        val next = Achievements.nextMilestone(a.longestStreak)
        milestone.text = when {
            next > 0 && a.currentStreak > 0 ->
                "${a.currentStreak} days to go for the $next-day milestone."
            reached > 0 -> "Milestone reached: $reached-day streak."
            next > 0 -> "Next milestone: $next-day streak."
            else -> "All streak milestones reached."
        }
    }

    private fun showWeekdays(container: LinearLayout, result: ContributionsResult) {
        container.removeAllViews()
        val a = Analytics.compute(result.days, result.totalLastYear)
        val max = a.weekdayTotals.maxOrNull()?.coerceAtLeast(1) ?: 1
        // Monday-first order, like a work week.
        val order = listOf(1, 2, 3, 4, 5, 6, 0)
        for (dow in order) {
            val count = a.weekdayTotals[dow]
            val blocks = if (count > 0) ((count * 10f / max).toInt().coerceAtLeast(1)) else 0
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, dp(3), 0, dp(3))
            }
            row.addView(TextView(this).apply {
                text = Analytics.weekdayName(dow).take(3)
                textSize = 12f
                setTextColor(Color.parseColor("#8B949E"))
                layoutParams = LinearLayout.LayoutParams(dp(44), LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            row.addView(TextView(this).apply {
                text = "█".repeat(blocks)
                textSize = 12f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.parseColor("#39D353"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                contentDescription = "${Analytics.weekdayName(dow)}: $count contributions"
            })
            row.addView(TextView(this).apply {
                text = fmt(count)
                textSize = 12f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.parseColor("#F0F6FC"))
            })
            container.addView(row)
        }
        if (a.mostActiveWeekday >= 0) {
            container.addView(TextView(this).apply {
                text = "Most active: ${Analytics.weekdayName(a.mostActiveWeekday)}"
                textSize = 11f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.parseColor("#8B949E"))
                setPadding(0, dp(8), 0, 0)
            })
        }
    }

    private fun showRecords(container: LinearLayout, result: ContributionsResult) {
        container.removeAllViews()
        val r = Achievements.records(result.days)
        if (r.bestCount == 0) {
            container.addView(TextView(this).apply {
                text = "No records yet — contribute to set your first."
                textSize = 12f
                setTextColor(Color.parseColor("#8B949E"))
            })
            return
        }
        val rows = listOf(
            "Longest streak" to if (r.longestStreak > 0) "${r.longestStreak} days" else "—",
            "Most in a day" to "${fmt(r.bestCount)}${if (r.bestDate.isNotEmpty()) " • ${r.bestDate}" else ""}",
            "Most active month" to r.bestMonth.ifEmpty { "—" },
            "Most active weekday" to if (r.bestWeekday >= 0) Analytics.weekdayName(r.bestWeekday) else "—"
        )
        for ((label, value) in rows) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(4))
            }
            row.addView(TextView(this).apply {
                text = label
                textSize = 12f
                setTextColor(Color.parseColor("#8B949E"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(TextView(this).apply {
                text = value
                textSize = 12f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.parseColor("#F0F6FC"))
            })
            container.addView(row)
        }
    }

    private fun showInsights(container: LinearLayout, lines: List<String>) {
        container.removeAllViews()
        for (line in lines) {
            container.addView(TextView(this).apply {
                text = "•  $line"
                textSize = 12f
                setTextColor(Color.parseColor("#F0F6FC"))
                setPadding(0, dp(4), 0, dp(4))
            })
        }
    }

    private fun showAchievements(container: LinearLayout, total: Int, a: AnalyticsData) {
        container.removeAllViews()
        for (ach in Achievements.evaluate(total, a)) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }
            row.addView(TextView(this).apply {
                text = if (ach.unlocked) "✓" else "○"
                textSize = 16f
                setTextColor(
                    if (ach.unlocked) Color.parseColor("#39D353")
                    else Color.parseColor("#8B949E")
                )
                layoutParams = LinearLayout.LayoutParams(dp(32), LinearLayout.LayoutParams.WRAP_CONTENT)
                contentDescription = if (ach.unlocked) "Unlocked" else "Locked"
            })
            val texts = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            texts.addView(TextView(this).apply {
                text = ach.title
                textSize = 13f
                setTextColor(Color.parseColor("#F0F6FC"))
            })
            texts.addView(TextView(this).apply {
                text = ach.desc
                textSize = 11f
                setTextColor(Color.parseColor("#8B949E"))
            })
            row.addView(texts)
            val status = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.END
                layoutParams = LinearLayout.LayoutParams(
                    dp(96), LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            status.addView(TextView(this).apply {
                text = if (ach.unlocked) "Unlocked" else "Locked"
                textSize = 10f
                typeface = Typeface.MONOSPACE
                setTextColor(
                    if (ach.unlocked) Color.parseColor("#39D353")
                    else Color.parseColor("#8B949E")
                )
            })
            if (!ach.unlocked && ach.progressText.isNotEmpty()) {
                val track = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, dp(4), 0, 0)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(6)
                    )
                    setBackgroundColor(Color.parseColor("#21262D"))
                }
                track.addView(View(this).apply {
                    val w = (ach.progress * 96).toInt().coerceIn(0, 96)
                    layoutParams = LinearLayout.LayoutParams(dp(w), LinearLayout.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(Color.parseColor("#39D353"))
                    contentDescription = "Progress ${ach.progressText}"
                })
                status.addView(track)
                status.addView(TextView(this).apply {
                    text = ach.progressText
                    textSize = 9f
                    typeface = Typeface.MONOSPACE
                    setTextColor(Color.parseColor("#8B949E"))
                    setPadding(0, dp(2), 0, 0)
                })
            }
            row.addView(status)
            container.addView(row)
        }
    }

    private fun openShareOptions(result: ContributionsResult) {
        fun check(label: String, initial: Boolean): MaterialCheckBox =
            MaterialCheckBox(this).apply {
                text = label
                isChecked = initial
                setTextColor(Color.parseColor("#F0F6FC"))
            }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(20)
            setPadding(pad, pad, pad, 0)
        }
        val chkDark = check("Dark card", true)
        val chkUser = check("Show username", true)
        val chkTotal = check("Show contribution count", true)
        val chkStreak = check("Show streak", true)
        val templateRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(8))
        }
        box.addView(TextView(this).apply {
            text = "Template"
            textSize = 12f
            setTextColor(Color.parseColor("#8B949E"))
            setPadding(0, 0, 0, dp(4))
        })
        box.addView(templateRow)
        fun applyTemplate(t: ShareCard.CardTemplate) {
            chkDark.isChecked = t.dark
            chkUser.isChecked = t.showUsername
            chkTotal.isChecked = t.showTotal
            chkStreak.isChecked = t.showStreak
        }
        var selectedTemplate = 0
        val templateButtons = ArrayList<com.google.android.material.button.MaterialButton>()
        ShareCard.TEMPLATES.forEachIndexed { index, t ->
            val b = com.google.android.material.button.MaterialButton(this).apply {
                text = t.name
                textSize = 11f
                cornerRadius = dp(12)
                minimumWidth = 0
                minWidth = 0
                minHeight = dp(48)
                setPadding(dp(4), 0, dp(4), 0)
                backgroundTintList = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
                setTextColor(Color.parseColor("#F0F6FC"))
                strokeColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#3D444D"))
                strokeWidth = dp(1)
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                if (t != ShareCard.TEMPLATES.last()) lp.marginEnd = dp(6)
                layoutParams = lp
                setOnClickListener {
                    selectedTemplate = index
                    applyTemplate(t)
                    templateButtons.forEachIndexed { i, other ->
                        if (i == selectedTemplate) {
                            other.backgroundTintList = android.content.res.ColorStateList.valueOf(
                                androidx.core.graphics.ColorUtils.blendARGB(
                                    Color.parseColor("#0D1117"),
                                    Color.parseColor("#3FB950"), 0.45f
                                )
                            )
                            other.setTextColor(Color.WHITE)
                            other.strokeColor = android.content.res.ColorStateList.valueOf(
                                Color.parseColor("#3FB950")
                            )
                            other.strokeWidth = dp(2)
                        } else {
                            other.backgroundTintList = android.content.res.ColorStateList.valueOf(
                                Color.TRANSPARENT
                            )
                            other.setTextColor(Color.parseColor("#F0F6FC"))
                            other.strokeColor = android.content.res.ColorStateList.valueOf(
                                Color.parseColor("#3D444D")
                            )
                            other.strokeWidth = dp(1)
                        }
                    }
                }
            }
            templateButtons.add(b)
            templateRow.addView(b)
        }
        templateButtons.firstOrNull()?.performClick()
        box.addView(chkDark)
        box.addView(chkUser)
        box.addView(chkTotal)
        box.addView(chkStreak)

        AlertDialog.Builder(this)
            .setTitle("Share contribution card")
            .setView(box)
            .setPositiveButton("Generate & share") { _, _ ->
                val opts = ShareCard.Options(
                    dark = chkDark.isChecked,
                    showUsername = chkUser.isChecked,
                    showTotal = chkTotal.isChecked,
                    showStreak = chkStreak.isChecked
                )
                Toast.makeText(this, "Generating card…", Toast.LENGTH_SHORT).show()
                Thread {
                    try {
                        val theme = Themes.resolve(
                            Prefs.getThemeId(this), Prefs.getCustomColor(this)
                        )
                        val avatar = ShareCard.fetchAvatar(this, result.username)
                        val bmp = ShareCard.render(
                            result, theme, opts,
                            cornerRadius = WidgetPrefs.shapeRadiusFactor(WidgetPrefs.getShape(this)),
                            gapScale = WidgetPrefs.getSpacing(this),
                            customLevels = Studio.effectiveLevels(this, theme).takeIf { opts.dark },
                            avatar = avatar
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
            .setNegativeButton("Cancel", null)
            .show()
    }
}
