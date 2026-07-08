package com.consensius.controller.ui.splash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import com.consensius.controller.datastore.SettingsDataStore
import com.consensius.controller.network.ConnectionState
import com.consensius.controller.network.WebSocketManager
import com.consensius.controller.ui.theme.GraffitiFont
import com.consensius.controller.ui.theme.NeoColors
import com.consensius.controller.ui.theme.drawAmbientParticles
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SplashScreen(
    settingsDataStore: SettingsDataStore,
    webSocketManager: WebSocketManager,
    onNavigateToHome: () -> Unit
) {
    var statusText by remember { mutableStateOf("Initializing…") }
    var progress by remember { mutableFloatStateOf(0f) }

    // ── Animations ───────────────────────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "splash")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowAlpha"
    )
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.92f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowScale"
    )
    val particlePhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Restart),
        label = "particlePhase"
    )
    val dotPhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "dotPhase"
    )

    // ── Ring rotation animation ──────────────────────────────────────────────
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Restart),
        label = "ringRotation"
    )

    LaunchedEffect(Unit) {
        progress = 0.1f
        val savedIp = settingsDataStore.serverIpFlow.first()
        val savedPort = settingsDataStore.serverPortFlow.first()

        val hasCustomIp = savedIp.isNotBlank() && savedIp != "192.168.1.100"
        if (hasCustomIp) {
            statusText = "Connecting to $savedIp…"
            progress = 0.4f
            try {
                webSocketManager.connect(savedIp, savedPort)
                delay(1200)
                progress = 0.75f
                val state = webSocketManager.connectionState.value
                statusText = if (state is ConnectionState.Connected) "Connected!"
                else "Server not reachable, continuing…"
                delay(600)
            } catch (e: Exception) {
                statusText = "Auto-connect failed, continuing…"
                delay(500)
            }
        } else {
            delay(600)
            statusText = "Loading profiles…"
            progress = 0.5f
            delay(800)
            statusText = "Ready!"
            progress = 1f
            delay(400)
        }
        progress = 1f
        delay(200)
        onNavigateToHome()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NeoColors.Background),
        contentAlignment = Alignment.Center
    ) {
        // ── Ambient particles ──────────────────────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawAmbientParticles(
                particleCount = 10,
                primaryColor = NeoColors.ElectricBlue.copy(alpha = 0.08f),
                secondaryColor = NeoColors.Amethyst.copy(alpha = 0.05f),
                phase = particlePhase
            )
        }

        // ── Outer glowing ring ─────────────────────────────────────────────
        Canvas(
            modifier = Modifier
                .size(320.dp)
                .scale(glowScale)
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            val r = size.minDimension / 2

            // Outer ring glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        NeoColors.ElectricBlue.copy(alpha = glowAlpha * 0.15f),
                        Color.Transparent
                    )
                ),
                radius = r
            )

            // Rotating arc
            val startAngle = ringRotation
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        NeoColors.ElectricBlue.copy(alpha = 0.3f),
                        NeoColors.Amethyst.copy(alpha = 0.15f),
                        NeoColors.Cyan.copy(alpha = 0.2f),
                        Color.Transparent,
                        NeoColors.ElectricBlue.copy(alpha = 0.3f)
                    )
                ),
                startAngle = startAngle,
                sweepAngle = 120f,
                useCenter = false,
                style = Stroke(width = 2f, cap = StrokeCap.Round),
                topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
                size = Size(r * 2 - 8.dp.toPx(), r * 2 - 8.dp.toPx())
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ── Logo with ambient glow ─────────────────────────────────────
            Box(contentAlignment = Alignment.Center) {
                // Ambient glow behind logo
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .scale(glowScale)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    NeoColors.ElectricBlue.copy(alpha = glowAlpha * 0.2f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )

                // Logo
                Image(
                    painter = painterResource(com.consensius.controller.R.drawable.logo_consensius_full),
                    contentDescription = "Consensius Controller logo",
                    modifier = Modifier.size(200.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Brand Name ─────────────────────────────────────────────────
            Text(
                text = "CONSENSIUS",
                fontFamily = GraffitiFont,
                fontSize = 34.sp,
                color = Color.White,
                letterSpacing = 2.sp
            )
            Text(
                text = "CONTROLLER",
                fontSize = 11.sp,
                color = NeoColors.Cyan,
                letterSpacing = 5.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(48.dp))

            // ── Glassmorphism progress bar ─────────────────────────────────
            Box(
                modifier = Modifier
                    .width(220.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(NeoColors.ElectricBlue.copy(alpha = 0.08f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = progress
                            scaleY = 1f
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    NeoColors.ElectricBlue,
                                    NeoColors.Amethyst,
                                    NeoColors.Cyan
                                )
                            )
                        )
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Status text ────────────────────────────────────────────────
            val dots = ".".repeat((dotPhase.toInt() % 3) + 1)
            Text(
                text = "$statusText$dots",
                fontSize = 12.sp,
                color = NeoColors.TextSecondary,
                letterSpacing = 0.5.sp
            )
        }
    }
}
