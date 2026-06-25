package com.consensius.controller.ui.controller

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.consensius.controller.datastore.SettingsDataStore
import com.consensius.controller.model.CanvasElement
import com.consensius.controller.model.ControllerProfile
import com.consensius.controller.model.ElementType
import com.consensius.controller.model.JoystickType
import com.consensius.controller.model.toDp
import com.consensius.controller.network.WebSocketManager
import com.consensius.controller.ui.theme.ConsensiusColors
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

// ─── Per-element runtime state ─────────────────────────────────────────────────

/** Mutable runtime state for a single canvas element */
data class ElementRuntimeState(
    val isPressed:       Boolean = false,
    val joystickOffX:    Float   = 0f,
    val joystickOffY:    Float   = 0f,
    val activeDpadDir:   String? = null,   // "up"/"down"/"left"/"right"
    val lastTouchX:      Float   = 0f,
    val lastTouchY:      Float   = 0f,
    // ── Touchpad gesture fields ──
    val touchDownTime:   Long    = 0L,     // System.currentTimeMillis() when finger touched
    val touchDownX:      Float   = 0f,     // raw X at first touch
    val touchDownY:      Float   = 0f,     // raw Y at first touch
    val touchMoved:      Boolean = false,  // true if finger drifted > drag threshold
    val isLongPressed:   Boolean = false,  // true while long-press click-drag is active
    val pointer2Id:      Int     = -1,     // second pointer ID on the touchpad
    val pointer2X:       Float   = 0f,     // second pointer last X
    val pointer2Y:       Float   = 0f,     // second pointer last Y
    val twoFingerDown:   Boolean = false,  // true while 2 fingers are on this touchpad
    val lastTapTime:     Long    = 0L,     // time of last single-tap (for double-tap detection)
    // ── Two-finger scroll state ──
    val twoFingerScrollActive: Boolean = false  // true once two-finger drag starts scrolling
)

