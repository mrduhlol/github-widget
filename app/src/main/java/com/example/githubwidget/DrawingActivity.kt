package com.example.githubwidget

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import java.io.File
import java.io.FileOutputStream

/** Drawing Studio: draw a background, export it, use it on the widget. */
class DrawingActivity : AppCompatActivity() {

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun drawingsDir(): File = File(filesDir, "drawings").apply { mkdirs() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drawing)

        val canvas = findViewById<DrawingView>(R.id.drawing_canvas)
        val rowTools = findViewById<LinearLayout>(R.id.drawing_tools)
        val colorBtn = findViewById<MaterialButton>(R.id.drawing_color)
        val sizeText = findViewById<TextView>(R.id.drawing_size_text)
        val sizeSlider = findViewById<Slider>(R.id.drawing_size)
        val undoBtn = findViewById<MaterialButton>(R.id.drawing_undo)
        val redoBtn = findViewById<MaterialButton>(R.id.drawing_redo)

        val tools = listOf(
            "Brush" to DrawingView.TOOL_BRUSH,
            "Eraser" to DrawingView.TOOL_ERASER,
            "Glow" to DrawingView.TOOL_HIGHLIGHT,
            "Line" to DrawingView.TOOL_LINE,
            "Rect" to DrawingView.TOOL_RECT,
            "Circle" to DrawingView.TOOL_CIRCLE,
            "Fill" to DrawingView.TOOL_FILL
        )
        val toolButtons = tools.map { (name, _) ->
            MaterialButton(this).apply {
                text = name
                textSize = 12f
                cornerRadius = dp(10)
                minimumWidth = 0
                minWidth = 0
                setPadding(dp(6), 0, dp(6), 0)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.marginEnd = dp(8)
                layoutParams = lp
                contentDescription = "$name tool"
                rowTools.addView(this)
            }
        }
        toolButtons.forEachIndexed { i, b ->
            b.setOnClickListener {
                canvas.tool = tools[i].second
                paintTools(toolButtons, i)
            }
        }

        fun paintTools(buttons: List<MaterialButton>, selected: Int) {
            val accent = Color.parseColor("#39D353")
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
        paintTools(toolButtons, 0)

        fun syncHistory() {
            undoBtn.isEnabled = canvas.canUndo
            redoBtn.isEnabled = canvas.canRedo
            undoBtn.alpha = if (canvas.canUndo) 1f else 0.4f
            redoBtn.alpha = if (canvas.canRedo) 1f else 0.4f
        }
        canvas.onChange = { syncHistory() }
        syncHistory()

        colorBtn.backgroundTintList = ColorStateList.valueOf(canvas.color)
        colorBtn.setOnClickListener {
            ColorPickerDialog.show(this, canvas.color) { c ->
                canvas.color = c
                colorBtn.backgroundTintList = ColorStateList.valueOf(c)
            }
        }

        sizeSlider.addOnChangeListener { _, v, _ ->
            sizeText.text = "${v.toInt()}px"
            canvas.strokeWidth = v
        }

        undoBtn.setOnClickListener { canvas.undo() }
        redoBtn.setOnClickListener { canvas.redo() }
        findViewById<MaterialButton>(R.id.drawing_clear).setOnClickListener { canvas.clear() }

        fun savePng(): File? {
            return try {
                val file = File(drawingsDir(), "drawing_${System.currentTimeMillis()}.png")
                FileOutputStream(file).use { out ->
                    canvas.export().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }
                // Keep the folder tidy: newest 10 drawings only.
                drawingsDir().listFiles()
                    ?.sortedByDescending { it.lastModified() }
                    ?.drop(10)
                    ?.forEach { it.delete() }
                file
            } catch (_: Exception) {
                null
            }
        }

        findViewById<MaterialButton>(R.id.drawing_save).setOnClickListener {
            if (canvas.isEmpty) {
                Toast.makeText(this, "Draw something first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (savePng() != null) {
                Toast.makeText(this, "Drawing saved", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Couldn't save the drawing.", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<MaterialButton>(R.id.drawing_use_bg).setOnClickListener {
            if (canvas.isEmpty) {
                Toast.makeText(this, "Draw something first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val file = savePng()
            if (file == null) {
                Toast.makeText(this, "Couldn't save the drawing.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Studio.setImageUri(this, "file://${file.absolutePath}")
            Studio.setBgType(this, Studio.BG_IMAGE)
            Prefs.requestRepaint(this)
            Toast.makeText(this, "Drawing is now your widget background", Toast.LENGTH_SHORT).show()
            finish()
        }

        findViewById<MaterialButton>(R.id.drawing_share).setOnClickListener {
            if (canvas.isEmpty) {
                Toast.makeText(this, "Draw something first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            try {
                val bmp = canvas.export()
                ShareCard.share(this, bmp)
                bmp.recycle()
            } catch (_: Exception) {
                Toast.makeText(this, "Couldn't share the drawing.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
