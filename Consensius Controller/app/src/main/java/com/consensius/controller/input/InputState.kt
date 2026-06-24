package com.consensius.controller.input

data class InputState(
    val leftX: Float = 0f,
    val leftY: Float = 0f,
    val rightX: Float = 0f,
    val rightY: Float = 0f,
    val buttons: Map<String, Boolean> = emptyMap()
)
