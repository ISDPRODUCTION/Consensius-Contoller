package com.consensius.controller.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradient
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.consensius.controller.R
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ─── Brand Font ──────────────────────────────────────────────────────────────
val GraffitiFont = FontFamily(
    Font(R.font.bangers, FontWeight.Normal)
)

val MainFont = FontFamily.SansSerif

// ─── NeoGlass Premium Color System ────────────────────────────────────────────
// Modern, sophisticated dark palette with glass/translucency aesthetic.
// - Deep matte backgrounds
// - Frosted glass surfaces
// - Electric blue with purple accent for dimension
// - Clean, high-contrast text hierarchy
object NeoColors {
    // Core surfaces
    val Background       = Color(0xFF08080E)  // Deeper black with subtle blue
    val Surface          = Color(0xFF111118)  // Card surface base
    val SurfaceLight     = Color(0xFF1A1A26)  // Elevated surface
    val GlassCard        = Color(0xE61A1A28)  // Frosted glass card (90% alpha)
    val GlassElevated    = Color(0xE6222238)  // Elevated glass card

    // Primary palette
    val ElectricBlue     = Color(0xFF3D8BFF)  // Vibrant primary blue
    val DeepBlue         = Color(0xFF1A3A6B)  // Deep ocean blue
    val IceBlue          = Color(0xFF7AB8FF)  // Light blue accent
    val SoftBlueGlow     = Color(0xFF1A4B8A)  // Subtle glow

    // Accent
    val Amethyst         = Color(0xFF7C5CFC)  // Purple accent for variety
    val AmethystGlow     = Color(0xFF4A2FBD)  // Purple glow
    val Cyan             = Color(0xFF00D4FF)  // Cyan accent

    // Feedback
    val Success          = Color(0xFF00D68F)  // Teal green
    val Error            = Color(0xFFFF4757)  // Coral red
    val Warning          = Color(0xFFFFAB38)  // Amber

    // Text
    val TextPrimary      = Color(0xFFF0F0F5)
    val TextSecondary    = Color(0xFF9898AF)
    val TextTertiary     = Color(0xFF5C5C75)
    val TextMuted        = Color(0xFF3A3A4A)

    // Borders & Dividers
    val GlassBorder      = Color(0x33FFFFFF)  // 20% white
    val GlassBorderLight = Color(0x1AFFFFFF)  // 10% white
    val Divider          = Color(0x0DFFFFFF)  // 5% white
    val AccentBorder     = ElectricBlue.copy(alpha = 0.25f)

    // Gradient tokens
    val BlueGradient     = listOf(ElectricBlue, IceBlue)
    val PurpleGradient   = listOf(Amethyst, ElectricBlue)
    val DarkGradient     = listOf(Surface, SurfaceLight)
}

// ─── Glass Gradients ──────────────────────────────────────────────────────────
fun glassCardGradient(alpha: Float = 0.92f): Brush = Brush.linearGradient(
    colors = listOf(
        Color(0xFF1C1C2E).copy(alpha = alpha),
        Color(0xFF141420).copy(alpha = alpha)
    ),
    start = Offset(0f, 0f),
    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
    tileMode = TileMode.Clamp
)

// ─── Material 3 Dark Color Scheme ─────────────────────────────────────────────
private val NeoDarkColorScheme = darkColorScheme(
    primary            = NeoColors.ElectricBlue,
    onPrimary          = Color.White,
    primaryContainer   = NeoColors.ElectricBlue.copy(alpha = 0.15f),
    onPrimaryContainer = NeoColors.IceBlue,
    secondary          = NeoColors.Amethyst,
    onSecondary        = Color.White,
    secondaryContainer = NeoColors.Amethyst.copy(alpha = 0.15f),
    onSecondaryContainer = NeoColors.Cyan,
    tertiary           = NeoColors.Cyan,
    onTertiary         = Color.White,
    background         = NeoColors.Background,
    onBackground       = NeoColors.TextPrimary,
    surface            = NeoColors.Surface,
    onSurface          = NeoColors.TextPrimary,
    surfaceVariant     = NeoColors.SurfaceLight,
    onSurfaceVariant   = NeoColors.TextSecondary,
    error              = NeoColors.Error,
    onError            = Color.White,
    outline            = NeoColors.GlassBorder,
    outlineVariant     = NeoColors.Divider,
)

