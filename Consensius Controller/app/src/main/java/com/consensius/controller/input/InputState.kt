package com.consensius.controller.input

/**
 * Single source of truth for all live controller input values.
 * The FPS-throttled send loop reads from this; individual touch handlers write to it.
 */
data class InputState(
    val leftX:   Float = 0f,
    val leftY:   Float = 0f,
    val rightX:  Float = 0f,
    val rightY:  Float = 0f,
    // Map of key → pressed state (for continuous-send buttons via InputState)
    val buttons: Map<String, Boolean> = emptyMap()
)
