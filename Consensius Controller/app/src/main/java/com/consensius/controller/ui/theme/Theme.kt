package com.consensius.controller.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ─── Brand Colors ────────────────────────────────────────────────────────────
object ConsensiusColors {
    val Background     = Color(0xFF0A0A0F)
    val Surface        = Color(0xFF0D1117)
    val Card           = Color(0xFF0F1624)
    val Accent         = Color(0xFF0084FF)
    val AccentSecondary= Color(0xFF00C2FF)
    val Success        = Color(0xFF00E676)
    val Error          = Color(0xFFFF1744)
    val TextPrimary    = Color(0xFFE0E0E0)
    val TextSecondary  = Color(0xFF8899AA)
    val CardBorder     = Color(0xFF0084FF).copy(alpha = 0.2f)
    val GlowBlue       = Color(0xFF0084FF)
    val Transparent    = Color.Transparent
}

// ─── Color Scheme ─────────────────────────────────────────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary            = ConsensiusColors.Accent,
    secondary          = ConsensiusColors.AccentSecondary,
    background         = ConsensiusColors.Background,
    surface            = ConsensiusColors.Surface,
    onPrimary          = Color.White,
    onSecondary        = Color.White,
    onBackground       = ConsensiusColors.TextPrimary,
    onSurface          = ConsensiusColors.TextPrimary,
    error              = ConsensiusColors.Error,
    onError            = Color.White,
    tertiary           = ConsensiusColors.Success,
    surfaceVariant     = ConsensiusColors.Card,
    onSurfaceVariant   = ConsensiusColors.TextSecondary,
)

@Composable
fun ConsensiusControllerTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    // App always uses dark theme to match futuristic design system
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
