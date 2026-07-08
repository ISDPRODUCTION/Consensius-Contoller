package com.consensius.controller.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.consensius.controller.ui.theme.NeoColors

/**
 * NeoGlass Card — premium frosted glass card with subtle border glow.
 *
 * Features:
 * - Frosted glass gradient background
 * - Subtle border glow that pulses gently
 * - Soft elevation shadow
 * - Rounded corners (16dp)
 */
@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Unspecified,
    borderColor: Color = NeoColors.GlassBorder,
    showBorder: Boolean = true,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val bg = if (backgroundColor == Color.Unspecified) NeoColors.GlassCard else backgroundColor

    Box(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .then(
                if (showBorder) Modifier.border(
                    width = 0.5.dp,
                    color = borderColor,
                    shape = shape
                ) else Modifier
            )
            .padding(16.dp)
    ) {
        content()
    }
}

/**
 * Glass raised card with a subtle animated border glow.
 * Use for primary/featured cards.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    glowColor: Color = NeoColors.ElectricBlue,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val infiniteTransition = rememberInfiniteTransition(label = "glass_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(NeoColors.GlassElevated)
            .border(
                width = 1.dp,
                color = glowColor.copy(alpha = glowAlpha),
                shape = shape
            )
            .padding(16.dp)
    ) {
        content()
    }
}

/**
 * Compact premium card — smaller padding for list items.
 */
@Composable
fun CompactCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = NeoColors.GlassCard,
    borderColor: Color = NeoColors.GlassBorder,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(
                width = 0.5.dp,
                color = borderColor,
                shape = shape
            )
            .padding(12.dp)
    ) {
        content()
    }
}

// ─── Backward Compatibility ──────────────────────────────────────────────────

@Composable
fun GraffitiCard(
    modifier: Modifier = Modifier,
    rotationDeg: Float = 0f,
    backgroundColor: Color = Color(0xFF101826),
    content: @Composable () -> Unit
) {
    PremiumCard(
        modifier = modifier,
        backgroundColor = backgroundColor,
        content = content
    )
}
