package com.example.githubwidget.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * Extra colors Material doesn't have slots for. Read them with
 * `AppTheme.colors.success` etc.
 */
data class ExtraColors(
    /** Screen background behind cards. */
    val canvas: Color,
    /** Card surface. */
    val card: Color,
    /** Slightly raised element inside a card (chips, tiles, segmented track). */
    val raised: Color,
    val hairline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    /** The 5 GitHub green levels for decorative graphs in the app UI. */
    val graph: List<Color>,
    val isDark: Boolean,
)

private val LightExtra = ExtraColors(
    canvas = Color(0xFFF6F8FA),
    card = Color(0xFFFFFFFF),
    raised = Color(0xFFEFF2F5),
    hairline = Color(0xFFDDE2E7),
    textPrimary = Color(0xFF1F2328),
    textSecondary = Color(0xFF59636E),
    textTertiary = Color(0xFF8C959F),
    success = Color(0xFF1A7F37),
    warning = Color(0xFF9A6700),
    danger = Color(0xFFCF222E),
    graph = listOf(Color(0xFFEFF2F5), Color(0xFF9BE9A8), Color(0xFF40C463), Color(0xFF30A14E), Color(0xFF216E39)),
    isDark = false,
)

private val DarkExtra = ExtraColors(
    canvas = Color(0xFF0B0E13),
    card = Color(0xFF141920),
    raised = Color(0xFF1D232C),
    hairline = Color(0xFF262D37),
    textPrimary = Color(0xFFF0F6FC),
    textSecondary = Color(0xFF9198A1),
    textTertiary = Color(0xFF656D76),
    success = Color(0xFF3FB950),
    warning = Color(0xFFD29922),
    danger = Color(0xFFF85149),
    graph = listOf(Color(0xFF1D232C), Color(0xFF0E4429), Color(0xFF006D32), Color(0xFF26A641), Color(0xFF39D353)),
    isDark = true,
)

private fun scheme(x: ExtraColors): ColorScheme {
    val base = if (x.isDark) darkColorScheme() else lightColorScheme()
    val primary = if (x.isDark) Color(0xFF3FB950) else Color(0xFF1F883D)
    return base.copy(
        primary = primary,
        onPrimary = Color.White,
        primaryContainer = primary.copy(alpha = 0.16f),
        onPrimaryContainer = if (x.isDark) Color(0xFF7EE787) else Color(0xFF116329),
        secondary = primary,
        onSecondary = Color.White,
        secondaryContainer = x.raised,
        onSecondaryContainer = x.textPrimary,
        background = x.canvas,
        onBackground = x.textPrimary,
        surface = x.card,
        onSurface = x.textPrimary,
        surfaceVariant = x.raised,
        onSurfaceVariant = x.textSecondary,
        surfaceContainerLowest = x.card,
        surfaceContainerLow = x.card,
        surfaceContainer = x.card,
        surfaceContainerHigh = x.card,
        surfaceContainerHighest = x.raised,
        outline = x.hairline,
        outlineVariant = x.hairline,
        error = x.danger,
        inverseSurface = x.textPrimary,
        inverseOnSurface = x.card,
    )
}

private val sans = FontFamily.SansSerif

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.4.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val LocalExtraColors = staticCompositionLocalOf { DarkExtra }

object AppTheme {
    val colors: ExtraColors @Composable get() = LocalExtraColors.current
}

@Composable
fun GhTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val extra = if (dark) DarkExtra else LightExtra
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalExtraColors provides extra) {
        MaterialTheme(colorScheme = scheme(extra), typography = AppTypography, shapes = AppShapes, content = content)
    }
}
