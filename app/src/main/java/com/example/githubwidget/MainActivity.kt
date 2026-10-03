package com.example.githubwidget

import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider

class MainActivity : AppCompatActivity() {

    private var lastResult: ContributionsResult? = null

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
        val previewTotal = findViewById<TextView>(R.id.preview_total)
        val previewGraph = findViewById<ImageView>(R.id.preview_graph)
        val previewStatus = findViewById<TextView>(R.id.preview_status)
        val themeName = findViewById<TextView>(R.id.text_theme_name)
        val opacityText = findViewById<TextView>(R.id.text_opacity)
        val slider = findViewById<Slider>(R.id.slider_opacity)

        val swatches: Map<String, MaterialButton> = mapOf(
            "green" to findViewById(R.id.swatch_green),
            "purple" to findViewById(R.id.swatch_purple),
            "blue" to findViewById(R.id.swatch_blue),
            "orange" to findViewById(R.id.swatch_orange),
            "rose" to findViewById(R.id.swatch_rose),
            Themes.CUSTOM_ID to findViewById(R.id.swatch_custom)
        )

        fun currentTheme() =
            Themes.resolve(Prefs.getThemeId(this), Prefs.getCustomColor(this))
        fun currentOpacity() = Prefs.getOpacity(this)

        fun paintPreviewCard() {
            card.setCardBackgroundColor(Themes.cardColor(currentOpacity()))
        }

        fun redrawGraphFromCache() {
            val cached = lastResult ?: return
            val theme = currentTheme()
            previewGraph.setImageBitmap(
                GraphRenderer.render(cached.days, scale = 2.5f, colors = theme.levels)
            )
            previewTotal.setTextColor(theme.accent)
            paintPreviewCard()
        }

        fun selectTheme(id: String, refreshWidget: Boolean = true) {
            Prefs.setThemeId(this, id)
            val theme = Themes.resolve(id, Prefs.getCustomColor(this))
            themeName.text = if (id == Themes.CUSTOM_ID) {
                "Custom ${Themes.toHex(Prefs.getCustomColor(this))}"
            } else {
                theme.name
            }
            for ((key, btn) in swatches) {
                if (key == id) {
                    btn.strokeWidth = 4
                    btn.strokeColor = ColorStateList.valueOf(Color.WHITE)
                } else {
                    btn.strokeWidth = 0
                }
            }
            previewTotal.setTextColor(theme.accent)
            paintPreviewCard()
            redrawGraphFromCache()
            if (refreshWidget) Prefs.requestRefresh(this)
        }

        fun loadPreview(username: String) {
            if (username.isBlank()) {
                card.visibility = View.GONE
                return
            }
            card.visibility = View.VISIBLE
            paintPreviewCard()
            previewTitle.text = "@$username"
            previewSubtitle.text = "Loading contributions…"
            previewStatus.text = ""
            previewGraph.setImageDrawable(null)
            previewTotal.text = "–"
            previewTotal.setTextColor(currentTheme().accent)
            Thread {
                try {
                    val result = GithubApi.fetch(username)
                    lastResult = result
                    val bmp = GraphRenderer.render(
                        result.days, scale = 2.5f, colors = currentTheme().levels
                    )
                    runOnUiThread {
                        previewTitle.text = "@${result.username}"
                        val today = result.days.lastOrNull()
                        previewSubtitle.text =
                            "Today: ${today?.count ?: 0} • ${result.totalLastYear} in last year"
                        previewTotal.text = "${result.totalLastYear}"
                        previewTotal.setTextColor(currentTheme().accent)
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

        fun openColorPicker() {
            val current = Prefs.getCustomColor(this)
            var r = Color.red(current)
            var g = Color.green(current)
            var b = Color.blue(current)
            val density = resources.displayMetrics.density

            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val pad = (20 * density).toInt()
                setPadding(pad, pad, pad, pad)
            }
            val preview = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (56 * density).toInt()
                )
            }
            val hex = TextView(this).apply {
                textSize = 13f
                typeface = android.graphics.Typeface.MONOSPACE
                setTextColor(Color.parseColor("#8B949E"))
                setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
            }
            box.addView(preview)
            box.addView(hex)

            fun refresh() {
                val c = Color.rgb(r, g, b)
                preview.setBackgroundColor(c)
                hex.text = "RGB($r, $g, $b)  ${Themes.toHex(c)}"
            }

            val bars = mutableListOf<SeekBar>()
            listOf("Red" to r, "Green" to g, "Blue" to b).forEach { (label, start) ->
                val rowLabel = TextView(this).apply {
                    text = label
                    setTextColor(Color.WHITE)
                    textSize = 12f
                }
                val bar = SeekBar(this).apply {
                    max = 255
                    progress = start
                }
                box.addView(rowLabel)
                box.addView(bar)
                bars.add(bar)
            }
            fun onChange(bar: SeekBar, fn: (Int) -> Unit) {
                bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar, v: Int, fromUser: Boolean) = fn(v)
                    override fun onStartTrackingTouch(s: SeekBar) = Unit
                    override fun onStopTrackingTouch(s: SeekBar) = Unit
                })
            }
            onChange(bars[0]) { r = it; refresh() }
            onChange(bars[1]) { g = it; refresh() }
            onChange(bars[2]) { b = it; refresh() }
            refresh()

            AlertDialog.Builder(this)
                .setTitle("Pick a graph color")
                .setView(box)
                .setPositiveButton("Use this color") { _, _ ->
                    val c = Color.rgb(r, g, b)
                    Prefs.setCustomColor(this, c)
                    swatches[Themes.CUSTOM_ID]?.backgroundTintList =
                        ColorStateList.valueOf(c)
                    selectTheme(Themes.CUSTOM_ID)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Init from saved prefs.
        input.setText(Prefs.getUsername(this))
        slider.value = currentOpacity().toFloat()
        opacityText.text = "${currentOpacity()}%"
        swatches[Themes.CUSTOM_ID]?.backgroundTintList =
            ColorStateList.valueOf(Prefs.getCustomColor(this))
        selectTheme(Prefs.getThemeId(this), refreshWidget = false)
        paintPreviewCard()
        updateStatus(status)

        for ((id, btn) in swatches) {
            btn.setOnClickListener {
                if (id == Themes.CUSTOM_ID) openColorPicker() else selectTheme(id)
            }
        }

        slider.addOnChangeListener { _, value, fromUser ->
            val opacity = value.toInt()
            opacityText.text = "$opacity%"
            if (fromUser) {
                Prefs.setOpacity(this, opacity)
                paintPreviewCard()
            }
        }
        slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(s: Slider) = Unit
            override fun onStopTrackingTouch(s: Slider) {
                Prefs.setOpacity(this@MainActivity, s.value.toInt())
                paintPreviewCard()
                Prefs.requestRefresh(this@MainActivity)
            }
        })

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
            "No username set.\n\n1. Enter your GitHub username above\n2. Tap Save\n3. Long-press homescreen → Widgets → GitHub Contributions (drag to resize)"
        } else {
            "Tracking: @$u\n\nLong-press homescreen → Widgets → GitHub Contributions.\nLong-press the widget to resize it."
        }
    }
}
