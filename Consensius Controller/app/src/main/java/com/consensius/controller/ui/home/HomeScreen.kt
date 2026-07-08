package com.consensius.controller.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.consensius.controller.datastore.SettingsDataStore
import com.consensius.controller.network.ConnectionState
import com.consensius.controller.network.WebSocketManager
import com.consensius.controller.ui.components.GlassCard
import com.consensius.controller.ui.components.MetricLabel
import com.consensius.controller.ui.components.PremiumCard
import com.consensius.controller.ui.components.SectionHeader
import com.consensius.controller.ui.components.ValueText
import com.consensius.controller.ui.theme.GraffitiFont
import com.consensius.controller.ui.theme.NeoColors
import com.consensius.controller.ui.theme.drawAmbientParticles

@Composable
fun HomeScreen(
    webSocketManager: WebSocketManager,
    settingsDataStore: SettingsDataStore,
    onNavigateToConnection: () -> Unit,
    onNavigateToProfiles: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToController: () -> Unit
) {
    val connectionState by webSocketManager.connectionState.collectAsState()
    val isConnected = connectionState is ConnectionState.Connected
    val isConnecting = connectionState is ConnectionState.Connecting
    val profiles by settingsDataStore.profilesFlow.collectAsState(initial = emptyList())
    val selectedProfileId by settingsDataStore.selectedProfileIdFlow.collectAsState(initial = "")
    val activeProfile = profiles.firstOrNull { it.id == selectedProfileId } ?: profiles.firstOrNull()
    val serverIp by settingsDataStore.serverIpFlow.collectAsState(initial = "–")
    val serverPort by settingsDataStore.serverPortFlow.collectAsState(initial = 8765)

    val infiniteTransition = rememberInfiniteTransition(label = "home_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NeoColors.Background)
    ) {
        // ── Ambient particles background ───────────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawAmbientParticles(
                particleCount = 6,
                primaryColor = NeoColors.ElectricBlue.copy(alpha = 0.04f),
                secondaryColor = NeoColors.Amethyst.copy(alpha = 0.03f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))

            // ── Top Bar ────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(NeoColors.GlassElevated, NeoColors.SurfaceLight)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(com.consensius.controller.R.drawable.logo_consensius_full),
                            contentDescription = "Logo",
                            modifier = Modifier.size(38.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Consensius",
                            fontFamily = GraffitiFont,
                            color = Color.White,
                            fontSize = 19.sp,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "CONTROLLER",
                            color = NeoColors.ElectricBlue,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 9.sp,
                            letterSpacing = 3.sp
                        )
                    }
                }
                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = NeoColors.TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Connection Status Glass Card ───────────────────────────────
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Status dot with pulse ring
                            Box(contentAlignment = Alignment.Center) {
                                val dotColor = when {
                                    isConnected -> NeoColors.Success
                                    isConnecting -> NeoColors.ElectricBlue
                                    else -> NeoColors.Error
                                }
                                if (isConnected) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .scale(pulseScale)
                                            .background(dotColor.copy(alpha = 0.15f), CircleShape)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(dotColor, CircleShape)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                val statusLabel = when {
                                    isConnected -> "Connected"
                                    isConnecting -> "Connecting…"
                                    connectionState is ConnectionState.Error ->
                                        "Error: ${(connectionState as ConnectionState.Error).message}"
                                    else -> "Disconnected"
                                }
                                Text(
                                    text = statusLabel,
                                    color = when {
                                        isConnected -> NeoColors.Success
                                        isConnecting -> NeoColors.ElectricBlue
                                        else -> NeoColors.Error
                                    },
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (isConnected) "$serverIp:$serverPort" else "Tap to configure",
                                    color = NeoColors.TextTertiary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Connection icon
                        Icon(
                            imageVector = if (isConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = if (isConnected) NeoColors.Success else NeoColors.TextMuted,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Metrics Row ────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricTile(
                    label = "Active Profile",
                    value = activeProfile?.name ?: "None",
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "Pages",
                    value = "${activeProfile?.pages?.size ?: 0}",
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "Elements",
                    value = "${activeProfile?.pages?.sumOf { it.elements.size } ?: 0}",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(20.dp))

            SectionHeader(text = "QUICK ACTIONS", fontSize = 11.sp)

            Spacer(Modifier.height(10.dp))

            // ── Action Grid ────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ActionGlassTile(
                    icon = Icons.Default.Lan,
                    label = "Connection",
                    subtitle = "Configure server",
                    onClick = onNavigateToConnection,
                    modifier = Modifier.weight(1f)
                )
                ActionGlassTile(
                    icon = Icons.Default.VideogameAsset,
                    label = "Profiles",
                    subtitle = "${profiles.size} saved",
                    onClick = onNavigateToProfiles,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Start Controller Button ────────────────────────────────────
            Button(
                onClick = onNavigateToController,
                enabled = isConnected,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeoColors.ElectricBlue,
                    disabledContainerColor = NeoColors.SurfaceLight
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = if (isConnected) 8.dp else 0.dp
                )
            ) {
                Icon(
                    Icons.Default.Gamepad,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = when {
                        isConnected -> "Launch Controller"
                        isConnecting -> "Connecting…"
                        else -> "Connect to Start"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    letterSpacing = 0.5.sp,
                    color = if (isConnected) Color.White else NeoColors.TextTertiary
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (isConnected) Color.White else NeoColors.TextTertiary
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    PremiumCard(
        modifier = modifier,
        backgroundColor = NeoColors.GlassCard,
        borderColor = NeoColors.GlassBorder
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            MetricLabel(text = label)
            Spacer(Modifier.height(6.dp))
            ValueText(
                text = value,
                fontSize = 16.sp,
                color = NeoColors.TextPrimary
            )
        }
    }
}

@Composable
private fun ActionGlassTile(
    icon: ImageVector,
    label: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    NeoColors.ElectricBlue.copy(alpha = 0.15f),
                                    NeoColors.Amethyst.copy(alpha = 0.1f)
                                )
                            ),
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = label,
                        tint = NeoColors.ElectricBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = label,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = NeoColors.TextTertiary,
                    fontSize = 11.sp
                )
            }
        }
    }
}
