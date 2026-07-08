package com.consensius.controller.ui.settings

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.consensius.controller.datastore.SettingsDataStore
import com.consensius.controller.ui.components.GlassCard
import com.consensius.controller.ui.components.GlassDivider
import com.consensius.controller.ui.components.MetricLabel
import com.consensius.controller.ui.components.SectionHeader
import com.consensius.controller.ui.theme.NeoColors
import com.consensius.controller.ui.theme.drawAmbientParticles
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settingsDataStore: SettingsDataStore,
    onNavigateBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var deadzone by remember { mutableFloatStateOf(0.1f) }
    var sensitivity by remember { mutableFloatStateOf(1.0f) }
    var mouseSensitivity by remember { mutableFloatStateOf(1.5f) }
    var sendFps by remember { mutableIntStateOf(60) }

    LaunchedEffect(Unit) {
        deadzone = settingsDataStore.deadzoneFlow.first()
        sensitivity = settingsDataStore.sensitivityFlow.first()
        mouseSensitivity = settingsDataStore.mouseSensitivityFlow.first()
        sendFps = settingsDataStore.sendFpsFlow.first()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(NeoColors.Background)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawAmbientParticles(
                particleCount = 5,
                primaryColor = NeoColors.ElectricBlue.copy(alpha = 0.03f),
                secondaryColor = NeoColors.Amethyst.copy(alpha = 0.02f)
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top Bar ─────────────────────────────────────────────────────
            Row(
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = NeoColors.TextPrimary
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    "Settings",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Controller Settings ─────────────────────────────────────
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SectionHeader(text = "CONTROLLER")
                        Spacer(Modifier.height(16.dp))

                        // Deadzone
                        SettingSliderRow(
                            icon = Icons.Default.Sensors,
                            label = "Left Joystick Deadzone",
                            value = deadzone,
                            format = "%.2f",
                            valueRange = 0f..0.5f,
                            onValueChange = { deadzone = it }
                        )

                        Spacer(Modifier.height(4.dp))
                        GlassDivider()
                        Spacer(Modifier.height(4.dp))

                        // Sensitivity
                        SettingSliderRow(
                            icon = Icons.Default.Speed,
                            label = "Left Joystick Sensitivity",
                            value = sensitivity,
                            format = "%.1f×",
                            valueRange = 0.5f..2.0f,
                            onValueChange = { sensitivity = it }
                        )

                        Spacer(Modifier.height(4.dp))
                        GlassDivider()
                        Spacer(Modifier.height(4.dp))

                        // Mouse Sensitivity
                        SettingSliderRow(
                            icon = Icons.Default.Mouse,
                            label = "Mouse Sensitivity",
                            value = mouseSensitivity,
                            format = "%.1f×",
                            valueRange = 0.5f..5.0f,
                            onValueChange = { mouseSensitivity = it }
                        )

                        Spacer(Modifier.height(4.dp))
                        GlassDivider()
                        Spacer(Modifier.height(4.dp))

                        // FPS
                        SettingSliderRow(
                            icon = Icons.Default.Speed,
                            label = "Input Send Rate",
                            value = sendFps.toFloat(),
                            format = "%.0f FPS",
                            valueRange = 10f..120f,
                            onValueChange = { sendFps = it.roundToInt() }
                        )
                    }
                }

                // ── About Section ───────────────────────────────────────────
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SectionHeader(text = "ABOUT")
                        Spacer(Modifier.height(16.dp))

                        AboutItem(icon = Icons.Default.Gamepad, label = "Application", value = "Consensius Controller v1.0")
                        Spacer(Modifier.height(4.dp))
                        GlassDivider()
                        Spacer(Modifier.height(4.dp))
                        AboutItem(icon = Icons.Default.Info, label = "Protocol", value = "WebSocket (ws://)")
                    }
                }
            }

            // ── Save Button ─────────────────────────────────────────────────
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            settingsDataStore.saveSettings(deadzone, sensitivity, mouseSensitivity, sendFps)
                            onNavigateBack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeoColors.ElectricBlue),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Save Settings",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSliderRow(
    icon: ImageVector,
    label: String,
    value: Float,
    format: String,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(NeoColors.ElectricBlue.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = NeoColors.ElectricBlue,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    label,
                    color = NeoColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                String.format(format, value),
                color = NeoColors.ElectricBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(2.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = NeoColors.ElectricBlue,
                activeTrackColor = NeoColors.ElectricBlue,
                inactiveTrackColor = NeoColors.GlassBorder
            )
        )
    }
}

@Composable
private fun AboutItem(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(NeoColors.TextMuted.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = NeoColors.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(label, color = NeoColors.TextPrimary, fontSize = 14.sp)
        }
        Text(value, color = NeoColors.TextSecondary, fontSize = 13.sp)
    }
}
