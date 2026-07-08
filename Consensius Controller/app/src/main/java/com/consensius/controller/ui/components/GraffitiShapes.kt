package com.consensius.controller.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Standard rounded shape for glass cards — 16dp corners.
 */
val GlassCardShape = RoundedCornerShape(16.dp)

/**
 * Shape for compact elements — 12dp corners.
 */
val CompactShape = RoundedCornerShape(12.dp)

/**
 * Button shape — 14dp corners.
 */
val GlassButtonShape = RoundedCornerShape(14.dp)

/**
 * Pill shape for tags and badges.
 */
val PillShape = RoundedCornerShape(50)

/**
 * Premium card shape with slightly asymmetrical bottom-right corner.
 */
class PremiumCardShape(
    private val topRadius: Float = 16f,
    private val bottomRadius: Float = 14f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val tr = topRadius * density.density
        val br = bottomRadius * density.density
        return Outline.Rounded(
            RoundRect(
                rect = Rect(0f, 0f, size.width, size.height),
                cornerRadius = CornerRadius(tr, tr)
            )
        )
    }
}

// ─── Backward Compatibility ──────────────────────────────────────────────────

val PremiumCardShapeCompat = RoundedCornerShape(14.dp)

class PremiumButtonShape(
    private val cornerRadius: Float = 14f,
    private val cutSize: Float = 6f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        return Outline.Rounded(
            RoundRect(
                rect = Rect(0f, 0f, size.width, size.height),
                cornerRadius = CornerRadius(cornerRadius * density.density)
            )
        )
    }
}

class GraffitiButtonShape(private val cut: Float = 10f) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        return PremiumButtonShape(cornerRadius = 14f, cutSize = cut).createOutline(size, layoutDirection, density)
    }
}
