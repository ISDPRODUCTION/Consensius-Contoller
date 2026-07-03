package com.consensius.controller.ui.controller

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.MotionEvent
import android.view.WindowManager
import android.view.WindowInsetsController
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
import androidx.compose.ui.zIndex
import com.consensius.controller.datastore.SettingsDataStore
import com.consensius.controller.model.CanvasElement
import com.consensius.controller.model.ControllerProfile
import com.consensius.controller.model.ElementType
import com.consensius.controller.model.JoystickType
import com.consensius.controller.network.WebSocketManager
import com.consensius.controller.ui.theme.ConsensiusColors
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds

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
    val lastTapTime:     Long    = 0L,     // time of last tap-UP (for double-tap detection)
    val isDoubleTapDrag: Boolean = false,  // true while double-tap-drag (scroll/drag) is active
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
        // Full immersive mode (API 30+ uses WindowInsetsController)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity?.window?.insetsController?.let { controller ->
                controller.hide(
                    android.view.WindowInsets.Type.statusBars()
                            or android.view.WindowInsets.Type.navigationBars()
                )
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            activity?.window?.decorView?.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }
        onDispose {
            activity?.requestedOrientation = origOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity?.window?.insetsController?.show(
                    android.view.WindowInsets.Type.statusBars()
                            or android.view.WindowInsets.Type.navigationBars()
                )
            } else {
                @Suppress("DEPRECATION")
                activity?.window?.decorView?.systemUiVisibility =
                    android.view.View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }

    // ── Settings ──────────────────────────────────────────────────────────────
    var deadzoneThreshold by remember { mutableFloatStateOf(0.1f) }
    var sensitivity       by remember { mutableFloatStateOf(1.0f) }
    var mouseSensitivity  by remember { mutableFloatStateOf(1.5f) }
    var fps               by remember { mutableIntStateOf(60) }

    LaunchedEffect(Unit) {
        deadzoneThreshold = settingsDataStore.deadzoneFlow.first()
        sensitivity       = settingsDataStore.sensitivityFlow.first()
        mouseSensitivity  = settingsDataStore.mouseSensitivityFlow.first()
        fps               = settingsDataStore.sendFpsFlow.first()
    }

    // ── Active profile ────────────────────────────────────────────────────────
    val profiles          by settingsDataStore.profilesFlow.collectAsState(initial = emptyList())
    val selectedProfileId by settingsDataStore.selectedProfileIdFlow.collectAsState(initial = "")
    val activeProfile: ControllerProfile? = profiles.firstOrNull { it.id == selectedProfileId }
        ?: profiles.firstOrNull()

    // ── Page state ────────────────────────────────────────────────────────────
    var currentPage     by remember { mutableIntStateOf(0) }
    var showPageOverlay by remember { mutableStateOf(false) }

    // ── Element runtime states ────────────────────────────────────────────────
    val elementStates = remember { mutableStateMapOf<String, ElementRuntimeState>() }

    // Element bounding boxes in root coords (px) for hit testing
    val elementBounds = remember { mutableStateMapOf<String, android.graphics.RectF>() }

    // Touchpad actual rendered bounds — same as elementBounds now that 2.5× factor is gone
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
        val (nx, ny) = if (mag < deadzoneThreshold) 0f to 0f else {
            val s = ((mag - deadzoneThreshold) / (1f - deadzoneThreshold)) * sensitivity
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
        val intervalMs = (1000f / fps.coerceIn(1, 120)).toLong()
        while (true) {
            delay(intervalMs.milliseconds)
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
                            with(density) { el.width.dp.toPx() * 0.4f }
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
    fun findElementAt(rawX: Float, rawY: Float, elements: List<CanvasElement>): CanvasElement? {
        for (el in elements.reversed()) { // top elements first
            val rect = elementBounds[el.id] ?: continue
            if (rect.contains(rawX, rawY)) return el
        }
        return null
    }

    // Touchpad drag threshold in pixels
    val touchpadDragThresholdPx = 10f
    // Long-press threshold in milliseconds
    val longPressMs = 500L
    // Single-tap max hold duration — generous so a normal tap always qualifies
    val tapMaxMs = 400L
    // Double-tap window — time from tap-1-UP to tap-2-UP must be within this
    val doubleTapMs = 420L

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
                    // First finger down — check if this is the second tap of a double-tap.
                    val timeSinceLastTap = now - (existing?.lastTapTime ?: 0L)
                    val isSecondTap = (existing?.lastTapTime ?: 0L) > 0L &&
                            timeSinceLastTap < doubleTapMs

                    if (isSecondTap) {
                        // ── DOUBLE-TAP DRAG START ──────────────────────────────
                        // Finger came down again within the double-tap window.
                        // Hold left mouse button down — user can now drag to scroll.
                        sendMouseClick("left", true)
                        elementStates[el.id] = ElementRuntimeState(
                            isPressed        = true,
                            lastTouchX       = rawX,
                            lastTouchY       = rawY,
                            touchDownTime    = now,
                            touchDownX       = rawX,
                            touchDownY       = rawY,
                            touchMoved       = false,
                            isDoubleTapDrag  = true,
                            lastTapTime      = 0L   // reset so next gesture starts fresh
                        )
                    } else {
                        // Normal first tap — start tracking
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
                            twoFingerScrollActive = false,
                            lastTapTime           = existing?.lastTapTime ?: 0L
                        )
                    }
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
                    maxR = with(density) { el.width.dp.toPx() * 0.4f }.coerceAtLeast(50f)
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
                    // Note: screen Y is inverted (down = positive), so negate for game coordinates
                    val ny       = -(clampedY / maxR).coerceIn(-1f, 1f)
                    // arcTan2(dy, dx) — use raw screen offsets so angle matches screen direction
                    // clampedX right = positive angle 0 deg, up (negative screen Y) = 90 deg
                    val angleDeg = Math.toDegrees(
                        atan2(clampedY.toDouble(), clampedX.toDouble())
                    ).toFloat()  // screen-space angle, clampedY not negated
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
                        // Scroll on any vertical movement > 1px (horizontal delta unused for scroll)
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
                    // ── Single-finger: mouse move / double-tap drag ─────────────
                    if (state.isDoubleTapDrag) {
                        // Double-tap drag active: every movement = mouse move
                        // (left button is already held down from onPointerDown)
                        val dx = rawX - state.lastTouchX
                        val dy = rawY - state.lastTouchY
                        if (dx != 0f || dy != 0f) {
                            sendMouseMove(dx, dy)
                        }
                        elementStates[elId] = state.copy(
                            lastTouchX = rawX,
                            lastTouchY = rawY,
                            touchMoved = true
                        )
                        return
                    }
                    val totalDx = rawX - state.touchDownX
                    val totalDy = rawY - state.touchDownY
                    val totalDist = sqrt(totalDx * totalDx + totalDy * totalDy)
                    val moved = state.touchMoved || totalDist > touchpadDragThresholdPx

                    if (moved) {
                        // Check long-press: if held > LONG_PRESS_MS → begin click-drag
                        val now = System.currentTimeMillis()
                        val heldMs = now - state.touchDownTime
                        val longPressed = state.isLongPressed ||
                            (!state.touchMoved && heldMs >= longPressMs)

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

    fun onPointerUp(pointerId: Int) {
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
                    if (state.isDoubleTapDrag) {
                        // Double-tap drag ended — release the held left button
                        sendMouseClick("left", false)
                        elementStates[elId] = state.copy(
                            isPressed        = false,
                            touchMoved       = false,
                            isDoubleTapDrag  = false,
                            lastTapTime      = 0L
                        )
                        return
                    }
                    if (state.isLongPressed) {
                        // Release the held left click (click-drag end)
                        sendMouseClick("left", false)
                        elementStates[elId] = state.copy(
                            isPressed     = false,
                            touchMoved    = false,
                            isLongPressed = false
                        )
                    } else if (!state.touchMoved) {
                        val heldMs = now - state.touchDownTime
                        if (heldMs < tapMaxMs) {
                            // ── SINGLE / DOUBLE-TAP DETECTION ────────────────────
                            // Compare time since last tap UP (stored in lastTapTime).
                            // If within doubleTapMs → tap-2: fire double-click.
                            // Otherwise            → tap-1: fire single click, record time.
                            val timeSinceLastTap = now - state.lastTapTime
                            val isDoubleTap = state.lastTapTime > 0L &&
                                    timeSinceLastTap < doubleTapMs

                            if (isDoubleTap) {
                                // TAP 2 — send two rapid click pairs so OS sees double-click
                                sendMouseClick("left", true)
                                sendMouseClick("left", false)
                                sendMouseClick("left", true)
                                sendMouseClick("left", false)
                                elementStates[elId] = state.copy(
                                    isPressed   = false,
                                    lastTapTime = 0L   // reset so next tap starts fresh
                                )
                            } else {
                                // TAP 1 — send single click, record time for tap-2 check
                                sendMouseClick("left", true)
                                sendMouseClick("left", false)
                                elementStates[elId] = state.copy(
                                    isPressed   = false,
                                    lastTapTime = now
                                )
                            }
                            return
                        }
                        // Held too long to be a tap — treat as no-op release
                        elementStates[elId] = state.copy(
                            isPressed     = false,
                            touchMoved    = false,
                            isLongPressed = false,
                            pointer2Id    = -1,
                            twoFingerDown = false,
                            twoFingerScrollActive = false
                        )
                    } else {
                        // Finger moved — drag/scroll release, no click
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
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Outer Box: Layer 1 = controller input, Layer 2 = exit button (zIndex)
    Box(modifier = Modifier.fillMaxSize()) {

        // ── Layer 1: Multi-touch controller area ──────────────────────────────
        @Suppress("UnusedBoxWithConstraintsScope")
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(ConsensiusColors.Background)
        ) {
            val screenW = maxWidth
            val screenH = maxHeight
            val currentElements = activeProfile?.pages?.getOrNull(currentPage)?.elements ?: emptyList()

            // ── Single root multitouch catcher ────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInteropFilter { event ->
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
                                    onPointerUp(pointerId)
                                }
                                MotionEvent.ACTION_CANCEL -> {
                                    pointerToElement.keys.toList().forEach { pid ->
                                        onPointerUp(pid)
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
                // Each element uses its own width and height (in dp),
                // never a shared or global size state. sizeDp is only used for
                // nonrectangular elements; widthDp and heightDp are used for positioning.
                currentElements.forEach { el ->
                    val widthDp  = el.width.dp
                    val heightDp = el.height.dp
                    val posX = screenW * el.x - widthDp / 2
                    val posY = screenH * el.y - heightDp / 2
                    val state = elementStates[el.id] ?: ElementRuntimeState()

                    Box(
                        modifier = Modifier
                            .offset(x = posX, y = posY)
                            // Register actual rendered bounds for hit testing.
                            // boundsInRoot() gives the true screen rect of this Box,
                            // including the 2.5x width of a touchpad.
                            .onGloballyPositioned { layoutCoords ->
                                val bounds = layoutCoords.boundsInRoot()
                                elementBounds[el.id] = android.graphics.RectF(
                                    bounds.left, bounds.top, bounds.right, bounds.bottom
                                )
                            }
                    ) {
                        when (el.type) {
                            ElementType.BUTTON ->
                                ControllerButton(
                                    element  = el,
                                    widthDp  = widthDp,
                                    heightDp = heightDp,
                                    isPressed = state.isPressed
                                )
                            ElementType.JOYSTICK ->
                                ControllerJoystick(
                                    element = el,
                                    sizeDp  = widthDp,  // joystick always square
                                    offsetX = state.joystickOffX,
                                    offsetY = state.joystickOffY
                                )
                            ElementType.DPAD ->
                                ControllerDpad(
                                    element  = el,
                                    widthDp  = widthDp,
                                    heightDp = heightDp,
                                    activeDir = state.activeDpadDir
                                )
                            ElementType.TOUCHPAD ->
                                ControllerTouchpad(
                                    element   = el,
                                    widthDp   = widthDp,
                                    heightDp  = heightDp,
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
                                        showPageOverlay = false
                                    }
                                    false
                                }
                        )
                    }
                }
            }
        }

        // ── Layer 2: Exit button — outside pointerInteropFilter, always on top ─
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(10f)
        ) {
            // Exit button is placed here, outside the multitouch
            // pointerInteropFilter Box so its onClick is never consumed by controller input.
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
                Text("X  EXIT", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }

            // ── Exit dialog (also in Layer 2) ─────────────────────────────────
            if (showExitDialog) {
                AlertDialog(
                    onDismissRequest = { showExitDialog = false },
                    containerColor = ConsensiusColors.Card,
                    title = { Text("Exit Controller", color = ConsensiusColors.TextPrimary, fontWeight = FontWeight.Bold) },
                    text  = { Text("Exit controller and disconnect?", color = ConsensiusColors.TextSecondary) },
                    confirmButton = {
                        Button(
                            onClick = {
                                webSocketManager.disconnect()
                                onNavigateBack()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ConsensiusColors.Error)
                        ) { Text("YES") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showExitDialog = false }) {
                            Text("NO", color = ConsensiusColors.TextSecondary)
                        }
                    }
                )
            }
        }

    } // end outer Box
}