// ─── Modern Typography ───────────────────────────────────────────────────────
// Clean, spacious, modern — inspired by SF Pro and Inter
val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.5).sp,
        color = NeoColors.TextPrimary
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
        color = NeoColors.TextPrimary
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
        color = NeoColors.TextPrimary
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
        color = NeoColors.TextPrimary
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
        color = NeoColors.TextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
        color = NeoColors.TextPrimary
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        color = NeoColors.TextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.2.sp,
        color = NeoColors.TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp,
        color = NeoColors.TextPrimary
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
        color = NeoColors.TextSecondary
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        color = NeoColors.TextPrimary
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
        color = NeoColors.TextPrimary
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
        color = NeoColors.TextSecondary
    ),
)

// ─── Ambient Particle Background ──────────────────────────────────────────────
// Floating particles that drift slowly — creates depth without distraction.
fun DrawScope.drawAmbientParticles(
    particleCount: Int = 8,
    primaryColor: Color = NeoColors.ElectricBlue.copy(alpha = 0.06f),
    secondaryColor: Color = NeoColors.Amethyst.copy(alpha = 0.04f),
    phase: Float = 0f
) {
    val rng = Random(seed = 123)
    val s = this.size
    for (i in 0 until particleCount) {
        val baseX = (i.toFloat() / particleCount) * s.width
        val baseY = (i.toFloat() / particleCount * 0.618f) * s.height
        val driftX = sin(phase + i * 0.7f) * 20f
        val driftY = cos(phase + i * 0.5f) * 15f
        val radius = 1.5f + rng.nextFloat() * 2.5f
        val color = if (i % 2 == 0) primaryColor else secondaryColor
        drawCircle(
            color = color.copy(alpha = color.alpha * (0.6f + 0.4f * sin(phase + i.toFloat()))),
            radius = radius,
            center = Offset(
                (baseX + driftX).coerceIn(0f, s.width),
                (baseY + driftY).coerceIn(0f, s.height)
            )
        )
    }
}

// ─── Subtle Grain Texture ────────────────────────────────────────────────────
fun DrawScope.drawSubtleGrain(opacity: Float = 0.02f) {
    val rng = Random(seed = 42)
    val s = this.size
    val dotCount = ((s.width * s.height) / 2000).toInt().coerceIn(80, 800)
    for (i in 0 until dotCount) {
        val x = rng.nextFloat() * s.width
        val y = rng.nextFloat() * s.height
        val r = rng.nextFloat() * 1.0f + 0.2f
        drawCircle(
            color = Color(1f, 1f, 1f, opacity * rng.nextFloat()),
            radius = r,
            center = Offset(x, y)
        )
    }
}

// ─── Glass Shimmer ────────────────────────────────────────────────────────────
// Subtle sheen effect for glass cards
fun DrawScope.drawGlassShimmer(
    progress: Float = 0f,
    cardWidth: Float,
    cardHeight: Float,
    cardOffset: Offset = Offset.Zero
) {
    val shimmerWidth = cardWidth * 0.3f
    val xPos = cardOffset.x + (progress * (cardWidth + shimmerWidth)) - shimmerWidth
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Color.Transparent,
            Color.White.copy(alpha = 0.03f),
            Color.Transparent
        ),
        start = Offset(xPos, 0f),
        end = Offset(xPos + shimmerWidth, cardHeight)
    )
    drawRect(shimmerBrush)
}

// ─── Corner Glow ──────────────────────────────────────────────────────────────
@Composable
fun CornerGlow(
    modifier: Modifier = Modifier,
    color: Color = NeoColors.ElectricBlue,
    size: Dp = 40.dp
) {
    Canvas(modifier = Modifier.size(size)) {
        val r = size.toPx() / 2
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = 0.12f),
                    color.copy(alpha = 0.03f),
                    Color.Transparent
                )
            ),
            radius = r
        )
    }
}

// ─── Theme ────────────────────────────────────────────────────────────────────
@Composable
fun ConsensiusControllerTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NeoDarkColorScheme,
        typography = AppTypography,
        content = content
    )
}
