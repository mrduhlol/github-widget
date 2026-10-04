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
                textSize = 12f
                cornerRadius = dp(10)
                minimumWidth = 0
                minWidth = 0
                setPadding(dp(4), 0, dp(4), 0)
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                lp.marginEnd = dp(8)
                layoutParams = lp
            }

        fun paintOptions(buttons: List<MaterialButton>, selected: Int, accent: Int) {
            buttons.forEachIndexed { i, b ->
                if (i == selected) {
                    b.backgroundTintList = ColorStateList.valueOf(accent)
                    b.setTextColor(Color.parseColor("#010409"))
                } else {
                    b.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#21262D"))
                    b.setTextColor(Color.parseColor("#F0F6FC"))
                }
            }
        }

        fun themeAccent(): Int =
            Themes.resolve(themeId, WidgetInstance.getCustomColor(this, widgetId)).accent

        val themeButtons = Themes.ALL.map { t ->
            optionButton(t.name).also { b ->
                b.setOnClickListener {
                    themeId = t.id
                    paintOptions(themeButtons, Themes.ALL.indexOf(t), themeAccent())
                }
                rowThemes.addView(b)
            }
        }
        // Custom colors stay editable in the main app; here we keep the preset set.
        paintOptions(themeButtons, Themes.ALL.indexOfFirst { it.id == themeId }.coerceAtLeast(0), themeAccent())

        val styleIds = WidgetPrefs.STYLES
        val styleButtons = styleIds.map { id ->
            optionButton(WidgetPrefs.STYLE_NAMES[id] ?: id).also { b ->
                b.contentDescription = "Style ${WidgetPrefs.STYLE_NAMES[id]}"
                b.setOnClickListener {
                    styleId = id
                    paintOptions(styleButtons, styleIds.indexOf(id), themeAccent())
                }
                rowStyles.addView(b)
            }
        }
        paintOptions(styleButtons, styleIds.indexOf(styleId).coerceAtLeast(0), themeAccent())

        val rangeIds = listOf(WidgetPrefs.RANGE_3M, WidgetPrefs.RANGE_6M, WidgetPrefs.RANGE_12M)
        val rangeNames = listOf("3m", "6m", "1y")
        val rangeButtons = rangeIds.mapIndexed { i, id ->
            optionButton(rangeNames[i]).also { b ->
                b.setOnClickListener {
                    rangeId = id
                    paintOptions(rangeButtons, i, themeAccent())
                }
                rowRanges.addView(b)
            }
        }
        paintOptions(rangeButtons, rangeIds.indexOf(rangeId).coerceAtLeast(0), themeAccent())

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
                shape = WidgetPrefs.getShape(this, widgetId),
                spacing = WidgetPrefs.getSpacing(this, widgetId),
                corners = WidgetPrefs.getCorners(this, widgetId),
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
