package com.consensius.controller.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.consensius.controller.datastore.SettingsDataStore
import com.consensius.controller.network.ConnectionState
import com.consensius.controller.network.WebSocketManager
import com.consensius.controller.ui.theme.ConsensiusColors

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
    val profiles by settingsDataStore.profilesFlow.collectAsState(initial = emptyList())
    val selectedProfileId by settingsDataStore.selectedProfileIdFlow.collectAsState(initial = "")
    val activeProfile = profiles.firstOrNull { it.id == selectedProfileId } ?: profiles.firstOrNull()
    val serverIp by settingsDataStore.serverIpFlow.collectAsState(initial = "–")
    val serverPort by settingsDataStore.serverPortFlow.collectAsState(initial = 8765)

    // Pulsing animation for the status dot
    val infiniteTransition = rememberInfiniteTransition(label = "home_anim")
    val dotScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue  = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotPulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ConsensiusColors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ── Top Bar ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Logo
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        ConsensiusColors.Accent,
                                        ConsensiusColors.AccentSecondary.copy(alpha = 0.7f)
                                    )
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("CC", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "CONSENSIUS",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "CONTROLLER",
                            color = ConsensiusColors.Accent,
                            fontWeight = FontWeight.Light,
                            fontSize = 11.sp,
                            letterSpacing = 2.sp
                        )
                    }
                }
                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = ConsensiusColors.TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // ── Status Card ───────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        width = 1.dp,
                        color = if (isConnected) ConsensiusColors.Success.copy(alpha = 0.4f)
                                else ConsensiusColors.Error.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .background(ConsensiusColors.Card)
                    .padding(20.dp)
            ) {
                Column {
                    // Status row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Pulsing dot
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .scale(if (isConnected) dotScale else 1f)
                                .background(
                                    color = if (isConnected) ConsensiusColors.Success else ConsensiusColors.Error,
                                    shape = CircleShape
                                )
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = if (isConnected) "CONNECTED" else
                                   if (connectionState is ConnectionState.Connecting) "CONNECTING…"
                                   else "DISCONNECTED",
                            color = if (isConnected) ConsensiusColors.Success else
                                    if (connectionState is ConnectionState.Connecting) ConsensiusColors.Accent
                                    else ConsensiusColors.Error,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // Info grid
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        InfoItem(label = "Profile", value = activeProfile?.name ?: "None")
                        InfoItem(label = "Server",  value = "$serverIp:$serverPort")
                        InfoItem(label = "Pages",   value = "${activeProfile?.pages?.size ?: 0}")
                    }
                }
            }

            // ── Bottom Nav Row ────────────────────────────────────────────────
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    NavButton(icon = Icons.Default.Share, label = "Connect", onClick = onNavigateToConnection)
                    Spacer(Modifier.width(12.dp))
                    NavButton(icon = Icons.Default.AccountCircle, label = "Profiles", onClick = onNavigateToProfiles)
                    Spacer(Modifier.width(12.dp))
                    NavButton(icon = Icons.Default.Settings, label = "Settings", onClick = onNavigateToSettings)
                }

                Spacer(Modifier.height(16.dp))

                // Big START CONTROLLER button
                Button(
                    onClick = onNavigateToController,
                    enabled = isConnected,
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ConsensiusColors.Accent,
                        disabledContainerColor = ConsensiusColors.Card
                    )
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isConnected) "START CONTROLLER" else "CONNECT FIRST",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        letterSpacing = 1.sp,
                        color = if (isConnected) Color.White else ConsensiusColors.TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = ConsensiusColors.TextSecondary, fontSize = 10.sp, letterSpacing = 0.5.sp)
        Spacer(Modifier.height(2.dp))
        Text(text = value, color = ConsensiusColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
private fun NavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ConsensiusColors.Card),
        modifier = Modifier.width(110.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp), tint = ConsensiusColors.Accent)
            Spacer(Modifier.height(2.dp))
            Text(label, fontSize = 10.sp, color = ConsensiusColors.TextPrimary)
        }
    }
}
