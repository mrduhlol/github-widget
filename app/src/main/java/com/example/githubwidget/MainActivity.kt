package com.example.githubwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.slider.Slider
import java.text.NumberFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private var lastResult: ContributionsResult? = null
    private var tintBmp: android.graphics.Bitmap? = null

    /** Tiny background sample for image-through-graph preview (off-thread only). */
    private fun sampleTint(weeks: Int): android.graphics.Bitmap? {
        tintBmp?.let { if (!it.isRecycled) it.recycle() }
        tintBmp = null
        if (!Studio.imageThroughGraph(this)) return null
        if (Studio.getBgType(this) != Studio.BG_IMAGE) return null
        val uri = Studio.getImageUri(this)
        if (uri.isEmpty()) return null
        tintBmp = CardRenderer.sampleImage(this, uri, weeks, 7, Studio.SCALE_FILL)
        return tintBmp
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val input = findViewById<EditText>(R.id.input_username)
        val status = findViewById<TextView>(R.id.text_status)
        val save = findViewById<Button>(R.id.btn_save)
        val refresh = findViewById<Button>(R.id.btn_refresh)
        val pin = findViewById<Button>(R.id.btn_add_widget)
        val activityBtn = findViewById<Button>(R.id.btn_activity)
        val card = findViewById<MaterialCardView>(R.id.card_preview)
        val previewTitle = findViewById<TextView>(R.id.preview_title)
        val previewSubtitle = findViewById<TextView>(R.id.preview_subtitle)
        val previewTotal = findViewById<TextView>(R.id.preview_total)
        val statStreak = findViewById<TextView>(R.id.stat_streak)
        val statBest = findViewById<TextView>(R.id.stat_best)
        val statActive = findViewById<TextView>(R.id.stat_active)
        val colStreak = findViewById<LinearLayout>(R.id.col_streak)
        val colBest = findViewById<LinearLayout>(R.id.col_best)
        val colActive = findViewById<LinearLayout>(R.id.col_active)
        val previewGraph = findViewById<ImageView>(R.id.preview_graph)
        val previewBg = findViewById<ImageView>(R.id.preview_bg)
        val previewStatus = findViewById<TextView>(R.id.preview_status)
        val themeName = findViewById<TextView>(R.id.text_theme_name)
        val rowThemes = findViewById<LinearLayout>(R.id.row_themes)
        val rowStyles = findViewById<LinearLayout>(R.id.row_styles)
        val rowRanges = findViewById<LinearLayout>(R.id.row_ranges)
        val rowShapes = findViewById<LinearLayout>(R.id.row_shapes)
        val rowPresets = findViewById<LinearLayout>(R.id.row_presets)
        val opacityText = findViewById<TextView>(R.id.text_opacity)
        val sliderOpacity = findViewById<Slider>(R.id.slider_opacity)
        val spacingText = findViewById<TextView>(R.id.text_spacing)
        val sliderSpacing = findViewById<Slider>(R.id.slider_spacing)
        val cornersText = findViewById<TextView>(R.id.text_corners)
        val sliderCorners = findViewById<Slider>(R.id.slider_corners)
        val chkTotal = findViewById<MaterialCheckBox>(R.id.chk_total)
        val chkStreak = findViewById<MaterialCheckBox>(R.id.chk_streak)
        val chkLongest = findViewById<MaterialCheckBox>(R.id.chk_longest)
        val chkUpdated = findViewById<MaterialCheckBox>(R.id.chk_updated)
        val btnReset = findViewById<Button>(R.id.btn_reset)

        // ---- state readers ----

        fun currentTheme() =
            Themes.resolve(Prefs.getThemeId(this), Prefs.getCustomColor(this))

        fun formatTotal(total: Int): String =
            NumberFormat.getInstance(Locale.US).format(total)

        fun spacingLabel(s: Float): String = when {
            s < 1f -> "Tight"
            s > 1f -> "Airy"
            else -> "Normal"
        }

        // ---- preview ----

        fun paintPreviewCard() {
            card.radius = dp(WidgetPrefs.getCorners(this)).toFloat()
            // Unified pipeline: the card layer paints the Studio background
            // (solid/gradient/image/transparent + blur + overlay) behind the
            // content, exactly like the widget.
            Thread {
                try {
                    val bg = CardRenderer.card(
                        this, 1024, 512,
                        dp(WidgetPrefs.getCorners(this)).toFloat() * 3f,
                        Color.parseColor("#30363D")
                    )
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        previewBg.setImageBitmap(bg)
                        previewBg.visibility = View.VISIBLE
                        card.setCardBackgroundColor(Color.TRANSPARENT)
                    }
                } catch (_: Exception) {
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        previewBg.visibility = View.GONE
                        card.setCardBackgroundColor(Themes.cardColor(Prefs.getOpacity(this)))
                    }
                }
            }.start()
        }

        /** Paints a dataset into the preview, honoring every look setting. */
        fun showResult(result: ContributionsResult, updatedAgo: String) {
            lastResult = result
            val theme = currentTheme()
            val weeks = WidgetPrefs.rangeWeeks(WidgetPrefs.getRange(this))
            previewGraph.setImageBitmap(
                GraphRenderer.render(
                    result.days,
                    scale = 2.5f,
                    colors = Studio.effectiveLevels(this, theme),
                    maxWeeks = weeks,
                    showMonthLabels = true,
                    cornerRadius = WidgetPrefs.shapeRadiusFactor(WidgetPrefs.getShape(this)),
                    gapScale = WidgetPrefs.getSpacing(this),
                    cellScale = Studio.getCellSize(this),
                    tint = tintBmp,
                    tintAmount = 0.35f
                )
            )
            previewGraph.contentDescription =
                "@${result.username}: ${formatTotal(result.totalLastYear)} contributions in the last year."
            previewTitle.text = "@${result.username}"
            val today = result.days.lastOrNull()?.count ?: 0
            val stats = Stats.compute(result.days)
            val total = formatTotal(result.totalLastYear)

            val parts = ArrayList<String>()
            parts.add("Today: $today")
            if (WidgetPrefs.showStreak(this)) parts.add("${stats.currentStreak}d streak")
            if (WidgetPrefs.showTotal(this)) parts.add("$total/yr")
            previewSubtitle.text = parts.joinToString(" • ")

            if (WidgetPrefs.showTotal(this)) {
                previewTotal.visibility = View.VISIBLE
                previewTotal.text = total
                previewTotal.setTextColor(theme.accent)
            } else {
                previewTotal.visibility = View.GONE
            }
            statStreak.text = "${stats.currentStreak}"
            statStreak.setTextColor(theme.accent)
            statBest.text = "${stats.bestCount}"
            statActive.text = "${stats.activeDays}"
            colStreak.visibility = if (WidgetPrefs.showStreak(this)) View.VISIBLE else View.GONE
            colBest.visibility = View.VISIBLE
            colActive.visibility = View.VISIBLE

            if (WidgetPrefs.showUpdated(this)) {
                previewStatus.visibility = View.VISIBLE
                var tail = "updated $updatedAgo"
                if (WidgetPrefs.showLongest(this)) {
                    tail += " • longest streak ${stats.longestStreak} days"
                }
                previewStatus.text = "Longest streak ${stats.longestStreak} days • $tail."
            } else {
                previewStatus.visibility = View.GONE
            }
            paintPreviewCard()
        }

        fun repaintFromCache() {
            val cached = lastResult ?: return
            val ts = Cache.load(this)?.second ?: 0L
            showResult(cached, TimeAgo.format(ts))
        }

        fun repaintWidget() = Prefs.requestRepaint(this)

        fun syncContentChecks() {
            chkTotal.isChecked = WidgetPrefs.showTotal(this)
            chkStreak.isChecked = WidgetPrefs.showStreak(this)
            chkLongest.isChecked = WidgetPrefs.showLongest(this)
            chkUpdated.isChecked = WidgetPrefs.showUpdated(this)
        }

        // ---- option button helpers ----

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

        fun paintOptions(buttons: List<MaterialButton>, selected: Int) {
            val accent = currentTheme().accent
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

        // ---- theme preview tiles ----
        // Taps are assigned late (see init) so no function is called before it exists.

        var onThemeTap: (String) -> Unit = {}
        var onCustomTap: () -> Unit = {}

        fun themeTile(name: String, colors: IntArray, selected: Boolean, onTap: () -> Unit): MaterialCardView {
            val tile = MaterialCardView(this).apply {
                radius = dp(12).toFloat()
                setCardBackgroundColor(Color.parseColor("#0D1117"))
                strokeWidth = if (selected) dp(2) else 0
                strokeColor = Color.WHITE
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.marginEnd = dp(10)
                layoutParams = lp
                isClickable = true
                isFocusable = true
            }
            val inner = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(10), dp(10), dp(10))
                gravity = Gravity.CENTER_HORIZONTAL
            }
            val squares = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (c in colors) {
                val s = View(this).apply {
                    setBackgroundColor(c)
                    val slp = LinearLayout.LayoutParams(dp(18), dp(18))
                    slp.marginEnd = dp(3)
                    layoutParams = slp
                }
                squares.addView(s)
            }
            inner.addView(squares)
            inner.addView(TextView(this).apply {
                text = name
                textSize = 10f
                typeface = Typeface.MONOSPACE
                setTextColor(if (selected) Color.WHITE else Color.parseColor("#8B949E"))
                setPadding(0, dp(6), 0, 0)
            })
            tile.addView(inner)
            tile.setOnClickListener { onTap() }
            return tile
        }

        fun buildThemeRow() {
            rowThemes.removeAllViews()
            val currentId = Prefs.getThemeId(this)
            for (theme in Themes.ALL) {
                rowThemes.addView(
                    themeTile(theme.name, theme.levels, currentId == theme.id) {
                        onThemeTap(theme.id)
                    }
                )
            }
            val customColors = Themes.resolve(
                Themes.CUSTOM_ID, Prefs.getCustomColor(this)
            ).levels
            rowThemes.addView(
                themeTile(
                    "Custom ${Themes.toHex(Prefs.getCustomColor(this))}",
                    customColors,
                    currentId == Themes.CUSTOM_ID
                ) { onCustomTap() }
            )
        }

        // ---- selection actions ----

        lateinit var styleButtons: List<MaterialButton>
        lateinit var rangeButtons: List<MaterialButton>
        lateinit var shapeButtons: List<MaterialButton>
        lateinit var presetButtons: List<MaterialButton>

        fun selectTheme(id: String) {
            Prefs.setThemeId(this, id)
            val theme = Themes.resolve(id, Prefs.getCustomColor(this))
            themeName.text = if (id == Themes.CUSTOM_ID) {
                "Custom ${Themes.toHex(Prefs.getCustomColor(this))}"
            } else {
                theme.name
            }
            buildThemeRow()
            paintOptions(styleButtons, WidgetPrefs.STYLES.indexOf(WidgetPrefs.getStyle(this)))
            repaintFromCache()
            repaintWidget()
        }

        fun selectStyle(id: String) {
            WidgetPrefs.setStyle(this, id)
            val c = Presets.styleContent(id)
            WidgetPrefs.setContent(this, c[0], c[1], c[2], c[3])
            syncContentChecks()
            paintOptions(styleButtons, WidgetPrefs.STYLES.indexOf(id))
            repaintFromCache()
            repaintWidget()
        }

        fun syncAllControls() {
            buildThemeRow()
            val theme = currentTheme()
            themeName.text = if (Prefs.getThemeId(this) == Themes.CUSTOM_ID) {
                "Custom ${Themes.toHex(Prefs.getCustomColor(this))}"
            } else {
                theme.name
            }
            paintOptions(styleButtons, WidgetPrefs.STYLES.indexOf(WidgetPrefs.getStyle(this)))
            val ranges = listOf(WidgetPrefs.RANGE_3M, WidgetPrefs.RANGE_6M, WidgetPrefs.RANGE_12M)
            paintOptions(rangeButtons, ranges.indexOf(WidgetPrefs.getRange(this)))
            val shapes = listOf(WidgetPrefs.SHAPE_SQUARE, WidgetPrefs.SHAPE_ROUNDED, WidgetPrefs.SHAPE_SOFT, WidgetPrefs.SHAPE_CIRCLE)
            paintOptions(shapeButtons, shapes.indexOf(WidgetPrefs.getShape(this)))
            paintOptions(presetButtons, -1)
            sliderOpacity.value = Prefs.getOpacity(this).toFloat()
            opacityText.text = "${Prefs.getOpacity(this)}%"
            sliderSpacing.value = WidgetPrefs.getSpacing(this)
            spacingText.text = spacingLabel(WidgetPrefs.getSpacing(this))
            sliderCorners.value = WidgetPrefs.getCorners(this).toFloat()
            cornersText.text = "${WidgetPrefs.getCorners(this)}dp"
            syncContentChecks()
            repaintFromCache()
            paintPreviewCard()
        }

        fun openColorPicker() {
            ColorPickerDialog.show(this, Prefs.getCustomColor(this)) { c ->
                Prefs.setCustomColor(this, c)
                Prefs.setThemeId(this, Themes.CUSTOM_ID)
                themeName.text = "Custom ${Themes.toHex(c)}"
                buildThemeRow()
                repaintFromCache()
                repaintWidget()
            }
        }

        // Wire theme taps now that every action above exists.
        onThemeTap = { id -> selectTheme(id) }
        onCustomTap = { openColorPicker() }

        // ---- build option rows ----

        styleButtons = WidgetPrefs.STYLES.map { id ->
            optionButton(WidgetPrefs.STYLE_NAMES[id] ?: id).also { b ->
                b.contentDescription = "Style ${WidgetPrefs.STYLE_NAMES[id]}"
                b.setOnClickListener { selectStyle(id) }
                rowStyles.addView(b)
            }
        }
        val rangeIds = listOf(WidgetPrefs.RANGE_3M, WidgetPrefs.RANGE_6M, WidgetPrefs.RANGE_12M)
        val rangeNames = listOf("3 months", "6 months", "1 year")
        rangeButtons = rangeIds.mapIndexed { i, id ->
            optionButton(rangeNames[i]).also { b ->
                b.setOnClickListener {
                    WidgetPrefs.setRange(this, id)
                    paintOptions(rangeButtons, i)
                    repaintFromCache()
                    repaintWidget()
                }
                rowRanges.addView(b)
            }
        }
        val shapeIds = listOf(WidgetPrefs.SHAPE_SQUARE, WidgetPrefs.SHAPE_ROUNDED, WidgetPrefs.SHAPE_SOFT, WidgetPrefs.SHAPE_CIRCLE)
        val shapeNames = listOf("Square", "Rounded", "Soft", "Circle")
        shapeButtons = shapeIds.mapIndexed { i, id ->
            optionButton(shapeNames[i]).also { b ->
                b.setOnClickListener {
                    WidgetPrefs.setShape(this, id)
                    paintOptions(shapeButtons, i)
                    repaintFromCache()
                    repaintWidget()
                }
                rowShapes.addView(b)
            }
        }
        presetButtons = Presets.ALL.map { id ->
            optionButton(Presets.NAMES[id] ?: id).also { b ->
                b.setOnClickListener {
                    Presets.apply(this, id)
                    syncAllControls()
                    repaintWidget()
                    Toast.makeText(this, "${Presets.NAMES[id]} look applied", Toast.LENGTH_SHORT).show()
                }
                rowPresets.addView(b)
            }
        }

        // ---- sliders / checks / reset ----

        sliderOpacity.addOnChangeListener { _, value, fromUser ->
            val opacity = value.toInt()
            opacityText.text = "$opacity%"
            if (fromUser) {
                Prefs.setOpacity(this, opacity)
                paintPreviewCard()
            }
        }
        sliderOpacity.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(s: Slider) = Unit
            override fun onStopTrackingTouch(s: Slider) {
                Prefs.setOpacity(this@MainActivity, s.value.toInt())
                paintPreviewCard()
                repaintWidget()
            }
        })

        sliderSpacing.addOnChangeListener { _, value, fromUser ->
            spacingText.text = spacingLabel(value)
            if (fromUser) WidgetPrefs.setSpacing(this, value)
        }
        sliderSpacing.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(s: Slider) = Unit
            override fun onStopTrackingTouch(s: Slider) {
                WidgetPrefs.setSpacing(this@MainActivity, s.value)
                spacingText.text = spacingLabel(s.value)
                repaintFromCache()
                repaintWidget()
            }
        })

        sliderCorners.addOnChangeListener { _, value, fromUser ->
            cornersText.text = "${value.toInt()}dp"
            if (fromUser) {
                WidgetPrefs.setCorners(this, value.toInt())
                paintPreviewCard()
            }
        }
        sliderCorners.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(s: Slider) = Unit
            override fun onStopTrackingTouch(s: Slider) {
                WidgetPrefs.setCorners(this@MainActivity, s.value.toInt())
                paintPreviewCard()
                repaintWidget()
            }
        })

        val onContentChanged = View.OnClickListener {
            WidgetPrefs.setContent(
                this,
                chkTotal.isChecked, chkStreak.isChecked,
                chkLongest.isChecked, chkUpdated.isChecked
            )
            repaintFromCache()
            repaintWidget()
        }
        chkTotal.setOnClickListener(onContentChanged)
        chkStreak.setOnClickListener(onContentChanged)
        chkLongest.setOnClickListener(onContentChanged)
        chkUpdated.setOnClickListener(onContentChanged)

        btnReset.setOnClickListener {
            WidgetPrefs.resetToDefaults(this)
            syncAllControls()
            repaintWidget()
            Toast.makeText(this, "Look reset — username and data kept", Toast.LENGTH_SHORT).show()
        }

        // ---- data loading (cache-first) ----

        fun loadPreview(username: String) {
            if (username.isBlank()) {
                card.visibility = View.GONE
                return
            }
            card.visibility = View.VISIBLE
            paintPreviewCard()

            val cached = Cache.load(this)
            val hasCache = cached != null && cached.first.username.equals(username, ignoreCase = true)
            if (hasCache) {
                showResult(cached!!.first, TimeAgo.format(cached.second))
                previewSubtitle.text = "${previewSubtitle.text} • refreshing…"
            } else {
                previewTitle.text = "@$username"
                previewSubtitle.text = "Loading contributions…"
                previewStatus.text = ""
                previewGraph.setImageDrawable(null)
                previewTotal.text = "–"
                previewTotal.setTextColor(currentTheme().accent)
                statStreak.text = "–"
                statBest.text = "–"
                statActive.text = "–"
            }

            Thread {
                try {
                    val result = GithubApi.fetch(username)
                    Cache.save(this, result)
                    sampleTint(WidgetPrefs.rangeWeeks(WidgetPrefs.getRange(this)))
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        showResult(result, TimeAgo.format(System.currentTimeMillis()))
                    }
                } catch (e: UserNotFoundException) {
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        if (!hasCache) {
                            previewSubtitle.text = "User not found"
                            previewStatus.text = e.message
                        } else {
                            previewStatus.text = "${e.message} Showing saved data."
                        }
                    }
                } catch (_: Exception) {
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        if (!hasCache) {
                            previewSubtitle.text = "No connection"
                            previewStatus.text = "Connect to the internet and tap Refresh."
                        } else {
                            val ts = Cache.load(this)?.second ?: 0L
                            previewStatus.text =
                                "Couldn't refresh — showing data from ${TimeAgo.format(ts)}."
                        }
                    }
                }
            }.start()
        }

        // ---- init ----

        input.setText(Prefs.getUsername(this))
        syncAllControls()
        updateStatus(status)

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

        activityBtn.setOnClickListener {
            startActivity(Intent(this, ActivityActivity::class.java))
        }

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

        val saved = Prefs.getUsername(this)
        if (saved.isNotBlank()) loadPreview(saved)
    }

    private fun updateStatus(status: TextView) {
        val u = Prefs.getUsername(this)
        status.text = if (u.isBlank()) {
            "No username set.\n\n1. Enter your GitHub username above\n2. Tap Save\n3. Long-press homescreen → Widgets → GH-widgets (drag to resize)"
        } else {
            "Tracking: @$u\n\nLong-press homescreen → Widgets → GH-widgets.\nLong-press the widget to resize it."
        }
    }
}
