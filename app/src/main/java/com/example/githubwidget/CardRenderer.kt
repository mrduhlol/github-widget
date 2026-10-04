package com.example.githubwidget

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import kotlin.math.cos
import kotlin.math.sin

/**
 * V2 unified card rendering. One pipeline feeds the widget, the Studio
 * preview and the share card:
 *
 *   base (solid / gradient / transparent)
 *     -> image (fill or fit, own opacity)
 *     -> blur -> overlay -> rounded corners + border
 */
object CardRenderer {

    /** Background composite WITHOUT border — also feeds image-through-graph. */
    fun background(
        context: android.content.Context,
        w: Int,
        h: Int,
        opacityOverride: Int? = null
    ): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val type = Studio.getBgType(context)
        val opacity = (opacityOverride ?: Studio.getBgOpacity(context)).coerceIn(0, 100)

        if (type == Studio.BG_TRANSPARENT) {
            return bmp // fully transparent; overlay would defeat the point
        }

        if (type == Studio.BG_GRADIENT) {
            drawGradient(canvas, w, h, context)
        } else {
            canvas.drawColor(Studio.getBgColor(context))
        }

        if (type == Studio.BG_IMAGE) {
            val uri = Studio.getImageUri(context)
            val img = if (uri.isNotEmpty()) {
                sampleImage(context, uri, w, h, Studio.getImageScale(context))
            } else {
                null
            }
            if (img != null) {
                val alpha = (opacity * 255 / 100)
                val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { this.alpha = alpha }
                canvas.drawBitmap(img, 0f, 0f, paint)
                if (!img.isRecycled) img.recycle()
            }
        } else {
            // Solid + gradient fills: fold the opacity in with DST_OUT so the
            // exact hue survives and text drawn above stays crisp.
            val alpha = (opacity * 255 / 100)
            if (alpha < 255) {
                val dim = Paint().apply {
                    color = Color.argb(255 - alpha, 0, 0, 0)
                    xfermode = android.graphics.PorterDuffXfermode(
                        android.graphics.PorterDuff.Mode.DST_OUT
                    )
                }
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), dim)
            }
        }

        val blur = Studio.getBlur(context)
        var out = bmp
        if (blur > 0) {
            out = softBlur(bmp, blur)
            if (out !== bmp) bmp.recycle()
        }

        val overlayAlpha = (Studio.getOverlayOpacity(context).coerceIn(0, 80) * 255 / 100)
        if (overlayAlpha > 0) {
            val c = Studio.getOverlay(context)
            outCanvas(out).drawColor(
                Color.argb(
                    overlayAlpha,
                    Color.red(c), Color.green(c), Color.blue(c)
                )
            )
        }
        return out
    }

    private fun outCanvas(bmp: Bitmap): Canvas = Canvas(bmp)

    /** Full card: background clipped to rounded corners plus the border. */
    fun card(
        context: android.content.Context,
        w: Int,
        h: Int,
        cornerPx: Float,
        borderColor: Int
    ): Bitmap {
        val bg = background(context, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val path = Path().apply {
            addRoundRect(rect, cornerPx, cornerPx, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(path)
        canvas.drawBitmap(bg, 0f, 0f, null)
        canvas.restore()
        if (!bg.isRecycled) bg.recycle()
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = borderColor
        }
        canvas.drawRoundRect(rect, cornerPx, cornerPx, border)
        return bmp
    }

    private fun drawGradient(canvas: Canvas, w: Int, h: Int, context: android.content.Context) {
        val c1 = Studio.getBgColor(context)
        val c2 = Studio.getGrad2(context)
        val c3 = Studio.getGrad3(context)
        val colors = if (c3 != null) intArrayOf(c1, c2, c3) else intArrayOf(c1, c2)
        val shader = if (Studio.isGradRadial(context)) {
            RadialGradient(
                w / 2f, h / 2f, maxOf(w, h) / 2f,
                colors, null, Shader.TileMode.CLAMP
            )
        } else {
            val angle = Math.toRadians(Studio.getGradAngle(context).toDouble())
            // 90deg = top -> bottom.
            val dx = cos(angle - Math.PI / 2).toFloat()
            val dy = sin(angle - Math.PI / 2).toFloat()
            val cx = w / 2f
            val cy = h / 2f
            val rx = (w / 2f) * kotlin.math.abs(dx) + (h / 2f) * kotlin.math.abs(dy)
            LinearGradient(
                cx - dx * rx, cy - dy * rx, cx + dx * rx, cy + dy * rx,
                colors, null, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { this.shader = shader })
    }

    /**
     * Memory-safe sampled decode, cropped/fitted to exactly w x h.
     * Returns null for missing or unreadable images (caller falls back).
     */
    fun sampleImage(
        context: android.content.Context,
        uriStr: String,
        w: Int,
        h: Int,
        mode: String
    ): Bitmap? {
        return try {
            val uri = Uri.parse(uriStr)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / sample / 2 >= w && bounds.outHeight / sample / 2 >= h) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
            val raw = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val bw = raw.width.toFloat()
            val bh = raw.height.toFloat()
            if (mode == Studio.SCALE_FIT) {
                val s = minOf(w / bw, h / bh)
                val dw = bw * s
                val dh = bh * s
                canvas.drawBitmap(
                    raw, null,
                    RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f),
                    Paint(Paint.FILTER_BITMAP_FLAG)
                )
            } else {
                val s = maxOf(w / bw, h / bh)
                val dw = bw * s
                val dh = bh * s
                canvas.drawBitmap(
                    raw, null,
                    RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f),
                    Paint(Paint.FILTER_BITMAP_FLAG)
                )
            }
            if (!raw.isRecycled) raw.recycle()
            out
        } catch (_: Exception) {
            null
        }
    }

    /** Cheap blur: downscale then upscale. strength 1..25. */
    fun softBlur(src: Bitmap, strength: Int): Bitmap {
        val s = 1f / (1f + strength / 4f)
        val sw = (src.width * s).toInt().coerceAtLeast(1)
        val sh = (src.height * s).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(src, sw, sh, true)
        val out = Bitmap.createScaledBitmap(small, src.width, src.height, true)
        if (!small.isRecycled) small.recycle()
        return out
    }
}
