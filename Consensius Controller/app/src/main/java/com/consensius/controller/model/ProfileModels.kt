package com.consensius.controller.model

import java.util.UUID

// ─── Element Types ────────────────────────────────────────────────────────────

enum class ElementType { BUTTON, JOYSTICK, DPAD, TOUCHPAD }
enum class JoystickType { MOVEMENT, SKILL_AIM }

// ─── Default sizes per type (dp) ─────────────────────────────────────────────

object ElementDefaults {
    const val BUTTON_WIDTH    = 80f
    const val BUTTON_HEIGHT   = 80f
    const val JOYSTICK_SIZE   = 150f  // always square/circular
    const val DPAD_WIDTH      = 160f
    const val DPAD_HEIGHT     = 160f
    const val TOUCHPAD_WIDTH  = 200f
    const val TOUCHPAD_HEIGHT = 80f
}

// ─── Element Config (per-type) ────────────────────────────────────────────────

data class ButtonElementConfig(
    val key: String = "k"
)

data class JoystickElementConfig(
    val type: JoystickType = JoystickType.MOVEMENT,
    val skillKey: String   = ""  // only when type == SKILL_AIM
)

data class DpadElementConfig(
    val upKey: String    = "w",
    val downKey: String  = "s",
    val leftKey: String  = "a",
    val rightKey: String = "d"
)

data class TouchpadElementConfig(
    val sensitivityMultiplier: Float = 1.0f,
    val leftClickKey: String         = "mouse1",
    val rightClickKey: String        = "mouse2"
)

// ─── Canvas Element ───────────────────────────────────────────────────────────

/**
 * A single controller element on the canvas.
 * Position (x, y) is stored as a fraction of canvas dimensions (0.0 – 1.0).
 * Width and height are individual per element, stored in dp.
 * There is NO shared size enum — each element owns its own size.
 */
data class CanvasElement(
    val id: String          = UUID.randomUUID().toString(),
    val type: ElementType   = ElementType.BUTTON,
    val label: String       = "BTN",
    val x: Float            = 0.5f,                           // position as % of screen width
    val y: Float            = 0.5f,                           // position as % of screen height
    val width: Float        = ElementDefaults.BUTTON_WIDTH,   // size in dp — individual per element
    val height: Float       = ElementDefaults.BUTTON_HEIGHT,  // size in dp — individual per element
    val buttonConfig: ButtonElementConfig     = ButtonElementConfig(),
    val joystickConfig: JoystickElementConfig = JoystickElementConfig(),
    val dpadConfig: DpadElementConfig         = DpadElementConfig(),
    val touchpadConfig: TouchpadElementConfig = TouchpadElementConfig()
)

// ─── Profile Page ─────────────────────────────────────────────────────────────

data class ProfilePage(
    val id: String                    = UUID.randomUUID().toString(),
    val name: String                  = "Page 1",
    val elements: List<CanvasElement> = emptyList()
)

// ─── Controller Profile ───────────────────────────────────────────────────────

data class ControllerProfile(
    val id: String                  = UUID.randomUUID().toString(),
    val name: String                = "New Profile",
    val pages: List<ProfilePage>    = listOf(ProfilePage())
) {
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

// ─── Legacy models (kept for compatibility) ───────────────────────────────────

enum class RightJoystickMode { DIRECT, SKILL_AIM }

data class DpadMapping(
    val up: String    = "w",
    val down: String  = "s",
    val left: String  = "a",
    val right: String = "d"
)
