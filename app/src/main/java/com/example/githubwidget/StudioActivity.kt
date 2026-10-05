package com.example.githubwidget

import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.slider.Slider
import java.text.NumberFormat
import java.util.Locale

/** Widget Studio: live preview with every look control in one screen. */
class StudioActivity : AppCompatActivity() {

    private var lastResult: ContributionsResult? = null
    private var tintBmp: android.graphics.Bitmap? = null

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun fmt(n: Int): String = NumberFormat.getInstance(Locale.US).format(n)

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Not all providers grant persistable access; the URI still
                // works for the current session and usually beyond it.
            }
            Studio.setImageUri(this, uri.toString())
            Studio.setBgType(this, Studio.BG_IMAGE)
            syncAll()
            repaintWidget()
        }

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
        setContentView(R.layout.activity_studio)

        val card = findViewById<MaterialCardView>(R.id.st_preview_card)
        val bg = findViewById<ImageView>(R.id.st_preview_bg)
        val title = findViewById<TextView>(R.id.st_title)
        val subtitle = findViewById<TextView>(R.id.st_subtitle)
        val total = findViewById<TextView>(R.id.st_total)
        val graph = findViewById<ImageView>(R.id.st_graph)
        val footer = findViewById<TextView>(R.id.st_footer)
        val rowThemes = findViewById<LinearLayout>(R.id.st_themes)
        val themeName = findViewById<TextView>(R.id.st_theme_name)
        val rowStyles = findViewById<LinearLayout>(R.id.st_styles)
        val rowRanges = findViewById<LinearLayout>(R.id.st_ranges)
        val rowShapes = findViewById<LinearLayout>(R.id.st_shapes)
        val cellText = findViewById<TextView>(R.id.st_cellsize_text)
        val cellSlider = findViewById<Slider>(R.id.st_cellsize)
        val spacingText = findViewById<TextView>(R.id.st_spacing_text)
        val spacingSlider = findViewById<Slider>(R.id.st_spacing)
        val rowLevels = findViewById<LinearLayout>(R.id.st_levels)
        val rowBgTypes = findViewById<LinearLayout>(R.id.st_bgtypes)
        val solidRow = findViewById<LinearLayout>(R.id.st_solid_row)
        val bgColorBtn = findViewById<MaterialButton>(R.id.st_bgcolor)
        val gradBox = findViewById<LinearLayout>(R.id.st_gradient_box)
        val grad1 = findViewById<MaterialButton>(R.id.st_grad_1)
        val grad2 = findViewById<MaterialButton>(R.id.st_grad_2)
        val grad3 = findViewById<MaterialButton>(R.id.st_grad_3)
        val gradThird = findViewById<MaterialCheckBox>(R.id.st_grad_third)
        val gradRadial = findViewById<MaterialCheckBox>(R.id.st_grad_radial)
        val rowAngles = findViewById<LinearLayout>(R.id.st_angles)
        val imageBox = findViewById<LinearLayout>(R.id.st_image_box)
        val rowScales = findViewById<LinearLayout>(R.id.st_scales)
        val chkImgGraph = findViewById<MaterialCheckBox>(R.id.st_image_graph)
        val bgOpText = findViewById<TextView>(R.id.st_bgopacity_text)
        val bgOpSlider = findViewById<Slider>(R.id.st_bgopacity)
        val blurText = findViewById<TextView>(R.id.st_blur_text)
        val blurSlider = findViewById<Slider>(R.id.st_blur)
        val rowOverlays = findViewById<LinearLayout>(R.id.st_overlays)
        val ovOpText = findViewById<TextView>(R.id.st_overlayop_text)
        val ovOpSlider = findViewById<Slider>(R.id.st_overlayop)
        val chkTotal = findViewById<MaterialCheckBox>(R.id.st_chk_total)
        val chkStreak = findViewById<MaterialCheckBox>(R.id.st_chk_streak)
        val chkLongest = findViewById<MaterialCheckBox>(R.id.st_chk_longest)
        val chkUpdated = findViewById<MaterialCheckBox>(R.id.st_chk_updated)
        val chkActive = findViewById<MaterialCheckBox>(R.id.st_chk_active)
        val labelInput = findViewById<EditText>(R.id.st_label)
        val rowTextSizes = findViewById<LinearLayout>(R.id.st_textsizes)
        val cornersText = findViewById<TextView>(R.id.st_corners_text)
        val cornersSlider = findViewById<Slider>(R.id.st_corners)
        val rowPresets = findViewById<LinearLayout>(R.id.st_presets)

        fun theme(): GraphTheme =
            Themes.resolve(Prefs.getThemeId(this), Prefs.getCustomColor(this))

        fun repaintWidget() = Prefs.requestRepaint(this)

        // ---------- preview ----------

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
            lastResult = result
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
            title.text = "@${result.username}"
            val today = result.days.lastOrNull()?.count ?: 0
            val stats = Stats.compute(result.days)
            val parts = ArrayList<String>()
            parts.add("Today: $today")
            if (WidgetPrefs.showStreak(this)) parts.add("${stats.currentStreak}d streak")
            if (WidgetPrefs.showTotal(this)) parts.add("${fmt(result.totalLastYear)}/yr")
            if (Studio.showActiveDays(this)) parts.add("${stats.activeDays} active")
            subtitle.text = parts.joinToString(" • ")
            if (WidgetPrefs.showTotal(this)) {
                total.visibility = View.VISIBLE
                total.text = fmt(result.totalLastYear)
                total.setTextColor(t.accent)
            } else {
                total.visibility = View.GONE
            }
            val label = Studio.getCustomLabel(this)
            if (label.isNotEmpty()) {
                footer.visibility = View.VISIBLE
                footer.text = label
            } else if (WidgetPrefs.showUpdated(this)) {
                footer.visibility = View.VISIBLE
                var tail = "updated $ago"
                if (WidgetPrefs.showLongest(this)) tail += " • ${stats.longestStreak}d longest"
                footer.text = tail
            } else {
                footer.visibility = View.GONE
            }
            val s = Studio.textScale(Studio.getTextSize(this))
            title.textSize = 15f * s
            subtitle.textSize = 12f * s
            total.textSize = 24f * s
            footer.textSize = 11f * s
            paintCard()
        }

        fun repaintPreview() {
            val cached = lastResult ?: return
            val ts = Cache.load(this)?.second ?: 0L
            showResult(cached, TimeAgo.format(ts))
        }

        // ---------- option buttons ----------

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
            val accent = theme().accent
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
                squares.addView(View(this).apply {
                    setBackgroundColor(c)
                    val slp = LinearLayout.LayoutParams(dp(18), dp(18))
                    slp.marginEnd = dp(3)
                    layoutParams = slp
                })
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
            for (t in Themes.ALL) {
                rowThemes.addView(
                    themeTile(t.name, t.levels, currentId == t.id) { onThemeTap(t.id) }
                )
            }
            val custom = Themes.resolve(Themes.CUSTOM_ID, Prefs.getCustomColor(this)).levels
            rowThemes.addView(
                themeTile("Custom ${Themes.toHex(Prefs.getCustomColor(this))}", custom, currentId == Themes.CUSTOM_ID) {
                    onCustomTap()
                }
            )
            themeName.text = if (currentId == Themes.CUSTOM_ID) {
                "Custom ${Themes.toHex(Prefs.getCustomColor(this))}"
            } else {
                theme().name
            }
        }

        lateinit var styleButtons: List<MaterialButton>
        lateinit var rangeButtons: List<MaterialButton>
        lateinit var shapeButtons: List<MaterialButton>
        lateinit var sizeButtons: List<MaterialButton>
        lateinit var bgTypeButtons: List<MaterialButton>
        lateinit var scaleButtons: List<MaterialButton>
        lateinit var angleButtons: List<MaterialButton>
        lateinit var overlayButtons: List<MaterialButton>
        lateinit var presetButtons: List<MaterialButton>

        fun syncSwatches() {
            bgColorBtn.backgroundTintList = ColorStateList.valueOf(Studio.getBgColor(this))
            grad1.backgroundTintList = ColorStateList.valueOf(Studio.getBgColor(this))
            grad2.backgroundTintList = ColorStateList.valueOf(Studio.getGrad2(this))
            Studio.getGrad3(this)?.let {
                grad3.backgroundTintList = ColorStateList.valueOf(it)
            }
            buildLevels()
        }

        fun syncAll() {
            buildThemeRow()
            paintOptions(styleButtons, WidgetPrefs.STYLES.indexOf(WidgetPrefs.getStyle(this)))
            val ranges = listOf(WidgetPrefs.RANGE_3M, WidgetPrefs.RANGE_6M, WidgetPrefs.RANGE_12M)
            paintOptions(rangeButtons, ranges.indexOf(WidgetPrefs.getRange(this)))
            val shapes = listOf(
                WidgetPrefs.SHAPE_SQUARE, WidgetPrefs.SHAPE_ROUNDED,
                WidgetPrefs.SHAPE_SOFT, WidgetPrefs.SHAPE_CIRCLE
            )
            paintOptions(shapeButtons, shapes.indexOf(WidgetPrefs.getShape(this)))
            paintOptions(sizeButtons, Studio.getTextSize(this))
            val bgTypes = listOf(Studio.BG_SOLID, Studio.BG_GRADIENT, Studio.BG_IMAGE, Studio.BG_TRANSPARENT)
            paintOptions(bgTypeButtons, bgTypes.indexOf(Studio.getBgType(this)))
            paintOptions(scaleButtons, if (Studio.getImageScale(this) == Studio.SCALE_FIT) 1 else 0)
            val angles = listOf(0, 45, 90, 135)
            paintOptions(angleButtons, angles.indexOf(Studio.getGradAngle(this)).coerceAtLeast(0))
            paintOptions(overlayButtons, overlayIndex())
            paintOptions(presetButtons, -1)
            cellSlider.value = Studio.getCellSize(this)
            cellText.text = "${(Studio.getCellSize(this) * 100).toInt()}%"
            spacingSlider.value = WidgetPrefs.getSpacing(this)
            spacingText.text = spacingLabel(WidgetPrefs.getSpacing(this))
            cornersSlider.value = WidgetPrefs.getCorners(this).toFloat()
            cornersText.text = "${WidgetPrefs.getCorners(this)}dp"
            bgOpSlider.value = Studio.getBgOpacity(this).toFloat()
            bgOpText.text = "${Studio.getBgOpacity(this)}%"
            blurSlider.value = Studio.getBlur(this).toFloat()
            blurText.text = if (Studio.getBlur(this) == 0) "Off" else "${Studio.getBlur(this)}"
            ovOpSlider.value = Studio.getOverlayOpacity(this).toFloat()
            ovOpText.text = "${Studio.getOverlayOpacity(this)}%"
            gradThird.isChecked = Studio.getGrad3(this) != null
            gradRadial.isChecked = Studio.isGradRadial(this)
            chkImgGraph.isChecked = Studio.imageThroughGraph(this)
            chkTotal.isChecked = WidgetPrefs.showTotal(this)
            chkStreak.isChecked = WidgetPrefs.showStreak(this)
            chkLongest.isChecked = WidgetPrefs.showLongest(this)
            chkUpdated.isChecked = WidgetPrefs.showUpdated(this)
            chkActive.isChecked = Studio.showActiveDays(this)
            if (labelInput.text.toString() != Studio.getCustomLabel(this)) {
                labelInput.setText(Studio.getCustomLabel(this))
            }
            val t = Studio.getBgType(this)
            solidRow.visibility = if (t == Studio.BG_SOLID || t == Studio.BG_GRADIENT) View.VISIBLE else View.GONE
            gradBox.visibility = if (t == Studio.BG_GRADIENT) View.VISIBLE else View.GONE
            imageBox.visibility = if (t == Studio.BG_IMAGE) View.VISIBLE else View.GONE
            syncSwatches()
            repaintPreview()
        }

        fun spacingLabel(s: Float): String = when {
            s < 1f -> "Tight"
            s > 1f -> "Airy"
            else -> "Normal"
        }

        val overlayColors = listOf(
            "None" to null as Int?,
            "Black" to Color.BLACK,
            "White" to Color.WHITE,
            "Theme" to -1,
            "Custom" to -2
        )

        fun overlayIndex(): Int {
            if (Studio.getOverlayOpacity(this) == 0) return 0
            val c = Studio.getOverlay(this)
            return when {
                c == Color.BLACK -> 1
                c == Color.WHITE -> 2
                c == theme().accent -> 3
                else -> 4
            }
        }

        fun buildLevels() {
            rowLevels.removeAllViews()
            val levels = Studio.effectiveLevels(this, theme())
            val names = listOf("Empty", "Low", "Med", "High", "Max")
            levels.forEachIndexed { i, c ->
                val col = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    layoutParams = lp
                }
                col.addView(MaterialButton(this).apply {
                    text = ""
                    backgroundTintList = ColorStateList.valueOf(c)
                    cornerRadius = dp(14)
                    layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
                    insetTop = 0
                    insetBottom = 0
                    setPadding(0, 0, 0, 0)
                    contentDescription = "Palette ${names[i]}"
                    setOnClickListener {
                        ColorPickerDialog.show(this@StudioActivity, c) { picked ->
                            val cur = Studio.getLevels(this@StudioActivity)?.copyOf()
                                ?: theme().levels.copyOf()
                            cur[i] = picked
                            Studio.setLevels(this@StudioActivity, cur)
                            buildLevels()
                            repaintPreview()
                            repaintWidget()
                        }
                    }
                })
                col.addView(TextView(this).apply {
                    text = names[i]
                    textSize = 9f
                    typeface = Typeface.MONOSPACE
                    setTextColor(Color.parseColor("#8B949E"))
                    setPadding(0, dp(4), 0, 0)
                })
                rowLevels.addView(col)
            }
        }

        // ---------- actions ----------

        fun selectTheme(id: String) {
            Prefs.setThemeId(this, id)
            buildThemeRow()
            paintOptions(styleButtons, WidgetPrefs.STYLES.indexOf(WidgetPrefs.getStyle(this)))
            repaintPreview()
            repaintWidget()
        }

        fun openColorPicker() {
            ColorPickerDialog.show(this, Prefs.getCustomColor(this)) { c ->
                Prefs.setCustomColor(this, c)
                Prefs.setThemeId(this, Themes.CUSTOM_ID)
                buildThemeRow()
                repaintPreview()
                repaintWidget()
            }
        }

        onThemeTap = { id -> selectTheme(id) }
        onCustomTap = { openColorPicker() }

        fun pickSimpleColor(title: String, initial: Int, onPick: (Int) -> Unit) {
            ColorPickerDialog.show(this, initial) { onPick(it) }
        }

        // ---------- build rows ----------

        styleButtons = WidgetPrefs.STYLES.map { id ->
            optionButton(WidgetPrefs.STYLE_NAMES[id] ?: id).also { b ->
                b.contentDescription = "Style ${WidgetPrefs.STYLE_NAMES[id]}"
                b.setOnClickListener {
                    WidgetPrefs.setStyle(this, id)
                    val c = Presets.styleContent(id)
                    WidgetPrefs.setContent(this, c[0], c[1], c[2], c[3])
                    syncAll()
                    repaintWidget()
                }
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
                    repaintPreview()
                    repaintWidget()
                }
                rowRanges.addView(b)
            }
        }

        val shapeIds = listOf(
            WidgetPrefs.SHAPE_SQUARE, WidgetPrefs.SHAPE_ROUNDED,
            WidgetPrefs.SHAPE_SOFT, WidgetPrefs.SHAPE_CIRCLE
        )
        val shapeNames = listOf("Square", "Rounded", "Soft", "Circle")
        shapeButtons = shapeIds.mapIndexed { i, id ->
            optionButton(shapeNames[i]).also { b ->
                b.setOnClickListener {
                    WidgetPrefs.setShape(this, id)
                    paintOptions(shapeButtons, i)
                    repaintPreview()
                    repaintWidget()
                }
                rowShapes.addView(b)
            }
        }

        sizeButtons = listOf("Compact", "Normal", "Large").mapIndexed { i, name ->
            optionButton(name).also { b ->
                b.setOnClickListener {
                    Studio.setTextSize(this, i)
                    paintOptions(sizeButtons, i)
                    repaintPreview()
                    repaintWidget()
                }
                rowTextSizes.addView(b)
            }
        }

        val bgTypeIds = listOf(Studio.BG_SOLID, Studio.BG_GRADIENT, Studio.BG_IMAGE, Studio.BG_TRANSPARENT)
        val bgTypeNames = listOf("Solid", "Gradient", "Image", "Clear")
        bgTypeButtons = bgTypeIds.mapIndexed { i, id ->
            optionButton(bgTypeNames[i]).also { b ->
                b.contentDescription = "Background $id"
                b.setOnClickListener {
                    Studio.setBgType(this, id)
                    if (id == Studio.BG_IMAGE && Studio.getImageUri(this).isEmpty()) {
                        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    syncAll()
                    repaintWidget()
                }
                rowBgTypes.addView(b)
            }
        }

        scaleButtons = listOf("Fill", "Fit").mapIndexed { i, name ->
            optionButton(name).also { b ->
                b.setOnClickListener {
                    Studio.setImageScale(this, if (i == 0) Studio.SCALE_FILL else Studio.SCALE_FIT)
                    paintOptions(scaleButtons, i)
                    repaintPreview()
                    repaintWidget()
                }
                rowScales.addView(b)
            }
        }

        angleButtons = listOf("0°", "45°", "90°", "135°").mapIndexed { i, name ->
            optionButton(name).also { b ->
                b.setOnClickListener {
                    Studio.setGradAngle(this, listOf(0, 45, 90, 135)[i])
                    paintOptions(angleButtons, i)
                    repaintPreview()
                    repaintWidget()
                }
                rowAngles.addView(b)
            }
        }

        overlayButtons = overlayColors.mapIndexed { i, (name, _) ->
            optionButton(name).also { b ->
                b.setOnClickListener { applyOverlay(i) }
                rowOverlays.addView(b)
            }
        }

        presetButtons = Presets.ALL.map { id ->
            optionButton(Presets.NAMES[id] ?: id).also { b ->
                b.setOnClickListener {
                    Presets.apply(this, id)
                    syncAll()
                    repaintWidget()
                    Toast.makeText(this, "${Presets.NAMES[id]} look applied", Toast.LENGTH_SHORT).show()
                }
                rowPresets.addView(b)
            }
        }

        fun applyOverlay(i: Int) {
            when (i) {
                0 -> Studio.setOverlayOpacity(this, 0)
                1 -> {
                    Studio.setOverlay(this, Color.BLACK)
                    if (Studio.getOverlayOpacity(this) == 0) Studio.setOverlayOpacity(this, 40)
                }
                2 -> {
                    Studio.setOverlay(this, Color.WHITE)
                    if (Studio.getOverlayOpacity(this) == 0) Studio.setOverlayOpacity(this, 40)
                }
                3 -> {
                    Studio.setOverlay(this, theme().accent)
                    if (Studio.getOverlayOpacity(this) == 0) Studio.setOverlayOpacity(this, 30)
                }
                else -> {
                    pickSimpleColor("Overlay color", Studio.getOverlay(this)) { c ->
                        Studio.setOverlay(this, c)
                        if (Studio.getOverlayOpacity(this) == 0) Studio.setOverlayOpacity(this, 40)
                        syncAll()
                        repaintWidget()
                    }
                    return
                }
            }
            ovOpSlider.value = Studio.getOverlayOpacity(this).toFloat()
            ovOpText.text = "${Studio.getOverlayOpacity(this)}%"
            paintOptions(overlayButtons, overlayIndex())
            repaintPreview()
            repaintWidget()
        }

        // ---------- background controls ----------

        bgColorBtn.setOnClickListener {
            pickSimpleColor("Background color", Studio.getBgColor(this)) { c ->
                Studio.setBgColor(this, c)
                syncSwatches()
                repaintPreview()
                repaintWidget()
            }
        }
        grad1.setOnClickListener {
            pickSimpleColor("Gradient start", Studio.getBgColor(this)) { c ->
                Studio.setBgColor(this, c)
                syncSwatches()
                repaintPreview()
                repaintWidget()
            }
        }
        grad2.setOnClickListener {
            pickSimpleColor("Gradient end", Studio.getGrad2(this)) { c ->
                Studio.setGrad2(this, c)
                syncSwatches()
                repaintPreview()
                repaintWidget()
            }
        }
        grad3.setOnClickListener {
            pickSimpleColor("Gradient middle", Studio.getGrad3(this) ?: Studio.getGrad2(this)) { c ->
                Studio.setGrad3(this, c)
                gradThird.isChecked = true
                syncSwatches()
                repaintPreview()
                repaintWidget()
            }
        }
        gradThird.setOnClickListener {
            if (gradThird.isChecked) {
                Studio.setGrad3(this, Studio.getGrad2(this))
            } else {
                Studio.setGrad3(this, null)
            }
            repaintPreview()
            repaintWidget()
        }
        gradRadial.setOnClickListener {
            Studio.setGradRadial(this, gradRadial.isChecked)
            repaintPreview()
            repaintWidget()
        }

        findViewById<MaterialButton>(R.id.st_image_pick).setOnClickListener {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        findViewById<MaterialButton>(R.id.st_image_remove).setOnClickListener {
            Studio.setImageUri(this, "")
            Studio.setBgType(this, Studio.BG_SOLID)
            syncAll()
            repaintWidget()
            Toast.makeText(this, "Image removed", Toast.LENGTH_SHORT).show()
        }
        chkImgGraph.setOnClickListener {
            Studio.setImageThroughGraph(this, chkImgGraph.isChecked)
            repaintPreview()
            repaintWidget()
        }

        // ---------- sliders ----------

        fun liveSlider(
            slider: Slider,
            label: TextView,
            format: (Float) -> String,
            onRelease: (Float) -> Unit
        ) {
            slider.addOnChangeListener { _, v, _ -> label.text = format(v) }
            slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(s: Slider) = Unit
                override fun onStopTrackingTouch(s: Slider) {
                    onRelease(s.value)
                    repaintPreview()
                    repaintWidget()
                }
            })
        }

        liveSlider(cellSlider, cellText, { "${(it * 100).toInt()}%" }) {
            Studio.setCellSize(this, it)
        }
        liveSlider(spacingSlider, spacingText, {
            when {
                it < 1f -> "Tight"
                it > 1f -> "Airy"
                else -> "Normal"
            }
        }) { WidgetPrefs.setSpacing(this, it) }
        liveSlider(cornersSlider, cornersText, { "${it.toInt()}dp" }) {
            WidgetPrefs.setCorners(this, it.toInt())
        }
        liveSlider(bgOpSlider, bgOpText, { "${it.toInt()}%" }) {
            val v = it.toInt()
            Studio.setBgOpacity(this, v)
            Prefs.setOpacity(this, v.coerceIn(20, 100))
        }
        liveSlider(blurSlider, blurText, { if (it == 0f) "Off" else "${it.toInt()}" }) {
            Studio.setBlur(this, it.toInt())
        }
        liveSlider(ovOpSlider, ovOpText, { "${it.toInt()}%" }) {
            Studio.setOverlayOpacity(this, it.toInt())
            paintOptions(overlayButtons, overlayIndex())
        }

        // ---------- palette ----------

        findViewById<MaterialButton>(R.id.st_palette_generate).setOnClickListener {
            val ramp = Themes.fromBaseColor(theme().accent).levels
            Studio.setLevels(this, ramp)
            buildLevels()
            repaintPreview()
            repaintWidget()
            Toast.makeText(this, "Palette built from theme color", Toast.LENGTH_SHORT).show()
        }
        findViewById<MaterialButton>(R.id.st_palette_reset).setOnClickListener {
            Studio.setLevels(this, null)
            buildLevels()
            repaintPreview()
            repaintWidget()
        }

        // ---------- content ----------

        val onContent = View.OnClickListener {
            WidgetPrefs.setContent(
                this,
                chkTotal.isChecked, chkStreak.isChecked,
                chkLongest.isChecked, chkUpdated.isChecked
            )
            Studio.setShowActiveDays(this, chkActive.isChecked)
            repaintPreview()
            repaintWidget()
        }
        chkTotal.setOnClickListener(onContent)
        chkStreak.setOnClickListener(onContent)
        chkLongest.setOnClickListener(onContent)
        chkUpdated.setOnClickListener(onContent)
        chkActive.setOnClickListener(onContent)

        labelInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                Studio.setCustomLabel(this, labelInput.text.toString())
                repaintPreview()
                repaintWidget()
            }
        }

        // ---------- presets / reset / transfer ----------

        findViewById<MaterialButton>(R.id.st_reset).setOnClickListener {
            WidgetPrefs.resetToDefaults(this)
            Studio.resetBackground(this)
            Studio.resetGraph(this)
            Studio.setCustomLabel(this, "")
            Studio.setShowActiveDays(this, false)
            labelInput.setText("")
            syncAll()
            repaintWidget()
            Toast.makeText(this, "Studio look reset — username and data kept", Toast.LENGTH_SHORT).show()
        }

        findViewById<MaterialButton>(R.id.st_export).setOnClickListener {
            val json = Studio.exportLook(this)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, json)
            }
            startActivity(Intent.createChooser(intent, "Share look"))
        }

        findViewById<MaterialButton>(R.id.st_import).setOnClickListener {
            val field = EditText(this).apply {
                hint = "Paste a shared look"
                setPadding(dp(4))
            }
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(8), dp(20), dp(8))
                addView(field)
            }
            AlertDialog.Builder(this)
                .setTitle("Import look")
                .setView(box)
                .setPositiveButton("Import") { _, _ ->
                    val err = Studio.importLook(this, field.text.toString())
                    if (err == null) {
                        syncAll()
                        repaintWidget()
                        Toast.makeText(this, "Look imported", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, err, Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // ---------- init ----------

        val username = Prefs.getUsername(this)
        if (username.isBlank()) {
            title.text = "No username yet"
            subtitle.text = "Set it on the home screen first."
            footer.visibility = View.GONE
        } else {
            val cached = Cache.load(this)
            if (cached != null && cached.first.username.equals(username, ignoreCase = true)) {
                showResult(cached.first, TimeAgo.format(cached.second))
            } else {
                title.text = "@$username"
                subtitle.text = "Open the home screen once to fetch data."
            }
        }
        labelInput.setText(Studio.getCustomLabel(this))
        syncAll()
    }

    override fun onResume() {
        super.onResume()
        // Data may have refreshed while the Studio was open.
        val username = Prefs.getUsername(this)
        val cached = Cache.load(this)
        if (username.isNotBlank() && cached != null &&
            cached.first.username.equals(username, ignoreCase = true) &&
            (lastResult == null || cached.first.days.size != lastResult?.days?.size)
        ) {
            // Repaint through the normal path.
            recreate()
        }
    }
}
