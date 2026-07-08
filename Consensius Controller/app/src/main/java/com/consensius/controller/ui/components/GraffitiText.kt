package com.consensius.controller.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.consensius.controller.ui.theme.GraffitiFont
import com.consensius.controller.ui.theme.NeoColors

/**
 * Brand heading with gradient fill — for logo and hero moments.
 * Uses Bangers font for brand identity.
 */
@Composable
fun BrandHeaderText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 24.sp,
    gradient: Brush = Brush.linearGradient(
        colors = listOf(NeoColors.ElectricBlue, NeoColors.IceBlue)
    ),
    outlineColor: Color = NeoColors.DeepBlue,
    outlineWidthPx: Float = 3f
) {
    Box(modifier = modifier) {
        BasicText(
            text = text,
            style = TextStyle(
                fontFamily = GraffitiFont,
                fontSize = fontSize,
                color = outlineColor,
                drawStyle = Stroke(width = outlineWidthPx)
            )
        )
        BasicText(
            text = text,
            style = TextStyle(
                fontFamily = GraffitiFont,
                fontSize = fontSize,
                brush = gradient,
                drawStyle = Fill
            )
        )
    }
}

@Composable
fun GraffitiHeaderText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 28.sp,
    fillColor: Color = Color.White,
    outlineColor: Color = Color.Black,
    outlineWidthPx: Float = 6f
) {
    BrandHeaderText(
        text = text,
        modifier = modifier,
        fontSize = fontSize,
        outlineColor = NeoColors.DeepBlue,
        outlineWidthPx = outlineWidthPx.coerceAtMost(3f)
    )
}

/**
 * Modern section header with accent dot prefix.
 */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 11.sp,
    color: Color = NeoColors.TextSecondary,
    showDot: Boolean = true
) {
    BasicText(
        text = if (showDot) "●  $text" else text,
        style = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            color = color,
            letterSpacing = 1.5.sp
        ),
        modifier = modifier
    )
}

/**
 * Gradient section header — for prominent section titles.
 */
@Composable
fun GradientSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 13.sp,
    gradient: Brush = Brush.linearGradient(
        colors = listOf(NeoColors.ElectricBlue, NeoColors.Amethyst)
    )
) {
    BasicText(
        text = text,
        style = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            brush = gradient,
            letterSpacing = 1.2.sp
        ),
        modifier = modifier
    )
}

/**
 * Value label — large numeric or short value display.
 */
@Composable
fun ValueText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 22.sp,
    color: Color = NeoColors.TextPrimary
) {
    BasicText(
        text = text,
        style = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            color = color
        ),
        modifier = modifier
    )
}

/**
 * Metric label — small descriptive label for values.
 */
@Composable
fun MetricLabel(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 10.sp,
    color: Color = NeoColors.TextTertiary
) {
    BasicText(
        text = text,
        style = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            color = color,
            letterSpacing = 0.8.sp
        ),
        modifier = modifier
    )
}