// ─── Element Renderers ────────────────────────────────────────────────────────

@Composable
private fun ControllerButton(
    element: CanvasElement,
    widthDp: Dp,
    heightDp: Dp,
    isPressed: Boolean
) {
    val glowElevation by animateFloatAsState(
        targetValue   = if (isPressed) 14f else 2f,
        animationSpec = tween(80),
        label         = "btnGlow"
    )
    val sizeDp = minOf(widthDp, heightDp) // keep circular glow/shape based on smaller dimension
    Box(
        modifier = Modifier
            .width(widthDp)
            .height(heightDp)
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
            text       = element.label,
            color      = if (isPressed) Color.White else ConsensiusColors.TextPrimary,
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

        // BUG 2 FIX: Show user-defined element label (centered, faint watermark inside ring)
        Text(
            text = element.label.ifBlank { if (isSkillAim) "AIM" else "MOV" },
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

        // BUG 2 FIX: Show user-defined label BELOW the joystick ring
        if (element.label.isNotBlank()) {
            Text(
                text = element.label,
                color = Color(0xFF8899AA),
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 14.dp)
            )
        }
    }
}

@Composable
private fun ControllerDpad(
    element: CanvasElement,
    widthDp: Dp,
    heightDp: Dp,
    activeDir: String?
) {
    Box(
        modifier         = Modifier.width(widthDp).height(heightDp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.width(widthDp).height(heightDp)) {
            val w  = size.width
            val h  = size.height
            val aw = minOf(w, h) * 0.30f
            val cx = w / 2f
            val cy = h / 2f

            fun dirActive(dir: String) = activeDir == dir

            // Horizontal bar
            drawRect(
                color   = if (dirActive("left") || dirActive("right")) ConsensiusColors.Accent.copy(0.5f) else ConsensiusColors.Surface,
                topLeft = Offset(cx - w * 0.5f, cy - aw / 2),
                size    = androidx.compose.ui.geometry.Size(w, aw)
            )
            // Vertical bar
            drawRect(
                color   = if (dirActive("up") || dirActive("down")) ConsensiusColors.Accent.copy(0.5f) else ConsensiusColors.Surface,
                topLeft = Offset(cx - aw / 2, cy - h * 0.5f),
                size    = androidx.compose.ui.geometry.Size(aw, h)
            )
            // Active direction highlights
            if (dirActive("left"))  drawRect(ConsensiusColors.Accent.copy(0.4f), Offset(cx - w * 0.5f, cy - aw / 2), androidx.compose.ui.geometry.Size(w * 0.38f, aw))
            if (dirActive("right")) drawRect(ConsensiusColors.Accent.copy(0.4f), Offset(cx + w * 0.12f, cy - aw / 2), androidx.compose.ui.geometry.Size(w * 0.38f, aw))
            if (dirActive("up"))    drawRect(ConsensiusColors.Accent.copy(0.4f), Offset(cx - aw / 2, cy - h * 0.5f), androidx.compose.ui.geometry.Size(aw, h * 0.38f))
            if (dirActive("down"))  drawRect(ConsensiusColors.Accent.copy(0.4f), Offset(cx - aw / 2, cy + h * 0.12f), androidx.compose.ui.geometry.Size(aw, h * 0.38f))
            // Borders
            drawRect(ConsensiusColors.Accent.copy(0.35f), Offset(cx - w * 0.5f, cy - aw / 2), androidx.compose.ui.geometry.Size(w, aw), style = Stroke(1.5f))
            drawRect(ConsensiusColors.Accent.copy(0.35f), Offset(cx - aw / 2, cy - h * 0.5f), androidx.compose.ui.geometry.Size(aw, h), style = Stroke(1.5f))
        }

        // Arrow labels
        val arrowColor = ConsensiusColors.TextSecondary.copy(alpha = 0.5f)
        Text("^", color = if (activeDir == "up")    Color.White else arrowColor, fontSize = (heightDp.value * 0.15f).sp, modifier = Modifier.offset(y = -heightDp * 0.3f))
        Text("v", color = if (activeDir == "down")  Color.White else arrowColor, fontSize = (heightDp.value * 0.15f).sp, modifier = Modifier.offset(y =  heightDp * 0.3f))
        Text("<", color = if (activeDir == "left")  Color.White else arrowColor, fontSize = (widthDp.value  * 0.15f).sp, modifier = Modifier.offset(x = -widthDp  * 0.3f))
        Text(">", color = if (activeDir == "right") Color.White else arrowColor, fontSize = (widthDp.value  * 0.15f).sp, modifier = Modifier.offset(x =  widthDp  * 0.3f))

        if (element.label.isNotBlank()) {
            Text(
                text      = element.label,
                color     = Color(0xFF8899AA),
                fontSize  = 10.sp,
                textAlign = TextAlign.Center,
                modifier  = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-14).dp)
            )
        }
    }
}

@Composable
private fun ControllerTouchpad(
    element: CanvasElement,
    widthDp: Dp,
    heightDp: Dp,
    isActive: Boolean,
    onBoundsChanged: (android.graphics.RectF) -> Unit = {}
) {
    Box(
        modifier = Modifier
            .width(widthDp)
            .height(heightDp)
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
            // Report the actual rendered bounds of this wide touchpad box
            // back to the parent so hit testing covers the full area.
            .onGloballyPositioned { layoutCoords ->
                val b = layoutCoords.boundsInRoot()
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
            // BUG 2 FIX: Show user-defined label at top; fallback to "TOUCHPAD" if blank
            Text(
                text = element.label.ifBlank { "TOUCHPAD" },
                color = ConsensiusColors.TextSecondary.copy(0.5f),
                fontSize = 9.sp, letterSpacing = 1.sp
            )
            Text(
                "tap=click  2-tap=right  hold=drag  tap-tap-hold=scroll",
                color = ConsensiusColors.AccentSecondary.copy(0.35f),
                fontSize = 7.sp,
                letterSpacing = 0.3.sp
            )
        }
    }
}