// ─── ControllerScreen ─────────────────────────────────────────────────────────

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ControllerScreen(
    webSocketManager: WebSocketManager,
    settingsDataStore: SettingsDataStore,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val gson    = remember { Gson() }
    val density = LocalDensity.current

    // ── Force landscape + immersive mode ──────────────────────────────────────
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val origOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Full immersive mode
        activity?.window?.decorView?.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
        onDispose {
            activity?.requestedOrientation = origOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    // ── Settings ──────────────────────────────────────────────────────────────
    var deadzone         by remember { mutableFloatStateOf(0.1f) }
    var sensitivity      by remember { mutableFloatStateOf(1.0f) }
    var mouseSensitivity by remember { mutableFloatStateOf(1.5f) }
    var fps              by remember { mutableIntStateOf(60) }

    LaunchedEffect(Unit) {
        deadzone         = settingsDataStore.deadzoneFlow.first()
        sensitivity      = settingsDataStore.sensitivityFlow.first()
        mouseSensitivity = settingsDataStore.mouseSensitivityFlow.first()
        fps              = settingsDataStore.sendFpsFlow.first()
    }

    // ── Active profile ────────────────────────────────────────────────────────
    val profiles          by settingsDataStore.profilesFlow.collectAsState(initial = emptyList())
    val selectedProfileId by settingsDataStore.selectedProfileIdFlow.collectAsState(initial = "")
    val activeProfile: ControllerProfile? = profiles.firstOrNull { it.id == selectedProfileId }
        ?: profiles.firstOrNull()

    // ── Page state ────────────────────────────────────────────────────────────
    var currentPage         by remember { mutableIntStateOf(0) }
    var showPageOverlay     by remember { mutableStateOf(false) }
    var pageHoldStartTime   by remember { mutableStateOf(0L) }

    // ── Element runtime states ────────────────────────────────────────────────
    val elementStates = remember { mutableStateMapOf<String, ElementRuntimeState>() }

    // Element bounding boxes in root coords (px) for hit testing
    val elementBounds = remember { mutableStateMapOf<String, android.graphics.RectF>() }

    // Touchpad actual rendered bounds (wider than the positioning box — 2.5× width)
    val touchpadActualBounds = remember { mutableStateMapOf<String, android.graphics.RectF>() }

    // ── Pointer → element mapping ─────────────────────────────────────────────
    val pointerToElement = remember { mutableStateMapOf<Int, String>() }

    // ── Dialogs ───────────────────────────────────────────────────────────────
    var showExitDialog by remember { mutableStateOf(false) }

    // ── Profile sync ──────────────────────────────────────────────────────────
    LaunchedEffect(activeProfile) {
        activeProfile?.let { profile ->
            val profileJson = gson.toJson(mapOf("type" to "profile", "data" to profile))
            webSocketManager.send(profileJson)
        }
    }

    // ── Send helpers ──────────────────────────────────────────────────────────
    fun sendButton(key: String, isDown: Boolean) {
        webSocketManager.send(gson.toJson(mapOf(
            "type"  to "button",
            "key"   to key,
            "state" to if (isDown) "down" else "up"
        )))
    }

    fun sendDpad(key: String, isDown: Boolean) {
        webSocketManager.send(gson.toJson(mapOf(
            "type"  to "dpad",
            "key"   to key,
            "state" to if (isDown) "down" else "up"
        )))
    }

    fun sendJoystick(x: Float, y: Float) {
        val mag = sqrt(x * x + y * y)
        val (nx, ny) = if (mag < deadzone) 0f to 0f else {
            val s = ((mag - deadzone) / (1f - deadzone)) * sensitivity
            (x / mag * s).coerceIn(-1f, 1f) to (y / mag * s).coerceIn(-1f, 1f)
        }
        webSocketManager.send(gson.toJson(mapOf(
            "type"  to "joystick",
            "stick" to "movement",
            "x"     to nx,
            "y"     to ny
        )))
    }

    fun sendSkillAim(key: String, angle: Float, magnitude: Float, state: String) {
        webSocketManager.send(gson.toJson(mapOf(
            "type"      to "skill_aim",
            "action"    to key,
            "angle"     to angle,
            "magnitude" to magnitude,
            "state"     to state
        )))
    }

    fun sendMouseMove(dx: Float, dy: Float) {
        // type = "mouse_move" so the server can distinguish it from mouse_click/scroll
        webSocketManager.send(gson.toJson(mapOf(
            "type" to "mouse_move",
            "dx"   to (dx * mouseSensitivity),
            "dy"   to (dy * mouseSensitivity)
        )))
    }

    fun sendMouseClick(button: String, isDown: Boolean) {
        webSocketManager.send(gson.toJson(mapOf(
            "type"   to "mouse_click",
            "button" to button,
            "state"  to if (isDown) "down" else "up"
        )))
    }

    fun sendMouseScroll(dy: Float) {
        webSocketManager.send(gson.toJson(mapOf(
            "type" to "mouse_scroll",
            "dy"   to dy
        )))
    }

    // ── FPS loop for continuous joystick send ──────────────────────────────────
    LaunchedEffect(fps) {
        val interval = (1000f / fps.coerceIn(1, 120)).toLong()
        while (true) {
            delay(interval)
            // BUG FIX: try-catch so any exception never kills this coroutine.
            try {
                val currentElements = activeProfile?.pages?.getOrNull(currentPage)?.elements
                    ?: continue
                currentElements
                    .filter { it.type == ElementType.JOYSTICK && it.joystickConfig.type == JoystickType.MOVEMENT }
                    .forEach { el ->
                        val state = elementStates[el.id] ?: return@forEach
                        if (!state.isPressed) return@forEach
                        val bounds = elementBounds[el.id]
                        val maxR = if (bounds != null && bounds.width() > 0f) {
                            bounds.width() * 0.4f
                        } else {
                            with(density) { el.size.toDp().dp.toPx() * 0.4f }
                        }
                        if (maxR > 0f) {
                            val rawNx = state.joystickOffX / maxR
                            val rawNy = state.joystickOffY / maxR
                            // Guard against NaN/Infinity before sending
                            if (rawNx.isFinite() && rawNy.isFinite()) {
                                sendJoystick(
                                    rawNx.coerceIn(-1f, 1f),
                                    -(rawNy.coerceIn(-1f, 1f))
                                )
                            }
                        }
                    }
            } catch (e: Exception) {
                android.util.Log.e("ControllerScreen", "FPS loop error: ${e.message}")
            }
        }
    }

    // ── Pointer event dispatcher ───────────────────────────────────────────────
    // BUG 3 FIX: Touchpad hit testing uses touchpadActualBounds (which capture the full
    // rendered width = sizeDp * 2.5f) instead of the narrower positioning Box bounds.
    fun findElementAt(rawX: Float, rawY: Float, elements: List<CanvasElement>): CanvasElement? {
        for (el in elements.reversed()) { // top elements first
            val rect = if (el.type == ElementType.TOUCHPAD) {
                touchpadActualBounds[el.id] ?: elementBounds[el.id]
            } else {
                elementBounds[el.id]
            } ?: continue
            if (rect.contains(rawX, rawY)) return el
        }
        return null
    }

    // Touchpad drag threshold in pixels
    val TOUCHPAD_DRAG_THRESHOLD_PX = 10f
    // Long-press threshold in milliseconds
    val LONG_PRESS_MS = 500L
    // Single-tap max duration in milliseconds
    val TAP_MAX_MS = 200L
    // Double-tap window in milliseconds
    val DOUBLE_TAP_MS = 300L

    fun onPointerDown(pointerId: Int, rawX: Float, rawY: Float) {
        val page = activeProfile?.pages?.getOrNull(currentPage) ?: return
        val el = findElementAt(rawX, rawY, page.elements) ?: return
        pointerToElement[pointerId] = el.id

        when (el.type) {
            ElementType.BUTTON -> {
                elementStates[el.id] = ElementRuntimeState(isPressed = true)
                sendButton(el.buttonConfig.key, true)
            }
            ElementType.JOYSTICK -> {
                // BUG 5 FIX: Do NOT press skill key on pointer down.
                // For SKILL_AIM: only send "aiming" while dragging, "cast" on release.
                elementStates[el.id] = ElementRuntimeState(
                    isPressed    = true,
                    joystickOffX = 0f,
                    joystickOffY = 0f,
                    lastTouchX   = rawX,
                    lastTouchY   = rawY
                )
            }
            ElementType.DPAD -> {
                val rect = elementBounds[el.id] ?: return
                val cx = rect.centerX(); val cy = rect.centerY()
                val dx = rawX - cx; val dy = rawY - cy
                val dir = if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                    if (dx > 0) "right" else "left"
                } else {
                    if (dy > 0) "down" else "up"
                }
                val key = when (dir) {
                    "up"    -> el.dpadConfig.upKey
                    "down"  -> el.dpadConfig.downKey
                    "left"  -> el.dpadConfig.leftKey
                    else    -> el.dpadConfig.rightKey
                }
                elementStates[el.id] = ElementRuntimeState(isPressed = true, activeDpadDir = dir)
                sendDpad(key, true)
            }
            ElementType.TOUCHPAD -> {
                val existing = elementStates[el.id]
                val now = System.currentTimeMillis()
                if (existing != null && existing.twoFingerDown) {
                    // Third+ finger ignored
                    return
                }
                if (existing != null && existing.isPressed && existing.pointer2Id == -1) {
                    // Second finger arriving on the touchpad → two-finger gesture
                    elementStates[el.id] = existing.copy(
                        pointer2Id             = pointerId,
                        pointer2X              = rawX,
                        pointer2Y              = rawY,
                        twoFingerDown          = true,
                        twoFingerScrollActive  = false   // reset scroll flag
                    )
                } else {
                    // First finger down
                    elementStates[el.id] = ElementRuntimeState(
                        isPressed             = true,
                        lastTouchX            = rawX,
                        lastTouchY            = rawY,
                        touchDownTime         = now,
                        touchDownX            = rawX,
                        touchDownY            = rawY,
                        touchMoved            = false,
                        isLongPressed         = false,
                        pointer2Id            = -1,
                        twoFingerDown         = false,
                        twoFingerScrollActive = false
                    )
                }
            }
        }
    }

    fun onPointerMove(pointerId: Int, rawX: Float, rawY: Float) {
        val elId = pointerToElement[pointerId] ?: return
        val page = activeProfile?.pages?.getOrNull(currentPage) ?: return
        val el = page.elements.firstOrNull { it.id == elId } ?: return
        val state = elementStates[elId] ?: return

        when (el.type) {
            ElementType.JOYSTICK -> {
                // FIX: Use elementBounds if available, otherwise fall back to the
                // initial touch position (lastTouchX/lastTouchY) as the joystick center.
                // This prevents onPointerMove from silently returning when bounds are
                // not yet measured, which caused joystickOffX/Y to stay 0 forever.
                val rect = elementBounds[elId]
                val cx: Float
                val cy: Float
                val maxR: Float
                if (rect != null && rect.width() > 0f) {
                    cx   = rect.centerX()
                    cy   = rect.centerY()
                    maxR = rect.width() * 0.4f
                } else {
                    // Fallback: treat initial touch as joystick center (floating mode)
                    cx   = state.lastTouchX
                    cy   = state.lastTouchY
                    maxR = with(density) { el.size.toDp().dp.toPx() * 0.4f }.coerceAtLeast(50f)
                }

                val rawDx    = rawX - cx
                val rawDy    = rawY - cy
                val dist     = sqrt(rawDx * rawDx + rawDy * rawDy)
                val clampedX = if (dist <= maxR) rawDx else rawDx / dist * maxR
                val clampedY = if (dist <= maxR) rawDy else rawDy / dist * maxR
                elementStates[elId] = state.copy(joystickOffX = clampedX, joystickOffY = clampedY)

                if (el.joystickConfig.type == JoystickType.SKILL_AIM && maxR > 0f) {
                    // Skill aim: send aiming direction on every move (user-paced, safe rate).
                    val nx       = (clampedX / maxR).coerceIn(-1f, 1f)
                    // Note: screen Y is inverted (down = positive), so negate for game coords
                    val ny       = -(clampedY / maxR).coerceIn(-1f, 1f)
                    // atan2(dy, dx) — use raw screen offsets so angle matches screen direction
                    // clampedX right = positive angle 0°, up (negative screenY) = 90°
                    val angleDeg = Math.toDegrees(
                        atan2(clampedY.toDouble(), clampedX.toDouble())
                    ).toFloat()  // use screen-space atan2 (clampedY not negated)
                    val magnitude = sqrt(nx * nx + ny * ny).coerceIn(0f, 1f)
                    sendSkillAim(el.joystickConfig.skillKey, angleDeg, magnitude, "aiming")
                }
                // Movement joystick: state is updated above; FPS loop reads it and sends
                // at the configured rate. Do NOT call sendJoystick() here — calling send()
                // on every touch event (potentially 200+/s) overflows OkHttp's WebSocket
                // send buffer → IOException → "no close frame" disconnect.
            }
            ElementType.TOUCHPAD -> {
                // Distinguish two-finger scroll vs single-finger move
                if (state.twoFingerDown) {
                    // ── Two-finger gesture: 2-finger drag = scroll ────────────
                    if (pointerId == state.pointer2Id) {
                        val dy2 = rawY - state.pointer2Y
                        val dx2 = rawX - state.pointer2X
                        // Scroll on any vertical movement > 1px
                        if (kotlin.math.abs(dy2) > 1f) {
                            val normalizedDy = -(dy2 / 15f) // positive = scroll up
                            sendMouseScroll(normalizedDy)
                            elementStates[elId] = state.copy(
                                pointer2X = rawX,
                                pointer2Y = rawY,
                                twoFingerScrollActive = true
                            )
                        } else {
                            elementStates[elId] = state.copy(pointer2X = rawX, pointer2Y = rawY)
                        }
                    } else {
                        // First pointer moved during two-finger: update position
                        elementStates[elId] = state.copy(lastTouchX = rawX, lastTouchY = rawY)
                    }
                } else {
                    // ── Single-finger: mouse move ─────────────────────────────
                    val totalDx = rawX - state.touchDownX
                    val totalDy = rawY - state.touchDownY
                    val totalDist = sqrt(totalDx * totalDx + totalDy * totalDy)
                    val moved = state.touchMoved || totalDist > TOUCHPAD_DRAG_THRESHOLD_PX

                    if (moved) {
                        // Check long-press: if held > LONG_PRESS_MS → begin click-drag
                        val now = System.currentTimeMillis()
                        val heldMs = now - state.touchDownTime
                        val longPressed = state.isLongPressed ||
                            (!state.touchMoved && heldMs >= LONG_PRESS_MS)

                        if (longPressed && !state.isLongPressed) {
                            // Finger just crossed long-press → begin held left click
                            sendMouseClick("left", true)
                        }

                        val dx = rawX - state.lastTouchX
                        val dy = rawY - state.lastTouchY
                        sendMouseMove(dx, dy)
                        elementStates[elId] = state.copy(
                            lastTouchX    = rawX,
                            lastTouchY    = rawY,
                            touchMoved    = true,
                            isLongPressed = longPressed
                        )
                    } else {
                        // Not yet moved — update position but don't move mouse
                        elementStates[elId] = state.copy(lastTouchX = rawX, lastTouchY = rawY)
                    }
                }
            }
            ElementType.DPAD -> {
                val rect = elementBounds[elId] ?: return
                val cx = rect.centerX(); val cy = rect.centerY()
                val dx = rawX - cx; val dy = rawY - cy
                val newDir = if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                    if (dx > 0) "right" else "left"
                } else {
                    if (dy > 0) "down" else "up"
                }
                if (newDir != state.activeDpadDir) {
                    state.activeDpadDir?.let { oldDir ->
                        val oldKey = when (oldDir) {
                            "up"   -> el.dpadConfig.upKey
                            "down" -> el.dpadConfig.downKey
                            "left" -> el.dpadConfig.leftKey
                            else   -> el.dpadConfig.rightKey
                        }
                        sendDpad(oldKey, false)
                    }
                    val newKey = when (newDir) {
                        "up"   -> el.dpadConfig.upKey
                        "down" -> el.dpadConfig.downKey
                        "left" -> el.dpadConfig.leftKey
                        else   -> el.dpadConfig.rightKey
                    }
                    sendDpad(newKey, true)
                    elementStates[elId] = state.copy(activeDpadDir = newDir)
                }
            }
            ElementType.BUTTON -> { /* handled on down/up only */ }
        }
    }

    fun onPointerUp(pointerId: Int, rawX: Float, rawY: Float) {
        val elId = pointerToElement.remove(pointerId) ?: return
        val page = activeProfile?.pages?.getOrNull(currentPage) ?: return
        val el = page.elements.firstOrNull { it.id == elId } ?: return
        val state = elementStates[elId] ?: return

        when (el.type) {
            ElementType.BUTTON -> {
                elementStates[elId] = state.copy(isPressed = false)
                sendButton(el.buttonConfig.key, false)
            }
            ElementType.JOYSTICK -> {
                if (el.joystickConfig.type == JoystickType.SKILL_AIM) {
                    // BUG 2 FIX: Only send cast if the joystick was dragged significantly.
                    // A tiny tap (no real drag) should NOT fire the skill.
                    val maxR = (elementBounds[elId]?.width() ?: 1f) * 0.4f
                    val nx   = if (maxR > 0f) (state.joystickOffX / maxR).coerceIn(-1f, 1f) else 0f
                    val ny   = if (maxR > 0f) -(state.joystickOffY / maxR).coerceIn(-1f, 1f) else 0f
                    val mag  = sqrt(nx * nx + ny * ny).coerceIn(0f, 1f)
                    if (mag >= 0.1f) {
                        // Meaningful drag → send cast with aim direction
                        val angleDeg = Math.toDegrees(
                            atan2(state.joystickOffY.toDouble(), state.joystickOffX.toDouble())
                        ).toFloat()
                        sendSkillAim(el.joystickConfig.skillKey, angleDeg, mag, "cast")
                    }
                    // If mag < 0.1f (just a tap with no real direction), do nothing
                } else {
                    // Send zero once to stop movement immediately
                    webSocketManager.send(gson.toJson(mapOf(
                        "type"  to "joystick",
                        "stick" to "movement",
                        "x"     to 0f,
                        "y"     to 0f
                    )))
                }
                elementStates[elId] = state.copy(isPressed = false, joystickOffX = 0f, joystickOffY = 0f)
            }
            ElementType.DPAD -> {
                state.activeDpadDir?.let { dir ->
                    val key = when (dir) {
                        "up"   -> el.dpadConfig.upKey
                        "down" -> el.dpadConfig.downKey
                        "left" -> el.dpadConfig.leftKey
                        else   -> el.dpadConfig.rightKey
                    }
                    sendDpad(key, false)
                }
                elementStates[elId] = state.copy(isPressed = false, activeDpadDir = null)
            }
            ElementType.TOUCHPAD -> {
                val now = System.currentTimeMillis()

                if (pointerId == state.pointer2Id) {
                    // ── Second finger lifted ───────────────────────────────────
                    // 2-finger tap (no scroll occurred) → right click
                    if (!state.twoFingerScrollActive) {
                        sendMouseClick("right", true)
                        sendMouseClick("right", false)
                    }
                    // Return to single-finger tracking
                    elementStates[elId] = state.copy(
                        pointer2Id            = -1,
                        twoFingerDown         = false,
                        twoFingerScrollActive = false
                    )
                } else if (state.twoFingerDown) {
                    // ── Primary finger lifted while two-finger gesture active ──
                    // Nothing special; just mark as no longer two-finger
                    elementStates[elId] = state.copy(
                        isPressed             = false,
                        twoFingerDown         = false,
                        twoFingerScrollActive = false,
                        pointer2Id            = -1
                    )
                } else {
                    // ── Primary finger lifted (single-finger gesture) ──────────
                    if (state.isLongPressed) {
                        // Release the held left click (click-drag end)
                        sendMouseClick("left", false)
                    } else if (!state.touchMoved) {
                        val heldMs = now - state.touchDownTime
                        if (heldMs < TAP_MAX_MS) {
                            // Quick tap → check for double-tap
                            val timeSinceLastTap = now - state.lastTapTime
                            if (state.lastTapTime > 0L && timeSinceLastTap < DOUBLE_TAP_MS) {
                                // Double-tap → two left-click down+up sequences
                                sendMouseClick("left", true)
                                sendMouseClick("left", false)
                                sendMouseClick("left", true)
                                sendMouseClick("left", false)
                                elementStates[elId] = state.copy(isPressed = false, lastTapTime = 0L)
                                return
                            } else {
                                // Single tap → one left click
                                sendMouseClick("left", true)
                                sendMouseClick("left", false)
                                elementStates[elId] = state.copy(isPressed = false, lastTapTime = now)
                                return
                            }
                        }
                    }
                    elementStates[elId] = state.copy(
                        isPressed             = false,
                        touchMoved            = false,
                        isLongPressed         = false,
                        pointer2Id            = -1,
                        twoFingerDown         = false,
                        twoFingerScrollActive = false
                    )
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ConsensiusColors.Background)
    ) {
        val screenW = maxWidth
        val screenH = maxHeight
        val currentElements = activeProfile?.pages?.getOrNull(currentPage)?.elements ?: emptyList()

        // ── Single root multi-touch catcher ───────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInteropFilter { event ->
                    // ← BUG FIX: wrap in try/catch so any Kotlin exception inside
                    // touch handling never crashes the composition or the Activity.
                    try {
                        val action = event.actionMasked
                        val pointerIndex = event.actionIndex
                        val pointerId = event.getPointerId(pointerIndex)

                        when (action) {
                            MotionEvent.ACTION_DOWN,
                            MotionEvent.ACTION_POINTER_DOWN -> {
                                val rawX = event.getX(pointerIndex)
                                val rawY = event.getY(pointerIndex)
                                onPointerDown(pointerId, rawX, rawY)
                            }
                            MotionEvent.ACTION_MOVE -> {
                                for (i in 0 until event.pointerCount) {
                                    val pid = event.getPointerId(i)
                                    onPointerMove(pid, event.getX(i), event.getY(i))
                                }
                            }
                            MotionEvent.ACTION_UP,
                            MotionEvent.ACTION_POINTER_UP -> {
                                val rawX = event.getX(pointerIndex)
                                val rawY = event.getY(pointerIndex)
                                onPointerUp(pointerId, rawX, rawY)
                            }
                            MotionEvent.ACTION_CANCEL -> {
                                // Release all
                                pointerToElement.keys.toList().forEach { pid ->
                                    onPointerUp(pid, 0f, 0f)
                                }
                                pointerToElement.clear()
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ControllerScreen", "Touch event error: ${e.message}")
                    }
                    true
                }
        ) {
            // ── Render all elements on current page ───────────────────────────
            currentElements.forEach { el ->
                val sizeDp = el.size.toDp().dp
                val posX   = screenW * el.x - sizeDp / 2
                val posY   = screenH * el.y - sizeDp / 2
                val state  = elementStates[el.id] ?: ElementRuntimeState()

                Box(
                    modifier = Modifier
                        .offset(x = posX, y = posY)
                        .onGloballyPositioned { coords ->
                            val bounds = coords.boundsInRoot()
                            elementBounds[el.id] = android.graphics.RectF(
                                bounds.left, bounds.top, bounds.right, bounds.bottom
                            )
                        }
                ) {
                    when (el.type) {
                        ElementType.BUTTON ->
                            ControllerButton(element = el, sizeDp = sizeDp, isPressed = state.isPressed)
                        ElementType.JOYSTICK ->
                            ControllerJoystick(
                                element = el, sizeDp = sizeDp,
                                offsetX = state.joystickOffX,
                                offsetY = state.joystickOffY
                            )
                        ElementType.DPAD ->
                            ControllerDpad(element = el, sizeDp = sizeDp, activeDir = state.activeDpadDir)
                        ElementType.TOUCHPAD ->
                            // BUG 3 FIX: Capture actual rendered bounds of the touchpad
                            // (which is wider than the positioning box: width = sizeDp * 2.5)
                            // so that hit-testing covers the full touchpad area.
                            ControllerTouchpad(
                                element   = el,
                                sizeDp    = sizeDp,
                                isActive  = state.isPressed,
                                onBoundsChanged = { rect ->
                                    touchpadActualBounds[el.id] = rect
                                }
                            )
                    }
                }
            }

            // ── Profile label (top-right) ─────────────────────────────────────
            Text(
                text = activeProfile?.name ?: "",
                color = ConsensiusColors.TextSecondary.copy(alpha = 0.35f),
                fontSize = 9.sp,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 10.dp)
            )

            // ── Page indicator (top center) ───────────────────────────────────
            val pageCount = activeProfile?.pages?.size ?: 1
            if (pageCount > 1) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(pageCount) { idx ->
                        val isActive = idx == currentPage
                        Box(
                            modifier = Modifier
                                .size(if (isActive) 8.dp else 5.dp)
                                .background(
                                    if (isActive) ConsensiusColors.Accent else ConsensiusColors.TextSecondary.copy(alpha = 0.4f),
                                    CircleShape
                                )
                        )
                    }
                }
            }

            // ── Exit button (top-left) ────────────────────────────────────────
            Button(
                onClick = { showExitDialog = true },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 6.dp, start = 8.dp)
                    .height(28.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ConsensiusColors.Error.copy(alpha = 0.65f)
                ),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                Text("✕  EXIT", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }

            // ── Page switcher pill (top center, only if >1 page) ──────────────
            if (pageCount > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 30.dp)
                        .background(
                            ConsensiusColors.Card.copy(alpha = 0.7f),
                            RoundedCornerShape(12.dp)
                        )
                        .border(1.dp, ConsensiusColors.CardBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        "Page ${currentPage + 1} / $pageCount  (hold to switch)",
                        color = ConsensiusColors.TextSecondary,
                        fontSize = 9.sp
                    )
                }
            }

            // ── Page overlay ──────────────────────────────────────────────────
            if (showPageOverlay) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        activeProfile?.pages?.forEachIndexed { idx, page ->
                            val isActive = idx == currentPage
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .background(
                                        if (isActive) ConsensiusColors.Accent.copy(alpha = 0.15f)
                                        else ConsensiusColors.Card,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .border(
                                        1.5.dp,
                                        if (isActive) ConsensiusColors.Accent else ConsensiusColors.CardBorder,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(page.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(Modifier.height(4.dp))
                                Text("${page.elements.size} elements", color = ConsensiusColors.TextSecondary, fontSize = 10.sp)
                            }
                        }
                    }
                    // close on tap
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInteropFilter { event ->
                                if (event.actionMasked == MotionEvent.ACTION_UP) {
                                    // Find page at tap
                                    showPageOverlay = false
                                }
                                false
                            }
                    )
                }
            }
        }
    }

    // ── Exit dialog ───────────────────────────────────────────────────────────
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            containerColor = ConsensiusColors.Card,
            title = { Text("Exit Controller", color = ConsensiusColors.TextPrimary, fontWeight = FontWeight.Bold) },
            text  = { Text("Disconnect and return to Home?", color = ConsensiusColors.TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        webSocketManager.disconnect()
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ConsensiusColors.Error)
                ) { Text("EXIT") }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("STAY", color = ConsensiusColors.TextSecondary)
                }
            }
        )
    }
}

