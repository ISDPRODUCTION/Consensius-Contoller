package com.consensius.controller.ui.settings

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.consensius.controller.datastore.SettingsDataStore
import com.consensius.controller.ui.theme.ConsensiusColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settingsDataStore: SettingsDataStore,
    onNavigateBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var deadzone         by remember { mutableFloatStateOf(0.1f) }
    var sensitivity      by remember { mutableFloatStateOf(1.0f) }
    var mouseSensitivity by remember { mutableFloatStateOf(1.5f) }
    var sendFps          by remember { mutableIntStateOf(60) }
    var darkMode         by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        deadzone         = settingsDataStore.deadzoneFlow.first()
        sensitivity      = settingsDataStore.sensitivityFlow.first()
        mouseSensitivity = settingsDataStore.mouseSensitivityFlow.first()
        sendFps          = settingsDataStore.sendFpsFlow.first()
        darkMode         = settingsDataStore.darkModeFlow.first()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ConsensiusColors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // ── Top bar ───────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = ConsensiusColors.TextPrimary)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "SETTINGS",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    letterSpacing = 2.sp
                )
            }

            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Controller Section ────────────────────────────────────────
                SettingsSection(title = "CONTROLLER") {
                    SliderRow(
                        label = "Left Joystick Deadzone",
                        value = deadzone,
                        range = 0f..0.5f,
                        format = "%.2f",
                        onValueChange = { deadzone = it }
                    )
                    SliderRow(
                        label = "Left Joystick Sensitivity",
                        value = sensitivity,
                        range = 0.5f..2.0f,
                        format = "%.1f×",
                        onValueChange = { sensitivity = it }
                    )
                    SliderRow(
                        label = "Mouse Sensitivity (Right Stick)",
                        value = mouseSensitivity,
                        range = 0.5f..5.0f,
                        format = "%.1f×",
                        onValueChange = { mouseSensitivity = it }
                    )
                    SliderRow(
                        label = "Input Send Rate",
                        value = sendFps.toFloat(),
                        range = 10f..120f,
                        format = "%.0f FPS",
                        steps = 110,
                        onValueChange = { sendFps = it.roundToInt() }
                    )
                }

                // ── Display Section ───────────────────────────────────────────
                SettingsSection(title = "DISPLAY") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Dark Mode", color = ConsensiusColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Theme preference", color = ConsensiusColors.TextSecondary, fontSize = 11.sp)
                        }
                        Switch(
                            checked = darkMode,
                            onCheckedChange = { darkMode = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ConsensiusColors.Accent,
                                uncheckedTrackColor = ConsensiusColors.Card
                            )
                        )
                    }
                }

                // ── About Section ─────────────────────────────────────────────
                SettingsSection(title = "ABOUT") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Application", color = ConsensiusColors.TextPrimary, fontSize = 14.sp)
                        Text("Consensius Controller v1.0", color = ConsensiusColors.TextSecondary, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Protocol", color = ConsensiusColors.TextPrimary, fontSize = 14.sp)
                        Text("WebSocket (ws://)", color = ConsensiusColors.TextSecondary, fontSize = 13.sp)
                    }
                }
            }

            // ── Save button ───────────────────────────────────────────────────
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    coroutineScope.launch {
                        settingsDataStore.saveSettings(deadzone, sensitivity, mouseSensitivity, sendFps, darkMode)
                        onNavigateBack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ConsensiusColors.Accent)
            ) {
                Text(
                    "SAVE SETTINGS",
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, ConsensiusColors.CardBorder, RoundedCornerShape(12.dp))
            .background(ConsensiusColors.Card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            color = ConsensiusColors.Accent,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 1.5.sp
        )
        HorizontalDivider(color = ConsensiusColors.CardBorder)
        content()
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: String,
    steps: Int = 0,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = ConsensiusColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(String.format(format, value), color = ConsensiusColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor       = ConsensiusColors.Accent,
                activeTrackColor = ConsensiusColors.Accent,
                inactiveTrackColor = ConsensiusColors.CardBorder
            )
        )
    }
}
