package com.consensius.controller.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.consensius.controller.ui.theme.NeoColors
import kotlin.random.Random

/**
 * Neo glass corner accent — a subtle arc in the top-left corner.
 * Clean, elegant brand touch without being distracting.
 */
@Composable
fun GlassCornerAccent(
    modifier: Modifier = Modifier,
    color: Color = NeoColors.ElectricBlue,
    alpha: Float = 0.2f,
    strokeWidth: Float = 2.5f
) {
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(0f, size.height * 0.35f)
            cubicTo(
                size.width * 0.03f, size.height * 0.08f,
                size.width * 0.1f, size.height * 0.02f,
                size.width * 0.3f, 0f
            )
        }
        drawPath(
            path = path,
            color = color.copy(alpha = alpha),
            style = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
    }
}

@Composable
fun BrandCornerAccent(
    modifier: Modifier = Modifier,
    color: Color = NeoColors.ElectricBlue,
    alpha: Float = 0.25f
) {
    GlassCornerAccent(modifier = modifier, color = color, alpha = alpha)
}

@Composable
fun GraffitiCornerStroke(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    alpha: Float = 0.35f
) {
    GlassCornerAccent(modifier = modifier, color = color, alpha = alpha.coerceAtMost(0.2f))
}

/**
 * Modern divider with gradient rather than solid line.
 */
@Composable
fun GlassDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 1.dp
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val y = size.height / 2
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    NeoColors.GlassBorder,
                    Color.Transparent
                )
            ),
            topLeft = Offset(0f, y - thickness.toPx() / 2),
            size = Size(size.width, thickness.toPx()),
            cornerRadius = CornerRadius(thickness.toPx() / 2)
        )
    }
}

@Composable
fun BrandDivider(
    modifier: Modifier = Modifier,
    color: Color = NeoColors.Divider,
    dotColor: Color = NeoColors.ElectricBlue.copy(alpha = 0.25f)
) {
    GlassDivider(modifier = modifier)
}

@Composable
fun GraffitiDivider(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF3D8BFF)
) {
    GlassDivider(modifier = modifier)
}

/**
 * Subtle ambient glow behind key elements.
 */
@Composable
fun AmbientGlow(
    modifier: Modifier = Modifier,
    color: Color = NeoColors.ElectricBlue,
    maxRadius: Dp = 60.dp
) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = 0.08f),
                    color.copy(alpha = 0.02f),
                    Color.Transparent
                )
            ),
            radius = maxRadius.toPx(),
            center = center
        )
    }
}

@Composable
fun BrandSpraySplatter(
    modifier: Modifier = Modifier,
    color: Color = NeoColors.ElectricBlue,
    dotCount: Int = 6,
    radius: Float = 50f
) {
    val seed = remember { Random(System.currentTimeMillis()) }
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        repeat(dotCount) {
            val angle = seed.nextFloat() * 360f
            val dist = seed.nextFloat() * radius
            val rad = Math.toRadians(angle.toDouble())
            val dotX = center.x + (dist * kotlin.math.cos(rad)).toFloat()
            val dotY = center.y + (dist * kotlin.math.sin(rad)).toFloat()
            val dotRadius = seed.nextFloat() * 1.5f + 0.5f
            drawCircle(
                color = color.copy(alpha = seed.nextFloat() * 0.15f + 0.03f),
                radius = dotRadius,
                center = Offset(dotX, dotY)
            )
        }
    }
}

@Composable
fun SpraySplatter(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    dotCount: Int = 10,
    radius: Float = 70f
) {
    BrandSpraySplatter(
        modifier = modifier,
        color = color,
        dotCount = dotCount.coerceAtMost(6),
        radius = radius.coerceAtMost(50f)
    )
}

/**
 * Minimal dot-grid background texture.
 */
@Composable
fun DotGridBackground(
    modifier: Modifier = Modifier,
    dotColor: Color = NeoColors.TextMuted,
    spacing: Dp = 28.dp,
    dotRadius: Float = 1.2f
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val sp = spacing.toPx()
        var x = sp
        while (x < size.width) {
            var y = sp
            while (y < size.height) {
                drawCircle(
                    color = dotColor.copy(alpha = 0.5f),
                    radius = dotRadius,
                    center = Offset(x, y)
                )
                y += sp
            }
            x += sp
        }
    }
}

@Composable
fun SubtleSurfaceTexture(
    modifier: Modifier = Modifier,
    dotColor: Color = Color.White,
    density: Int = 100
) {
    val seed = remember { Random(42) }
    Canvas(modifier = modifier.fillMaxSize()) {
        repeat(density) {
            val x = seed.nextFloat() * size.width
            val y = seed.nextFloat() * size.height
            drawCircle(
                color = dotColor.copy(alpha = seed.nextFloat() * 0.025f),
                radius = 0.7f,
                center = Offset(x, y)
            )
        }
    }
}

@Composable
fun ConcreteNoiseBackground(
    modifier: Modifier = Modifier,
    dotColor: Color = Color.White,
    density: Int = 200
) {
    SubtleSurfaceTexture(modifier = modifier, dotColor = dotColor, density = density.coerceAtMost(100))
}

@Composable
fun BrandDripAccent(
    modifier: Modifier = Modifier,
    color: Color = NeoColors.ElectricBlue
) {
    val seed = remember { Random(3) }
    Canvas(modifier = modifier) {
        repeat(2) {
            val x = seed.nextFloat() * size.width
            val len = 5f + seed.nextFloat() * 10f
            drawLine(
                color = color.copy(alpha = 0.2f),
                start = Offset(x, 0f),
                end = Offset(x, len),
                strokeWidth = 1.5f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }
}

@Composable
fun DripAccent(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF3D8BFF)
) {
    BrandDripAccent(modifier = modifier, color = color)
}