// ─── Element Renderers ────────────────────────────────────────────────────────

@Composable
private fun ControllerButton(element: CanvasElement, sizeDp: Dp, isPressed: Boolean) {
    val glowElevation by animateFloatAsState(
        targetValue  = if (isPressed) 14f else 2f,
        animationSpec = tween(80),
        label = "btnGlow"
    )
    Box(
        modifier = Modifier
            .size(sizeDp)
            .shadow(
                glowElevation.dp, CircleShape,
                ambientColor = ConsensiusColors.Accent,
                spotColor    = ConsensiusColors.Accent
            )
            .background(
                if (isPressed)
                    Brush.radialGradient(listOf(ConsensiusColors.Accent.copy(alpha = 0.45f), ConsensiusColors.Card))
                else
                    Brush.radialGradient(listOf(ConsensiusColors.Card, ConsensiusColors.Surface)),
                CircleShape
            )
            .border(
                width = if (isPressed) 2.dp else 1.5.dp,
                color = if (isPressed) ConsensiusColors.Accent else ConsensiusColors.Accent.copy(alpha = 0.45f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text  = element.label,
            color = if (isPressed) Color.White else ConsensiusColors.TextPrimary,
            fontWeight = FontWeight.ExtraBold,
            fontSize   = (sizeDp.value * 0.22f).sp,
            textAlign  = TextAlign.Center
        )
    }
}

@Composable
private fun ControllerJoystick(
    element:  CanvasElement,
    sizeDp:   Dp,
    offsetX:  Float,
    offsetY:  Float
) {
    val isSkillAim = element.joystickConfig.type == JoystickType.SKILL_AIM
    val ringColor  = if (isSkillAim) ConsensiusColors.AccentSecondary else ConsensiusColors.Accent
    val thumbColor = if (isSkillAim)
        Brush.radialGradient(listOf(ConsensiusColors.AccentSecondary, Color(0xFF0062CC)))
    else
        Brush.radialGradient(listOf(ConsensiusColors.AccentSecondary, ConsensiusColors.Accent))

    val thumbSize = sizeDp * 0.36f

    val infiniteTransition = rememberInfiniteTransition(label = "js_ring")
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.65f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ringAlpha"
    )

    Box(
        modifier = Modifier.size(sizeDp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(ringColor.copy(alpha = ringAlpha), r, style = Stroke(2f))
            drawCircle(
                Brush.radialGradient(
                    listOf(ringColor.copy(alpha = 0.07f), Color.Transparent)
                ), r
            )
        }

        // Label
        Text(
            text = if (isSkillAim) "AIM" else "MOV",
            color = ringColor.copy(alpha = 0.2f),
            fontSize = (sizeDp.value * 0.14f).sp,
            fontWeight = FontWeight.Bold
        )

        // Thumb
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(thumbSize)
                .shadow(8.dp, CircleShape)
                .background(thumbColor, CircleShape)
        )
    }
}

