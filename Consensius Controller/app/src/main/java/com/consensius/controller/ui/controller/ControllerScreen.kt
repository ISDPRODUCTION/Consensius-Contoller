package com.consensius.controller.ui.controller

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

import com.consensius.controller.datastore.SettingsDataStore
import com.consensius.controller.input.InputState
import com.consensius.controller.network.WebSocketManager
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ControllerScreen(
    webSocketManager: WebSocketManager,
    settingsDataStore: SettingsDataStore,
    onNavigateBack: () -> Unit
) {
    val gson = remember { Gson() }
    var deadzone by remember { mutableStateOf(0.1f) }
    var sensitivity by remember { mutableStateOf(1.0f) }
    var fps by remember { mutableStateOf(60) }

    LaunchedEffect(key1 = true) {
        deadzone = settingsDataStore.deadzoneFlow.first()
        sensitivity = settingsDataStore.sensitivityFlow.first()
        fps = settingsDataStore.sendFpsFlow.first()
    }

    // Interactive controller state tracking
    var leftStickX by remember { mutableStateOf(0f) }
    var leftStickY by remember { mutableStateOf(0f) }
    var rightStickX by remember { mutableStateOf(0f) }
    var rightStickY by remember { mutableStateOf(0f) }
    val buttonStates = remember { mutableStateMapOf<String, Boolean>() }

    // Helper functions to send joystick updates
    fun sendJoystickUpdate(stick: String, x: Float, y: Float) {
        val magnitude = sqrt(x * x + y * y)
        val finalX: Float
        val finalY: Float

        if (magnitude < deadzone) {
            finalX = 0f
            finalY = 0f
        } else {
            // Apply sensitivity
            val scaledX = (x / magnitude) * ((magnitude - deadzone) / (1f - deadzone)) * sensitivity
            val scaledY = (y / magnitude) * ((magnitude - deadzone) / (1f - deadzone)) * sensitivity
            // Clamp to -1.0 .. 1.0
            finalX = scaledX.coerceIn(-1.0f, 1.0f)
            finalY = scaledY.coerceIn(-1.0f, 1.0f)
        }

        val json = gson.toJson(
            mapOf(
                "type" to "joystick",
                "stick" to stick,
                "x" to finalX,
                "y" to finalY
            )
        )
        webSocketManager.send(json)
    }

    // Helper function to send button state
    fun sendButtonUpdate(action: String, isDown: Boolean) {
        buttonStates[action] = isDown
        val state = if (isDown) "down" else "up"
        val json = gson.toJson(
            mapOf(
                "type" to "button",
                "action" to action,
                "state" to state
            )
        )
        webSocketManager.send(json)
    }

    // Render elements layout
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Back Button
        Button(
            onClick = {
                webSocketManager.disconnect()
                onNavigateBack()
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Exit Controller")
        }

        // Left Joystick
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 48.dp, y = (-48).dp)
        ) {
            Joystick(
                onValueChange = { x, y ->
                    leftStickX = x
                    leftStickY = y
                    sendJoystickUpdate("left", x, y)
                }
            )
        }

        // Right Joystick
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-300).dp, y = (-48).dp)
        ) {
            Joystick(
                onValueChange = { x, y ->
                    rightStickX = x
                    rightStickY = y
                    sendJoystickUpdate("right", x, y)
                }
            )
        }

        // Right Side action button cluster
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-48).dp, y = (-48).dp)
                .size(240.dp)
        ) {
            // Skill 1
            ActionButton(
                label = "S1",
                action = "skill1",
                onButtonStateChange = ::sendButtonUpdate,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 0.dp)
            )

            // Skill 2
            ActionButton(
                label = "S2",
                action = "skill2",
                onButtonStateChange = ::sendButtonUpdate,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-20).dp, y = 20.dp)
            )

            // Skill 3
            ActionButton(
                label = "S3",
                action = "skill3",
                onButtonStateChange = ::sendButtonUpdate,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 0.dp)
            )

            // Attack
            ActionButton(
                label = "ATK",
                action = "attack",
                onButtonStateChange = ::sendButtonUpdate,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-10).dp, y = (-10).dp)
            )

            // Spell
            ActionButton(
                label = "SPL",
                action = "spell",
                onButtonStateChange = ::sendButtonUpdate,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 0.dp)
            )

            // Recall
            ActionButton(
                label = "RCL",
                action = "recall",
                onButtonStateChange = ::sendButtonUpdate,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = 20.dp, y = (-10).dp)
            )

            // Regen
            ActionButton(
                label = "REG",
                action = "regen",
                onButtonStateChange = ::sendButtonUpdate,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = 10.dp)
            )
        }
    }
}

@Composable
fun Joystick(
    onValueChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val maxRadius = 100.dp
    val maxRadiusPx = 250f // Mock rough layout dimension, dynamic detection is better but raw coordinates work perfectly here
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .size(150.dp)
            .background(Color.DarkGray.copy(alpha = 0.5f), shape = CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        dragOffset = Offset.Zero
                        onValueChange(0f, 0f)
                    },
                    onDragCancel = {
                        dragOffset = Offset.Zero
                        onValueChange(0f, 0f)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newOffset = dragOffset + dragAmount
                        val distance = sqrt(newOffset.x * newOffset.x + newOffset.y * newOffset.y)
                        
                        if (distance <= maxRadiusPx) {
                            dragOffset = newOffset
                        } else {
                            val angle = kotlin.math.atan2(newOffset.y, newOffset.x)
                            dragOffset = Offset(
                                x = cos(angle) * maxRadiusPx,
                                y = sin(angle) * maxRadiusPx
                            )
                        }

                        // Normalize to -1.0 .. 1.0 range
                        val normX = (dragOffset.x / maxRadiusPx).coerceIn(-1f, 1f)
                        // Invert Y axis standard coordinate to fit conventional joysticks
                        val normY = (-dragOffset.y / maxRadiusPx).coerceIn(-1f, 1f)
                        onValueChange(normX, normY)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Stick handle
        Box(
            modifier = Modifier
                .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
                .size(60.dp)
                .background(Color.LightGray, shape = CircleShape)
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ActionButton(
    label: String,
    action: String,
    onButtonStateChange: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(60.dp)
            .background(
                color = if (isPressed) MaterialTheme.colorScheme.primary else Color.DarkGray,
                shape = CircleShape
            )
            .pointerInteropFilter { motionEvent ->
                when (motionEvent.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isPressed = true
                        onButtonStateChange(action, true)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isPressed = false
                        onButtonStateChange(action, false)
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}
