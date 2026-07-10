package com.consensius.controller.ui.controller

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.MotionEvent
import android.view.WindowInsetsController
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.Size
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
import com.consensius.controller.ui.theme.NeoColors
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds

// ─── Per-element runtime state ─────────────────────────────────────────────────
data class ElementRuntimeState(
    val isPressed:       Boolean = false,
    val joystickOffX:    Float   = 0f,
    val joystickOffY:    Float   = 0f,
    val activeDpadDir:   String? = null,
    val lastTouchX:      Float   = 0f,
    val lastTouchY:      Float   = 0f,
    val touchDownTime:   Long    = 0L,
    val touchDownX:      Float   = 0f,
    val touchDownY:      Float   = 0f,
    val touchMoved:      Boolean = false,
    val isLongPressed:   Boolean = false,
    val pointer2Id:      Int     = -1,
    val pointer2X:       Float   = 0f,
    val pointer2Y:       Float   = 0f,
    val twoFingerDown:   Boolean = false,
    val lastTapTime:     Long    = 0L,
    val isDoubleTapDrag: Boolean = false,
    val twoFingerScrollActive: Boolean = false
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
    val elementBounds = remember { mutableStateMapOf<String, android.graphics.RectF>() }
    val touchpadActualBounds = remember { mutableStateMapOf<String, android.graphics.RectF>() }
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

    fun sendJoystick(stickType: String, x: Float, y: Float) {
        // Kirim langsung tanpa deadzone processing - deadzone sudah ditangani di FPS loop
        // lewat (rawNx = joystickOffX / maxR) yang sudah dipotong oleh maxR
        val nx = x.coerceIn(-1f, 1f)
        val ny = y.coerceIn(-1f, 1f)
        val stickName = if (stickType == JoystickType.SKILL_AIM.name) "camera" else "movement"
        webSocketManager.send(gson.toJson(mapOf(
            "type"  to "joystick",
            "stick" to stickName,
            "x"     to nx,
            "y"     to ny
        )))
    }

    fun sendSkillAim(key: String, angle: Float, magnitude: Float, state: String, aimStick: String, extraKeys: List<String>) {
        webSocketManager.send(gson.toJson(mapOf(
            "type"      to "skill_aim",
            "action"    to key,
            "angle"     to angle,
            "magnitude" to magnitude,
            "state"     to state,
            "aimStick"  to aimStick,
            "extraKeys" to extraKeys
        )))
    }

    fun sendMouseMove(dx: Float, dy: Float) {
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

    // ── FPS loop ─────────────────────────────────────────────────────────────
    // Pengiriman utama joystick sudah di onPointerMove (saat jari bergerak).
    // FPS loop ini hanya sebagai keep-alive saat jari diam tapi tetap ditekan.
    LaunchedEffect(Unit) {
        val intervalMs = 16L // ~60fps
        while (true) {
            delay(intervalMs)
            try {
                val page = activeProfile?.pages?.getOrNull(currentPage) ?: continue
                page.elements
                    .filter { it.type == ElementType.JOYSTICK }
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
                            val nx = (state.joystickOffX / maxR).coerceIn(-1f, 1f)
                            val ny = -(state.joystickOffY / maxR).coerceIn(-1f, 1f)
                            if (nx.isFinite() && ny.isFinite()) {
                                if (el.joystickConfig.type == JoystickType.MOVEMENT) {
                                    sendJoystick(JoystickType.MOVEMENT.name, nx, ny)
                                } else if (el.joystickConfig.type == JoystickType.SKILL_AIM && el.joystickConfig.skillKey.isBlank()) {
                                    sendJoystick(JoystickType.SKILL_AIM.name, nx, ny)
                                }
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
        for (el in elements.reversed()) {
            val rect = elementBounds[el.id] ?: continue
            if (rect.contains(rawX, rawY)) return el
        }
        return null
    }

    val touchpadDragThresholdPx = 10f
    val longPressMs = 500L
    val tapMaxMs = 400L
    val doubleTapMs = 420L

    fun onPointerDown(pointerId: Int, rawX: Float, rawY: Float) {
        val page = activeProfile?.pages?.getOrNull(currentPage) ?: return
        val el = findElementAt(rawX, rawY, page.elements) ?: return
        pointerToElement[pointerId] = el.id
        when (el.type) {
            ElementType.BUTTON -> {
                elementStates[el.id] = ElementRuntimeState(isPressed = true)
                // Tekan semua combo keys terlebih dahulu, baru tekan key utama
                el.buttonConfig.comboKeys.forEach { combo -> sendButton(combo, true) }
                sendButton(el.buttonConfig.key, true)
            }
            ElementType.JOYSTICK -> {
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
                if (existing != null && existing.twoFingerDown) return
                if (existing != null && existing.isPressed && existing.pointer2Id == -1) {
                    elementStates[el.id] = existing.copy(
                        pointer2Id   = pointerId,
                        pointer2X    = rawX,
                        pointer2Y    = rawY,
                        twoFingerDown = true,
                        twoFingerScrollActive = false
                    )
                } else {
                    val timeSinceLastTap = now - (existing?.lastTapTime ?: 0L)
                    val isSecondTap = (existing?.lastTapTime ?: 0L) > 0L && timeSinceLastTap < doubleTapMs
                    if (isSecondTap) {
                        sendMouseClick("left", true)
                        elementStates[el.id] = ElementRuntimeState(
                            isPressed       = true,
                            lastTouchX      = rawX,
                            lastTouchY      = rawY,
                            touchDownTime   = now,
                            touchDownX      = rawX,
                            touchDownY      = rawY,
                            touchMoved      = false,
                            isDoubleTapDrag = true,
                            lastTapTime     = 0L
                        )
                    } else {
                        elementStates[el.id] = ElementRuntimeState(
                            isPressed    = true,
                            lastTouchX   = rawX,
                            lastTouchY   = rawY,
                            touchDownTime = now,
                            touchDownX   = rawX,
                            touchDownY   = rawY,
                            touchMoved   = false,
                            isLongPressed = false,
                            pointer2Id   = -1,
                            twoFingerDown = false,
                            twoFingerScrollActive = false,
                            lastTapTime  = existing?.lastTapTime ?: 0L
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
                val rect = elementBounds[elId]
                val cx: Float; val cy: Float; val maxR: Float
                if (rect != null && rect.width() > 0f) {
                    cx   = rect.centerX()
                    cy   = rect.centerY()
                    maxR = rect.width() * 0.4f
                } else {
                    cx   = state.lastTouchX
                    cy   = state.lastTouchY
                    maxR = with(density) { el.width.dp.toPx() * 0.4f }.coerceAtLeast(50f)
                }
                val rawDx = rawX - cx
                val rawDy = rawY - cy
                val dist = sqrt(rawDx * rawDx + rawDy * rawDy)
                val clampedX = if (dist <= maxR) rawDx else rawDx / dist * maxR
                val clampedY = if (dist <= maxR) rawDy else rawDy / dist * maxR
                elementStates[elId] = state.copy(joystickOffX = clampedX, joystickOffY = clampedY)
                if (maxR > 0f) {
                    val nx = (clampedX / maxR).coerceIn(-1f, 1f)
                    val ny = -(clampedY / maxR).coerceIn(-1f, 1f)

                    if (el.joystickConfig.type == JoystickType.SKILL_AIM) {
                        if (el.joystickConfig.skillKey.isNotBlank()) {
                            val angleDeg = Math.toDegrees(atan2(clampedY.toDouble(), clampedX.toDouble())).toFloat()
                            val magnitude = sqrt(nx * nx + ny * ny).coerceIn(0f, 1f)
                            sendSkillAim(
                                el.joystickConfig.skillKey,
                                angleDeg,
                                magnitude,
                                "aiming",
                                el.joystickConfig.aimStick,
                                el.joystickConfig.extraKeys
                            )
                        } else {
                            // Analog kanan murni (skillKey kosong): kirim sebagai right stick
                            sendJoystick(JoystickType.SKILL_AIM.name, nx, ny)
                        }
                    } else {
                        // Analog kiri (movement)
                        sendJoystick(JoystickType.MOVEMENT.name, nx, ny)
                    }
                }
            }
            ElementType.TOUCHPAD -> {
                if (state.twoFingerDown) {
                    if (pointerId == state.pointer2Id) {
                        val dy2 = rawY - state.pointer2Y
                        if (kotlin.math.abs(dy2) > 1f) {
                            val normalizedDy = -(dy2 / 15f)
                            sendMouseScroll(normalizedDy)
                            elementStates[elId] = state.copy(pointer2X = rawX, pointer2Y = rawY, twoFingerScrollActive = true)
                        } else {
                            elementStates[elId] = state.copy(pointer2X = rawX, pointer2Y = rawY)
                        }
                    } else {
                        elementStates[elId] = state.copy(lastTouchX = rawX, lastTouchY = rawY)
                    }
                } else {
                    if (state.isDoubleTapDrag) {
                        val dx = rawX - state.lastTouchX
                        val dy = rawY - state.lastTouchY
                        if (dx != 0f || dy != 0f) sendMouseMove(dx, dy)
                        elementStates[elId] = state.copy(lastTouchX = rawX, lastTouchY = rawY, touchMoved = true)
                        return
                    }
                    val totalDx = rawX - state.touchDownX
                    val totalDy = rawY - state.touchDownY
                    val totalDist = sqrt(totalDx * totalDx + totalDy * totalDy)
                    val moved = state.touchMoved || totalDist > touchpadDragThresholdPx
                    if (moved) {
                        val now = System.currentTimeMillis()
                        val heldMs = now - state.touchDownTime
                        val longPressed = state.isLongPressed || (!state.touchMoved && heldMs >= longPressMs)
                        if (longPressed && !state.isLongPressed) sendMouseClick("left", true)
                        val dx = rawX - state.lastTouchX
                        val dy = rawY - state.lastTouchY
                        sendMouseMove(dx, dy)
                        elementStates[elId] = state.copy(lastTouchX = rawX, lastTouchY = rawY, touchMoved = true, isLongPressed = longPressed)
                    } else {
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
            ElementType.BUTTON -> { }
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
                // Lepas semua combo keys
                el.buttonConfig.comboKeys.forEach { combo -> sendButton(combo, false) }
            }
            ElementType.JOYSTICK -> {
                if (el.joystickConfig.type == JoystickType.SKILL_AIM) {
                    if (el.joystickConfig.skillKey.isNotBlank()) {
                        val maxR = (elementBounds[elId]?.width() ?: 1f) * 0.4f
                        val nx   = if (maxR > 0f) (state.joystickOffX / maxR).coerceIn(-1f, 1f) else 0f
                        val ny   = if (maxR > 0f) -(state.joystickOffY / maxR).coerceIn(-1f, 1f) else 0f
                        val mag  = sqrt(nx * nx + ny * ny).coerceIn(0f, 1f)
                        val angleDeg = Math.toDegrees(atan2(state.joystickOffY.toDouble(), state.joystickOffX.toDouble())).toFloat()
                        sendSkillAim(
                            el.joystickConfig.skillKey,
                            angleDeg,
                            mag.coerceAtLeast(0.01f),
                            "cast",
                            el.joystickConfig.aimStick,
                            el.joystickConfig.extraKeys
                        )
                    } else {
                        webSocketManager.send(gson.toJson(mapOf("type" to "joystick", "stick" to "camera", "x" to 0f, "y" to 0f)))
                    }
                } else {
                    webSocketManager.send(gson.toJson(mapOf("type" to "joystick", "stick" to "movement", "x" to 0f, "y" to 0f)))
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
                    if (!state.twoFingerScrollActive) {
                        sendMouseClick("right", true)
                        sendMouseClick("right", false)
                    }
                    elementStates[elId] = state.copy(pointer2Id = -1, twoFingerDown = false, twoFingerScrollActive = false)
                } else if (state.twoFingerDown) {
                    elementStates[elId] = state.copy(isPressed = false, twoFingerDown = false, twoFingerScrollActive = false, pointer2Id = -1)
                } else {
                    if (state.isDoubleTapDrag) {
                        sendMouseClick("left", false)
                        elementStates[elId] = state.copy(isPressed = false, touchMoved = false, isDoubleTapDrag = false, lastTapTime = 0L)
                        return
                    }
                    if (state.isLongPressed) {
                        sendMouseClick("left", false)
                        elementStates[elId] = state.copy(isPressed = false, touchMoved = false, isLongPressed = false)
                    } else if (!state.touchMoved) {
                        val heldMs = now - state.touchDownTime
                        if (heldMs < tapMaxMs) {
                            val timeSinceLastTap = now - state.lastTapTime
                            val isDoubleTap = state.lastTapTime > 0L && timeSinceLastTap < doubleTapMs
                            if (isDoubleTap) {
                                sendMouseClick("left", true); sendMouseClick("left", false)
                                sendMouseClick("left", true); sendMouseClick("left", false)
                                elementStates[elId] = state.copy(isPressed = false, lastTapTime = 0L)
                            } else {
                                sendMouseClick("left", true); sendMouseClick("left", false)
                                elementStates[elId] = state.copy(isPressed = false, lastTapTime = now)
                            }
                            return
                        }
                        elementStates[elId] = state.copy(isPressed = false, touchMoved = false, isLongPressed = false, pointer2Id = -1, twoFingerDown = false, twoFingerScrollActive = false)
                    } else {
                        elementStates[elId] = state.copy(isPressed = false, touchMoved = false, isLongPressed = false, pointer2Id = -1, twoFingerDown = false, twoFingerScrollActive = false)
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize()) {

        // ── Layer 1: Multi-touch controller area ──────────────────────────────
        @Suppress("UnusedBoxWithConstraintsScope")
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(NeoColors.Background)
        ) {
            val screenW = maxWidth
            val screenH = maxHeight
            val currentElements = activeProfile?.pages?.getOrNull(currentPage)?.elements ?: emptyList()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInteropFilter { event ->
                        try {
                            val action = event.actionMasked
                            val pointerIndex = event.actionIndex
                            val pointerId = event.getPointerId(pointerIndex)
                            when (action) {
                                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
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
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> onPointerUp(pointerId)
                                MotionEvent.ACTION_CANCEL -> {
                                    pointerToElement.keys.toList().forEach { pid -> onPointerUp(pid) }
                                    pointerToElement.clear()
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ControllerScreen", "Touch event error: ${e.message}")
                        }
                        true
                    }
            ) {
                currentElements.forEach { el ->
                    val widthDp  = el.width.dp
                    val heightDp = el.height.dp
                    val posX = screenW * el.x - widthDp / 2
                    val posY = screenH * el.y - heightDp / 2
                    val state = elementStates[el.id] ?: ElementRuntimeState()

                    Box(
                        modifier = Modifier
                            .offset(x = posX, y = posY)
                            .onGloballyPositioned { layoutCoords ->
                                val bounds = layoutCoords.boundsInRoot()
                                elementBounds[el.id] = android.graphics.RectF(
                                    bounds.left, bounds.top, bounds.right, bounds.bottom
                                )
                            }
                    ) {
                        when (el.type) {
                            ElementType.BUTTON ->
                                ControllerButton(element = el, widthDp = widthDp, heightDp = heightDp, isPressed = state.isPressed)
                            ElementType.JOYSTICK ->
                                ControllerJoystick(element = el, sizeDp = widthDp, offsetX = state.joystickOffX, offsetY = state.joystickOffY)
                            ElementType.DPAD ->
                                ControllerDpad(element = el, widthDp = widthDp, heightDp = heightDp, activeDir = state.activeDpadDir)
                            ElementType.TOUCHPAD ->
                                ControllerTouchpad(element = el, widthDp = widthDp, heightDp = heightDp, isActive = state.isPressed,
                                    onBoundsChanged = { rect -> touchpadActualBounds[el.id] = rect })
                        }
                    }
                }

                // Profile label
                Text(
                    text = activeProfile?.name ?: "",
                    color = NeoColors.TextMuted,
                    fontSize = 8.sp,
                    letterSpacing = 1.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 10.dp)
                )

                // Page indicator
                val pageCount = activeProfile?.pages?.size ?: 1
                if (pageCount > 1) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(pageCount) { idx ->
                            val isActive = idx == currentPage
                            Box(
                                modifier = Modifier
                                    .size(if (isActive) 8.dp else 5.dp)
                                    .background(
                                        if (isActive) NeoColors.ElectricBlue else NeoColors.TextMuted,
                                        CircleShape
                                    )
                            )
                        }
                    }
                }

                // Page switcher pill
                if (pageCount > 1) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 26.dp)
                            .background(NeoColors.GlassCard, RoundedCornerShape(12.dp))
                            .border(0.5.dp, NeoColors.GlassBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "Page ${currentPage + 1} / $pageCount  (hold to switch)",
                            color = NeoColors.TextTertiary,
                            fontSize = 8.sp
                        )
                    }
                }

                // Page overlay
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
                                            if (isActive) NeoColors.ElectricBlue.copy(alpha = 0.12f)
                                            else NeoColors.GlassCard,
                                            RoundedCornerShape(14.dp)
                                        )
                                        .border(
                                            1.5.dp,
                                            if (isActive) NeoColors.ElectricBlue else NeoColors.GlassBorder,
                                            RoundedCornerShape(14.dp)
                                        )
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    Text(page.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Text("${page.elements.size} elements", color = NeoColors.TextSecondary, fontSize = 10.sp)
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInteropFilter { event ->
                                    if (event.actionMasked == MotionEvent.ACTION_UP) showPageOverlay = false
                                    false
                                }
                        )
                    }
                }
            }
        }

        // ── Layer 2: Exit button ─────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxSize().zIndex(10f)) {
            Button(
                onClick = { showExitDialog = true },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 6.dp, start = 8.dp)
                    .height(28.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeoColors.Error.copy(alpha = 0.65f)
                ),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                Text("X  EXIT", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }

            if (showExitDialog) {
                AlertDialog(
                    onDismissRequest = { showExitDialog = false },
                    containerColor = NeoColors.GlassCard,
                    shape = RoundedCornerShape(20.dp),
                    title = { Text("Exit Controller", color = NeoColors.TextPrimary, fontWeight = FontWeight.Bold) },
                    text  = { Text("Exit controller and disconnect?", color = NeoColors.TextSecondary) },
                    confirmButton = {
                        Button(
                            onClick = { webSocketManager.disconnect(); onNavigateBack() },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeoColors.Error)
                        ) { Text("Yes") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showExitDialog = false }) {
                            Text("No", color = NeoColors.TextSecondary)
                        }
                    }
                )
            }
        }
    }
}

// ─── Element Renderers ────────────────────────────────────────────────────────

@Composable
private fun ControllerButton(element: CanvasElement, widthDp: Dp, heightDp: Dp, isPressed: Boolean) {
    val glowElevation by animateFloatAsState(
        targetValue   = if (isPressed) 14f else 2f,
        animationSpec = tween(80),
        label         = "btnGlow"
    )
    val sizeDp = minOf(widthDp, heightDp)
    Box(
        modifier = Modifier
            .width(widthDp).height(heightDp)
            .shadow(glowElevation.dp, CircleShape,
                ambientColor = NeoColors.ElectricBlue,
                spotColor    = NeoColors.ElectricBlue)
            .background(
                if (isPressed)
                    Brush.radialGradient(
                        colors = listOf(
                            NeoColors.ElectricBlue.copy(alpha = 0.35f),
                            NeoColors.Amethyst.copy(alpha = 0.15f),
                            NeoColors.Surface
                        )
                    )
                else
                    Brush.radialGradient(
                        colors = listOf(
                            NeoColors.GlassElevated,
                            NeoColors.SurfaceLight
                        )
                    ),
                CircleShape
            )
            .border(
                width = if (isPressed) 2.dp else 1.5.dp,
                color = if (isPressed) NeoColors.ElectricBlue else NeoColors.AccentBorder,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            element.label,
            color = if (isPressed) Color.White else NeoColors.TextPrimary,
            fontWeight = FontWeight.ExtraBold,
            fontSize = (sizeDp.value * 0.22f).sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ControllerJoystick(element: CanvasElement, sizeDp: Dp, offsetX: Float, offsetY: Float) {
    val isSkillAim = element.joystickConfig.type == JoystickType.SKILL_AIM
    val accentColor = if (isSkillAim) NeoColors.Cyan else NeoColors.ElectricBlue
    val thumbGradient = if (isSkillAim)
        Brush.radialGradient(listOf(NeoColors.Cyan, NeoColors.DeepBlue))
    else
        Brush.radialGradient(listOf(NeoColors.IceBlue, NeoColors.ElectricBlue))

    val thumbSize = sizeDp * 0.36f

    val infiniteTransition = rememberInfiniteTransition(label = "js_ring")
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f, targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ringAlpha"
    )

    Box(modifier = Modifier.size(sizeDp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(accentColor.copy(alpha = ringAlpha), r, style = Stroke(2f))
            drawCircle(
                Brush.radialGradient(listOf(accentColor.copy(alpha = 0.05f), Color.Transparent)),
                r
            )
        }

        Text(
            element.label.ifBlank { if (isSkillAim) "AIM" else "MOV" },
            color = accentColor.copy(alpha = 0.15f),
            fontSize = (sizeDp.value * 0.13f).sp,
            fontWeight = FontWeight.Bold
        )

        Box(modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .size(thumbSize)
            .shadow(10.dp, CircleShape)
            .background(thumbGradient, CircleShape))

        if (element.label.isNotBlank()) {
            Text(
                element.label, color = NeoColors.TextTertiary, fontSize = 10.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).offset(y = 14.dp)
            )
        }
    }
}

@Composable
private fun ControllerDpad(element: CanvasElement, widthDp: Dp, heightDp: Dp, activeDir: String?) {
    Box(modifier = Modifier.width(widthDp).height(heightDp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.width(widthDp).height(heightDp)) {
            val w = size.width; val h = size.height; val aw = minOf(w, h) * 0.30f
            val cx = w / 2f; val cy = h / 2f
            fun dirActive(dir: String) = activeDir == dir

            drawRect(if (dirActive("left") || dirActive("right")) NeoColors.ElectricBlue.copy(0.4f) else NeoColors.Surface,
                Offset(cx - w * 0.5f, cy - aw / 2), Size(w, aw))
            drawRect(if (dirActive("up") || dirActive("down")) NeoColors.ElectricBlue.copy(0.4f) else NeoColors.Surface,
                Offset(cx - aw / 2, cy - h * 0.5f), Size(aw, h))

            if (dirActive("left"))  drawRect(NeoColors.ElectricBlue.copy(0.35f), Offset(cx - w * 0.5f, cy - aw / 2), Size(w * 0.38f, aw))
            if (dirActive("right")) drawRect(NeoColors.ElectricBlue.copy(0.35f), Offset(cx + w * 0.12f, cy - aw / 2), Size(w * 0.38f, aw))
            if (dirActive("up"))    drawRect(NeoColors.ElectricBlue.copy(0.35f), Offset(cx - aw / 2, cy - h * 0.5f), Size(aw, h * 0.38f))
            if (dirActive("down"))  drawRect(NeoColors.ElectricBlue.copy(0.35f), Offset(cx - aw / 2, cy + h * 0.12f), Size(aw, h * 0.38f))

            drawRect(NeoColors.ElectricBlue.copy(0.3f), Offset(cx - w * 0.5f, cy - aw / 2), Size(w, aw), style = Stroke(1.5f))
            drawRect(NeoColors.ElectricBlue.copy(0.3f), Offset(cx - aw / 2, cy - h * 0.5f), Size(aw, h), style = Stroke(1.5f))
        }

        val arrowColor = NeoColors.TextTertiary.copy(alpha = 0.5f)
        Text("^", color = if (activeDir == "up") Color.White else arrowColor, fontSize = (heightDp.value * 0.15f).sp, modifier = Modifier.offset(y = -heightDp * 0.3f))
        Text("v", color = if (activeDir == "down") Color.White else arrowColor, fontSize = (heightDp.value * 0.15f).sp, modifier = Modifier.offset(y =  heightDp * 0.3f))
        Text("<", color = if (activeDir == "left") Color.White else arrowColor, fontSize = (widthDp.value * 0.15f).sp, modifier = Modifier.offset(x = -widthDp * 0.3f))
        Text(">", color = if (activeDir == "right") Color.White else arrowColor, fontSize = (widthDp.value * 0.15f).sp, modifier = Modifier.offset(x =  widthDp * 0.3f))

        if (element.label.isNotBlank()) {
            Text(element.label, color = NeoColors.TextTertiary, fontSize = 10.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = (-14).dp))
        }
    }
}

@Composable
private fun ControllerTouchpad(element: CanvasElement, widthDp: Dp, heightDp: Dp, isActive: Boolean, onBoundsChanged: (android.graphics.RectF) -> Unit = {}) {
    Box(
        modifier = Modifier
            .width(widthDp).height(heightDp)
            .background(
                Brush.linearGradient(
                    listOf(
                        NeoColors.Surface.copy(alpha = 0.5f),
                        NeoColors.SurfaceLight.copy(alpha = 0.3f)
                    )
                ),
                RoundedCornerShape(16.dp)
            )
            .border(
                1.dp,
                if (isActive) NeoColors.Cyan.copy(0.6f) else NeoColors.Cyan.copy(0.25f),
                RoundedCornerShape(16.dp)
            )
            .onGloballyPositioned { layoutCoords ->
                val b = layoutCoords.boundsInRoot()
                onBoundsChanged(android.graphics.RectF(b.left, b.top, b.right, b.bottom))
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sp = 16.dp.toPx()
            var gx = sp
            while (gx < size.width) {
                var gy = sp
                while (gy < size.height) {
                    drawCircle(NeoColors.Cyan.copy(alpha = 0.08f), 1.0f, Offset(gx, gy))
                    gy += sp
                }
                gx += sp
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                element.label.ifBlank { "TOUCHPAD" },
                color = NeoColors.TextTertiary.copy(0.5f),
                fontSize = 9.sp,
                letterSpacing = 1.sp
            )
            Text(
                "tap=click  2-tap=right  hold=drag  tap-tap-hold=scroll",
                color = NeoColors.Cyan.copy(0.3f),
                fontSize = 7.sp,
                letterSpacing = 0.3.sp
            )
        }
    }
}