@Composable
private fun ControllerDpad(element: CanvasElement, sizeDp: Dp, activeDir: String?) {
    val arm = sizeDp * 0.3f

    Box(modifier = Modifier.size(sizeDp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(sizeDp)) {
            val s   = size.minDimension
            val aw  = s * 0.30f
            val cx  = s / 2f
            val cy  = s / 2f

            fun dirActive(dir: String) = activeDir == dir

            // Horizontal bar
            drawRect(
                color   = if (dirActive("left") || dirActive("right")) ConsensiusColors.Accent.copy(0.5f) else ConsensiusColors.Surface,
                topLeft = Offset(cx - s * 0.5f, cy - aw / 2),
                size    = androidx.compose.ui.geometry.Size(s, aw)
            )
            // Vertical bar
            drawRect(
                color   = if (dirActive("up") || dirActive("down")) ConsensiusColors.Accent.copy(0.5f) else ConsensiusColors.Surface,
                topLeft = Offset(cx - aw / 2, cy - s * 0.5f),
                size    = androidx.compose.ui.geometry.Size(aw, s)
            )
            // Active direction highlight
            if (dirActive("left")) drawRect(ConsensiusColors.Accent.copy(0.4f), Offset(cx - s * 0.5f, cy - aw / 2), androidx.compose.ui.geometry.Size(s * 0.38f, aw))
            if (dirActive("right")) drawRect(ConsensiusColors.Accent.copy(0.4f), Offset(cx + s * 0.12f, cy - aw / 2), androidx.compose.ui.geometry.Size(s * 0.38f, aw))
            if (dirActive("up")) drawRect(ConsensiusColors.Accent.copy(0.4f), Offset(cx - aw / 2, cy - s * 0.5f), androidx.compose.ui.geometry.Size(aw, s * 0.38f))
            if (dirActive("down")) drawRect(ConsensiusColors.Accent.copy(0.4f), Offset(cx - aw / 2, cy + s * 0.12f), androidx.compose.ui.geometry.Size(aw, s * 0.38f))
            // Borders
            drawRect(ConsensiusColors.Accent.copy(0.35f), Offset(cx - s * 0.5f, cy - aw / 2), androidx.compose.ui.geometry.Size(s, aw), style = Stroke(1.5f))
            drawRect(ConsensiusColors.Accent.copy(0.35f), Offset(cx - aw / 2, cy - s * 0.5f), androidx.compose.ui.geometry.Size(aw, s), style = Stroke(1.5f))
        }

        // Arrow labels
        val arrowColor = ConsensiusColors.TextSecondary.copy(alpha = 0.5f)
        Text("▲", color = if (activeDir == "up") Color.White else arrowColor,
            fontSize = (sizeDp.value * 0.15f).sp,
            modifier = Modifier.offset(y = -sizeDp * 0.3f))
        Text("▼", color = if (activeDir == "down") Color.White else arrowColor,
            fontSize = (sizeDp.value * 0.15f).sp,
            modifier = Modifier.offset(y = sizeDp * 0.3f))
        Text("◀", color = if (activeDir == "left") Color.White else arrowColor,
            fontSize = (sizeDp.value * 0.15f).sp,
            modifier = Modifier.offset(x = -sizeDp * 0.3f))
        Text("▶", color = if (activeDir == "right") Color.White else arrowColor,
            fontSize = (sizeDp.value * 0.15f).sp,
            modifier = Modifier.offset(x = sizeDp * 0.3f))
    }
}

