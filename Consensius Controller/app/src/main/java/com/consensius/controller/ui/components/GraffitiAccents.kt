package com.consensius.controller.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.consensius.controller.ui.theme.NeoColors

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
