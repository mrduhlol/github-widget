package com.example.githubwidget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import android.view.View
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.slider.Slider

/**
 * Setup screen for ONE widget instance (V1.9).
 *
 * Launched by the launcher when a widget is added (configure flow) and
 * when the user taps a widget's total (reconfigure). Saves per-widget
 * settings; the app's screen keeps holding the defaults for new widgets.
 */
class WidgetConfigActivity : AppCompatActivity() {

    private var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var themeId: String = "green"
    private var styleId: String = WidgetPrefs.STYLE_CLASSIC
    private var rangeId: String = WidgetPrefs.RANGE_6M

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_config)

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        // Launcher configure flow needs RESULT_CANCELED until we save.
        setResult(RESULT_CANCELED)

        val input = findViewById<EditText>(R.id.cfg_username)
        val rowThemes = findViewById<LinearLayout>(R.id.cfg_themes)
        val rowStyles = findViewById<LinearLayout>(R.id.cfg_styles)
        val rowRanges = findViewById<LinearLayout>(R.id.cfg_ranges)
        val opacityText = findViewById<TextView>(R.id.cfg_opacity_text)
        val slider = findViewById<Slider>(R.id.cfg_opacity)
        val chkTotal = findViewById<MaterialCheckBox>(R.id.cfg_total)
        val chkStreak = findViewById<MaterialCheckBox>(R.id.cfg_streak)
        val chkLongest = findViewById<MaterialCheckBox>(R.id.cfg_longest)
        val chkUpdated = findViewById<MaterialCheckBox>(R.id.cfg_updated)
        val save = findViewById<Button>(R.id.cfg_save)

        // Start from this widget's effective settings (own or global fallback).
        input.setText(Prefs.getUsername(this))
        themeId = WidgetInstance.getThemeId(this, widgetId).takeIf { it != Themes.CUSTOM_ID } ?: "green"
        styleId = WidgetInstance.getStyle(this, widgetId)
        rangeId = WidgetInstance.getRange(this, widgetId)
        slider.value = WidgetInstance.getOpacity(this, widgetId).toFloat()
        opacityText.text = "${WidgetInstance.getOpacity(this, widgetId)}%"
        chkTotal.isChecked = WidgetInstance.showTotal(this, widgetId)
        chkStreak.isChecked = WidgetInstance.showStreak(this, widgetId)
        chkLongest.isChecked = WidgetInstance.showLongest(this, widgetId)
        chkUpdated.isChecked = WidgetInstance.showUpdated(this, widgetId)

        fun optionButton(label: String): MaterialButton =
            MaterialButton(this).apply {
                text = label
                textSize = 13f
                cornerRadius = dp(12)
                minimumWidth = 0
                minWidth = 0
                minHeight = dp(48)
                setPadding(dp(6), 0, dp(6), 0)
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                lp.marginEnd = dp(8)
                layoutParams = lp
            }

        fun paintOptions(buttons: List<MaterialButton>, selected: Int, accent: Int) {
            val surface = ContextCompat.getColor(this, R.color.surface)
            val strokeC = ContextCompat.getColor(this, R.color.stroke_strong)
            val primary = ContextCompat.getColor(this, R.color.text_primary)
            val secondary = ContextCompat.getColor(this, R.color.text_secondary)
            buttons.forEachIndexed { i, b ->
                if (i == selected) {
                    b.backgroundTintList = ColorStateList.valueOf(ColorUtils.blendARGB(surface, accent, 0.25f))
                    b.setTextColor(primary)
                    b.setTypeface(null, android.graphics.Typeface.BOLD)
                    b.strokeColor = ColorStateList.valueOf(accent)
                    b.strokeWidth = dp(2)
                } else {
                    b.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                    b.setTextColor(secondary)
                    b.setTypeface(null, android.graphics.Typeface.NORMAL)
                    b.strokeColor = ColorStateList.valueOf(strokeC)
                    b.strokeWidth = dp(1)
                }
            }
        }

        fun themeAccent(): Int =
            Themes.resolve(themeId, WidgetInstance.getCustomColor(this, widgetId)).accent

        val previewGrid = findViewById<LinearLayout>(R.id.cfg_preview_grid)
        val previewCaption = findViewById<TextView>(R.id.cfg_preview_caption)
        val cells = ArrayList<View>()
        val levels = floatArrayOf(0.15f, 0.35f, 0.6f, 1f)
        for (c in 0 until 18) {
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            for (r in 0 until 5) {
                val cell = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(14), dp(14)).apply {
                        gravity = android.view.Gravity.CENTER
                        topMargin = if (r == 0) 0 else dp(3)
                    }
                    tag = levels[(c * 7 + r * 3 + c * r) % levels.size]
                }
                cells.add(cell)
                col.addView(cell)
            }
            previewGrid.addView(col)
        }
        fun updatePreview() {
            val accent = themeAccent()
            val base = ContextCompat.getColor(this, R.color.surface_raised)
            cells.forEach { v ->
                val lvl = v.tag as Float
                val bg = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(3).toFloat()
                    setColor(ColorUtils.blendARGB(base, accent, lvl))
                }
                v.background = bg
            }
            val name = Themes.ALL.firstOrNull { it.id == themeId }?.name ?: ""
            previewCaption.text = "$name \u00b7 ${WidgetPrefs.STYLE_NAMES[styleId] ?: styleId}"
        }

        val themeButtons = Themes.ALL.map { t -> optionButton(t.name).also { rowThemes.addView(it) } }
        themeButtons.forEachIndexed { i, b ->
            b.setOnClickListener {
                themeId = Themes.ALL[i].id
                paintOptions(themeButtons, i, themeAccent())
                updatePreview()
            }
        }
        // Custom colors stay editable in the Widget Studio; here we keep the preset set.
        paintOptions(themeButtons, Themes.ALL.indexOfFirst { it.id == themeId }.coerceAtLeast(0), themeAccent())

        val styleIds = WidgetPrefs.STYLES
        val styleButtons = styleIds.map { id ->
            optionButton(WidgetPrefs.STYLE_NAMES[id] ?: id).also { rowStyles.addView(it) }
        }
        styleButtons.forEachIndexed { i, b ->
            b.contentDescription = "Style ${WidgetPrefs.STYLE_NAMES[styleIds[i]]}"
            b.setOnClickListener {
                styleId = styleIds[i]
                paintOptions(styleButtons, i, themeAccent())
                updatePreview()
            }
        }
        paintOptions(styleButtons, styleIds.indexOf(styleId).coerceAtLeast(0), themeAccent())

        val rangeIds = listOf(WidgetPrefs.RANGE_3M, WidgetPrefs.RANGE_6M, WidgetPrefs.RANGE_12M)
        val rangeNames = listOf("3m", "6m", "1y")
        val rangeButtons = rangeIds.mapIndexed { i, _ -> optionButton(rangeNames[i]).also { rowRanges.addView(it) } }
        rangeButtons.forEachIndexed { i, b ->
            b.setOnClickListener {
                rangeId = rangeIds[i]
                paintOptions(rangeButtons, i, themeAccent())
            }
        }
        paintOptions(rangeButtons, rangeIds.indexOf(rangeId).coerceAtLeast(0), themeAccent())

        updatePreview()

        slider.addOnChangeListener { _, value, _ ->
            opacityText.text = "${value.toInt()}%"
        }

        save.setOnClickListener {
            val u = input.text.toString().trim().trimStart('@')
            if (u.isEmpty()) {
                Toast.makeText(this, "Enter a GitHub username", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Prefs.setUsername(this, u)
            WidgetInstance.save(
                this, widgetId,
                themeId = themeId,
                style = styleId,
                opacity = slider.value.toInt(),
                range = rangeId,
                shape = WidgetInstance.getShape(this, widgetId),
                spacing = WidgetInstance.getSpacing(this, widgetId),
                corners = WidgetInstance.getCorners(this, widgetId),
                customColor = WidgetInstance.getCustomColor(this, widgetId),
                showTotal = chkTotal.isChecked,
                showStreak = chkStreak.isChecked,
                showLongest = chkLongest.isChecked,
                showUpdated = chkUpdated.isChecked
            )
            // Approve the widget, then paint + fetch it.
            val done = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            setResult(RESULT_OK, done)
            val refresh = Intent(this, ContributionWidgetProvider::class.java).apply {
                action = ContributionWidgetProvider.ACTION_REFRESH
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId))
            }
            sendBroadcast(refresh)
            Toast.makeText(this, "Widget saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