@Composable
private fun ControllerTouchpad(
    element: CanvasElement,
    sizeDp: Dp,
    isActive: Boolean,
    onBoundsChanged: (android.graphics.RectF) -> Unit = {}
) {
    val w = sizeDp * 2.5f
    Box(
        modifier = Modifier
            .width(w)
            .height(sizeDp)
            .background(
                Brush.linearGradient(listOf(
                    ConsensiusColors.Surface.copy(alpha = 0.55f),
                    ConsensiusColors.Card.copy(alpha = 0.55f)
                )),
                RoundedCornerShape(14.dp)
            )
            .border(
                1.dp,
                if (isActive) ConsensiusColors.AccentSecondary.copy(0.7f) else ConsensiusColors.AccentSecondary.copy(0.3f),
                RoundedCornerShape(14.dp)
            )
            // BUG 3 FIX: Report the actual rendered bounds of this wide touchpad box
            // back to the parent so hit-testing covers the full area.
            .onGloballyPositioned { coords ->
                val b = coords.boundsInRoot()
                onBoundsChanged(
                    android.graphics.RectF(b.left, b.top, b.right, b.bottom)
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Dot-grid texture
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sp = 14.dp.toPx()
            var gx = sp
            while (gx < size.width) {
                var gy = sp
                while (gy < size.height) {
                    drawCircle(Color(0xFF00C2FF).copy(alpha = 0.12f), 1.2f, Offset(gx, gy))
                    gy += sp
                }
                gx += sp
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("TOUCHPAD", color = ConsensiusColors.TextSecondary.copy(0.5f), fontSize = 9.sp, letterSpacing = 1.sp)
            Text(
                "1-tap=click  2-tap=right  hold=drag",
                color = ConsensiusColors.AccentSecondary.copy(0.35f),
                fontSize = 7.sp,
                letterSpacing = 0.3.sp
            )
        }
    }
}

// ─── Legacy Joystick (kept for potential reuse) ───────────────────────────────
@Composable
fun Joystick(
    size: Dp,
    label: String = "",
    onValueChange: (Float, Float) -> Unit,
    onRelease: ((Float, Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Stub kept for API compatibility; replaced by ControllerJoystick above
    Box(modifier = modifier.size(size))
}
