package com.consensius.controller.model

import java.util.UUID

// ─── Element Types ────────────────────────────────────────────────────────────

enum class ElementType { BUTTON, JOYSTICK, DPAD, TOUCHPAD }
enum class ElementSize { S, M, L }
enum class JoystickType { MOVEMENT, SKILL_AIM }

/** Returns element size in dp. */
fun ElementSize.toDp(): Float = when (this) {
    ElementSize.S -> 52f
    ElementSize.M -> 68f
    ElementSize.L -> 84f
}

// Legacy type alias so ControllerScreen still compiles
typealias ButtonSize = ElementSize

// ─── Element Config (per-type) ────────────────────────────────────────────────

data class ButtonElementConfig(
    val key: String = "k"
)

data class JoystickElementConfig(
    val type: JoystickType = JoystickType.MOVEMENT,
    val skillKey: String = ""       // only when type == SKILL_AIM
)

data class DpadElementConfig(
    val upKey: String = "w",
    val downKey: String = "s",
    val leftKey: String = "a",
    val rightKey: String = "d"
)

data class TouchpadElementConfig(
    val sensitivityMultiplier: Float = 1.0f,
    val leftClickKey: String = "mouse1",
    val rightClickKey: String = "mouse2"
)

// ─── Canvas Element ───────────────────────────────────────────────────────────

/**
 * A single controller element on the canvas.
 * Position (x, y) is stored as a fraction of canvas dimensions (0.0 – 1.0).
 */
data class CanvasElement(
    val id: String = UUID.randomUUID().toString(),
    val type: ElementType = ElementType.BUTTON,
    val label: String = "BTN",
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val size: ElementSize = ElementSize.M,
    val buttonConfig: ButtonElementConfig = ButtonElementConfig(),
    val joystickConfig: JoystickElementConfig = JoystickElementConfig(),
    val dpadConfig: DpadElementConfig = DpadElementConfig(),
    val touchpadConfig: TouchpadElementConfig = TouchpadElementConfig()
)

// ─── Profile Page ─────────────────────────────────────────────────────────────

data class ProfilePage(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Page 1",
    val elements: List<CanvasElement> = emptyList()
)

// ─── Controller Profile ───────────────────────────────────────────────────────

data class ControllerProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "New Profile",
    val pages: List<ProfilePage> = listOf(ProfilePage())
) {
    /** Legacy computed property for ControllerScreen backward-compat. */
    val buttons: List<ButtonConfig> get() = pages.flatMap { page ->
        page.elements.filter { it.type == ElementType.BUTTON }.map { el ->
            ButtonConfig(
                id    = el.id,
                label = el.label,
                key   = el.buttonConfig.key,
                x     = el.x,
                y     = el.y,
                size  = el.size
            )
        }
    }

    /** Legacy computed property – checks if any joystick is SKILL_AIM mode. */
    val rightJoystickMode: RightJoystickMode get() {
        val hasSkillAim = pages.flatMap { it.elements }.any {
            it.type == ElementType.JOYSTICK && it.joystickConfig.type == JoystickType.SKILL_AIM
        }
        return if (hasSkillAim) RightJoystickMode.SKILL_AIM else RightJoystickMode.DIRECT
    }

    /** Legacy computed property – first D-Pad mapping. */
    val dpadMapping: DpadMapping get() {
        val dpadEl = pages.flatMap { it.elements }.firstOrNull { it.type == ElementType.DPAD }
        return if (dpadEl != null) {
            DpadMapping(
                up    = dpadEl.dpadConfig.upKey,
                down  = dpadEl.dpadConfig.downKey,
                left  = dpadEl.dpadConfig.leftKey,
                right = dpadEl.dpadConfig.rightKey
            )
        } else DpadMapping()
    }
}

// ─── Legacy Models (kept for ControllerScreen) ────────────────────────────────

data class ButtonConfig(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "BTN",
    val key: String = "k",
    val x: Float = 0.75f,
    val y: Float = 0.5f,
    val size: ElementSize = ElementSize.M
)

enum class RightJoystickMode { DIRECT, SKILL_AIM }

data class DpadMapping(
    val up: String = "w",
    val down: String = "s",
    val left: String = "a",
    val right: String = "d"
)
