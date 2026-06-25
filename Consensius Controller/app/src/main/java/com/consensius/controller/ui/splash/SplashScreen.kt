package com.consensius.controller.ui.splash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.consensius.controller.datastore.SettingsDataStore
import com.consensius.controller.network.ConnectionState
import com.consensius.controller.network.WebSocketManager
import com.consensius.controller.ui.theme.ConsensiusColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

@Composable
fun SplashScreen(
    settingsDataStore: SettingsDataStore,
    webSocketManager: WebSocketManager,
    onNavigateToHome: () -> Unit
) {
    var statusText by remember { mutableStateOf("Initializing…") }
    var progress  by remember { mutableFloatStateOf(0f) }

    // Animated pulsing glow
    val infiniteTransition = rememberInfiniteTransition(label = "splash_anim")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue  = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )

    // Dot animation
    val dotPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dotPhase"
    )

    LaunchedEffect(Unit) {
        progress = 0.1f
        val savedIp   = settingsDataStore.serverIpFlow.first()
        val savedPort = settingsDataStore.serverPortFlow.first()

        val hasCustomIp = savedIp.isNotBlank() && savedIp != "192.168.1.100"
        if (hasCustomIp) {
            statusText = "Connecting to $savedIp…"
            progress   = 0.4f
            try {
                webSocketManager.connect(savedIp, savedPort)
                delay(1200)
                progress = 0.75f
                val state = webSocketManager.connectionState.value
                statusText = if (state is ConnectionState.Connected) {
                    "Connected!"
                } else {
                    "Server not reachable, continuing…"
                }
                delay(600)
            } catch (e: Exception) {
                statusText = "Auto-connect failed, continuing…"
                delay(500)
            }
        } else {
            delay(600)
            statusText = "Loading profiles…"
            progress   = 0.5f
            delay(800)
            statusText = "Ready!"
            progress   = 1f
            delay(400)
        }
        progress = 1f
        delay(200)
        onNavigateToHome()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ConsensiusColors.Background),
        contentAlignment = Alignment.Center
    ) {
        // Background glow blob
        Box(
            modifier = Modifier
                .size(320.dp)
                .scale(glowScale)
                .blur(80.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ConsensiusColors.Accent.copy(alpha = glowAlpha * 0.3f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo Icon — stylised gamepad shape drawn with boxes
            Box(contentAlignment = Alignment.Center) {
                // Outer glow ring
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(glowScale)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    ConsensiusColors.Accent.copy(alpha = glowAlpha * 0.5f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )
                // Inner logo circle
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    ConsensiusColors.Accent.copy(alpha = 0.85f),
                                    ConsensiusColors.AccentSecondary.copy(alpha = 0.6f)
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "CC",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            Text(
                text = "CONSENSIUS CONTROLLER",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = 3.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Gaming Controller for PC",
                fontSize = 13.sp,
                color = ConsensiusColors.TextSecondary,
                letterSpacing = 1.5.sp
            )

            Spacer(Modifier.height(40.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .width(220.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(50)),
                color = ConsensiusColors.Accent,
                trackColor = ConsensiusColors.Accent.copy(alpha = 0.15f),
                strokeCap = StrokeCap.Round,
            )

            Spacer(Modifier.height(20.dp))

            // Status with animated dots
            val dots = ".".repeat((dotPhase.toInt() % 3) + 1)
            Text(
                text = "$statusText$dots",
                fontSize = 12.sp,
                color = ConsensiusColors.TextSecondary
            )
        }
    }
}
