package com.example.githubwidget.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.githubwidget.data.Repository
import com.example.githubwidget.data.UserData
import com.example.githubwidget.design.WidgetDesign
import com.example.githubwidget.ui.theme.AppTheme
import com.example.githubwidget.widget.WidgetRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ------------------------------------------------------------------ layout

/** Large page title with optional subtitle and trailing action. */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = AppTheme.colors.textPrimary)
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
            }
        }
        action?.invoke()
    }
}

/** A rounded surface that groups related controls, with an optional title. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    contentPadding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            SectionLabel(title, Modifier.padding(start = 4.dp, bottom = 8.dp))
        }
        Surface(
            shape = MaterialTheme.shapes.large,
            color = AppTheme.colors.card,
            border = BorderStroke(1.dp, AppTheme.colors.hairline),
        ) {
            Column(Modifier.fillMaxWidth().padding(contentPadding)) {
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
                    Spacer(Modifier.height(12.dp))
                }
                content()
            }
        }
    }
}

/** Small uppercase label above a group. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = AppTheme.colors.textTertiary,
    )
}

/** A labelled control inside a card: title on top, control below. */
@Composable
fun Field(label: String, modifier: Modifier = Modifier, hint: String? = null, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.textPrimary)
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(AppTheme.colors.hairline))
}

// ------------------------------------------------------------------ buttons

/** The one obvious action on a screen. Full width, 56dp tall. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
            disabledContentColor = Color.White.copy(alpha = 0.8f),
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            if (icon != null) {
                Icon(icon, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, AppTheme.colors.hairline),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = AppTheme.colors.card,
            contentColor = AppTheme.colors.textPrimary,
        ),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ------------------------------------------------------------------ choices

/**
 * Pill-shaped segmented control for 2–4 short options. The selected option
 * gets a raised "thumb", like iOS/Android settings.
 */
@Composable
fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppTheme.colors.raised)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSel = option == selected
            val bg by animateColorAsState(if (isSel) AppTheme.colors.card else Color.Transparent, label = "seg")
            val fg by animateColorAsState(
                if (isSel) AppTheme.colors.textPrimary else AppTheme.colors.textSecondary, label = "segText",
            )
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .then(if (isSel) Modifier.shadow(1.dp, RoundedCornerShape(10.dp)) else Modifier)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .selectable(isSel, role = Role.RadioButton) { onSelect(option) }
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Medium,
                    color = fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** A row with a title, optional description and a switch. The whole row is tappable. */
@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) AppTheme.colors.textPrimary else AppTheme.colors.textTertiary,
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedTrackColor = AppTheme.colors.raised,
                uncheckedBorderColor = AppTheme.colors.hairline,
            ),
        )
    }
}

/** A tappable settings-style row with leading icon and trailing chevron (or [trailing]). */
@Composable
fun NavRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = AppTheme.colors.textSecondary,
    titleColor: Color = AppTheme.colors.textPrimary,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(AppTheme.colors.raised),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(20.dp), tint = iconTint)
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
            }
        }
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                tint = AppTheme.colors.textTertiary,
            )
        }
    }
}

// ------------------------------------------------------------------ identity

/** Circular avatar; falls back to the first letter of [name]. */
@Composable
fun Avatar(bitmap: Bitmap?, name: String, size: Dp, modifier: Modifier = Modifier) {
    val image: ImageBitmap? = remember(bitmap) { bitmap?.asImageBitmap() }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
            .border(1.dp, AppTheme.colors.hairline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else {
            Text(
                name.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ------------------------------------------------------------------ widget preview

/** Typical home-screen sizes, in dp, for previews. */
enum class PreviewSize(val label: String, val widthDp: Float, val heightDp: Float) {
    SMALL("Small", 170f, 170f),
    WIDE("Wide", 340f, 170f),
    LARGE("Large", 340f, 260f),
}

/**
 * Renders the real widget bitmap (same code as the home screen) at
 * [widthDp]×[heightDp], scaled to fit the available width.
 */
@Composable
fun WidgetPreview(
    design: WidgetDesign,
    data: UserData?,
    avatar: Bitmap?,
    widthDp: Float,
    heightDp: Float,
    modifier: Modifier = Modifier,
    dark: Boolean = AppTheme.colors.isDark,
) {
    val context = LocalContext.current
    val font by Repository.numberFont.collectAsState()
    val bitmap by produceState<ImageBitmap?>(null, design, data, avatar, widthDp, heightDp, dark, font) {
        value = withContext(Dispatchers.Default) {
            WidgetRenderer.render(context, design, data, avatar, widthDp, heightDp, dark, font = font).asImageBitmap()
        }
    }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        var scale = minOf(1f, maxWidth.value / widthDp)
        if (constraints.hasBoundedHeight) scale = minOf(scale, maxHeight.value / heightDp)
        val w = (widthDp * scale).dp
        val h = (heightDp * scale).dp
        val shown = bitmap
        if (shown != null) {
            Image(shown, contentDescription = "Widget preview", modifier = Modifier.size(w, h))
        } else {
            Spacer(Modifier.size(w, h))
        }
    }
}

/**
 * A soft gradient "wallpaper" behind widget previews, so see-through
 * backgrounds look like they will on a real home screen.
 */
@Composable
fun WallpaperBackdrop(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val dark = AppTheme.colors.isDark
    val brush = androidx.compose.ui.graphics.Brush.linearGradient(
        if (dark) listOf(Color(0xFF1B2A4A), Color(0xFF3B1F4F), Color(0xFF0F3B3A))
        else listOf(Color(0xFFB8D4FF), Color(0xFFF5C6E8), Color(0xFFC4F1E0)),
    )
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(brush)
            .padding(horizontal = 16.dp, vertical = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** Animated border width for selectable cards. */
@Composable
fun selectionBorder(selected: Boolean): Dp {
    val w by animateDpAsState(if (selected) 2.dp else 1.dp, label = "border")
    return w
}
