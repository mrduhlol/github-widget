package com.example.githubwidget.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import com.example.githubwidget.ui.theme.AppTheme

private val quickColors = listOf(
    0xFF39D353, 0xFF2DA44E, 0xFF14B8A6, 0xFF06B6D4, 0xFF3B82F6, 0xFF6366F1,
    0xFF8B5CF6, 0xFFD946EF, 0xFFEC4899, 0xFFF43F5E, 0xFFEF4444, 0xFFF97316,
    0xFFF59E0B, 0xFFEAB308, 0xFF84CC16, 0xFFA3A3A3,
).map { it.toInt() }

/** Simple color chooser: a row of quick picks plus hue and brightness sliders. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1].coerceAtLeast(0.15f)) }
    var value by remember { mutableFloatStateOf(hsv[2].coerceAtLeast(0.35f)) }
    val color = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppTheme.colors.card,
        title = { Text("Pick a color", color = AppTheme.colors.textPrimary) },
        text = {
            Column {
                Box(
                    Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp)).background(color),
                )
                Spacer(Modifier.height(16.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    quickColors.forEach { c ->
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .clickable {
                                    val h = FloatArray(3)
                                    android.graphics.Color.colorToHSV(c, h)
                                    hue = h[0]; sat = h[1]; value = h[2]
                                },
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("Color", style = MaterialTheme.typography.labelMedium, color = AppTheme.colors.textSecondary)
                Spacer(Modifier.height(8.dp))
                GradientSlider(
                    fraction = hue / 360f,
                    brush = Brush.horizontalGradient(
                        (0..6).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f, 0.85f, 1f))) },
                    ),
                    thumb = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.85f, 1f))),
                ) { hue = it * 360f }
                Spacer(Modifier.height(16.dp))
                Text("Brightness", style = MaterialTheme.typography.labelMedium, color = AppTheme.colors.textSecondary)
                Spacer(Modifier.height(8.dp))
                GradientSlider(
                    fraction = (value - 0.35f) / 0.65f,
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, 0.35f))),
                            Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, 1f))),
                        ),
                    ),
                    thumb = color,
                ) { value = 0.35f + it * 0.65f }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(color.toArgb()) }) { Text("Use color") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun GradientSlider(fraction: Float, brush: Brush, thumb: Color, onChange: (Float) -> Unit) {
    val thumbSize = 28.dp
    BoxWithConstraints(Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.CenterStart) {
        val density = LocalDensity.current
        val thumbPx = with(density) { thumbSize.toPx() }
        val trackPx = constraints.maxWidth - thumbPx
        fun update(x: Float) = onChange(((x - thumbPx / 2) / trackPx).coerceIn(0f, 1f))
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(trackPx) { detectTapGestures { update(it.x) } }
                .pointerInput(trackPx) { detectDragGestures { change, _ -> update(change.position.x) } },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(brush))
            Box(
                Modifier
                    .offset { IntOffset((fraction.coerceIn(0f, 1f) * trackPx).roundToInt(), 0) }
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(thumb)
                    .border(3.dp, Color.White, CircleShape),
            )
        }
    }
}
