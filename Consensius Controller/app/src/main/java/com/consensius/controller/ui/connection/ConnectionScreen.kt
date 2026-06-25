package com.consensius.controller.ui.connection

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.consensius.controller.datastore.SettingsDataStore
import com.consensius.controller.network.ConnectionState
import com.consensius.controller.network.WebSocketManager
import com.consensius.controller.ui.theme.ConsensiusColors
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@Composable
fun ConnectionScreen(
    settingsDataStore: SettingsDataStore,
    webSocketManager: WebSocketManager,
    onNavigateBack: () -> Unit
) {
    val coroutineScope  = rememberCoroutineScope()
    val connectionState by webSocketManager.connectionState.collectAsState()
    val isConnected = connectionState is ConnectionState.Connected
    val isConnecting = connectionState is ConnectionState.Connecting

    var ipAddress by remember { mutableStateOf("") }
    var port      by remember { mutableStateOf("8765") }
    var showQrScanner by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        ipAddress = settingsDataStore.serverIpFlow.first()
        port      = settingsDataStore.serverPortFlow.first().toString()
    }

    // Pulsing status dot
    val infiniteTransition = rememberInfiniteTransition(label = "conn_anim")
    val dotScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue  = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ConsensiusColors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Top bar ───────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = ConsensiusColors.TextPrimary)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "CONNECTION",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    letterSpacing = 2.sp
                )
            }

            // ── Status indicator ──────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(ConsensiusColors.Card)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                val dotColor = when (connectionState) {
                    is ConnectionState.Connected  -> ConsensiusColors.Success
                    is ConnectionState.Connecting -> ConsensiusColors.Accent
                    is ConnectionState.Error      -> ConsensiusColors.Error
                    else                          -> ConsensiusColors.TextSecondary
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .scale(if (isConnecting || isConnected) dotScale else 1f)
                        .background(dotColor, CircleShape)
                )
                Spacer(Modifier.width(10.dp))
                val statusLabel = when (connectionState) {
                    is ConnectionState.Connected  -> "Connected"
                    is ConnectionState.Connecting -> "Connecting…"
                    is ConnectionState.Error      -> "Error: ${(connectionState as ConnectionState.Error).message}"
                    else                          -> "Disconnected"
                }
                Text(statusLabel, color = dotColor, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }

            // ── Section A: Manual Connection ──────────────────────────────────
            SectionCard(title = "Manual Connection") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StyledTextField(
                        value = ipAddress,
                        onValueChange = { ipAddress = it },
                        label = "IP Address",
                        modifier = Modifier.weight(2f),
                        keyboardType = KeyboardType.Uri
                    )
                    StyledTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = "Port",
                        modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Number
                    )
                }

                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val portInt = port.toIntOrNull() ?: 8765
                                settingsDataStore.saveConnectionSettings(ipAddress, portInt)
                                webSocketManager.connect(ipAddress, portInt)
                            }
                        },
                        enabled = !isConnected && !isConnecting,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ConsensiusColors.Accent,
                            disabledContainerColor = ConsensiusColors.Card
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("CONNECT", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                    Button(
                        onClick = { webSocketManager.disconnect() },
                        enabled = isConnected || isConnecting,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ConsensiusColors.Error,
                            disabledContainerColor = ConsensiusColors.Card
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("DISCONNECT", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
            }

            // ── Section B: QR Code Scanner ────────────────────────────────────
            SectionCard(title = "QR Code Scanner") {
                Text(
                    text = "Scan a QR code in the format: consensius://[IP]:[PORT]",
                    color = ConsensiusColors.TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))

                if (!showQrScanner) {
                    Button(
                        onClick = { showQrScanner = true },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ConsensiusColors.Card),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("📷  OPEN CAMERA", fontWeight = FontWeight.Bold, color = ConsensiusColors.Accent)
                    }
                } else {
                    QrScannerView(
                        onQrDetected = { raw ->
                            // Parse "consensius://IP:PORT"
                            val content = raw.removePrefix("consensius://")
                            val parts = content.split(":")
                            if (parts.size == 2) {
                                ipAddress = parts[0]
                                port      = parts[1]
                            }
                            showQrScanner = false
                        },
                        onClose = { showQrScanner = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            }
        }
    }
}

// ─── Reusable card section ────────────────────────────────────────────────────
@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, ConsensiusColors.CardBorder, RoundedCornerShape(12.dp))
            .background(ConsensiusColors.Card)
            .padding(16.dp)
    ) {
        Text(
            text = title.uppercase(),
            color = ConsensiusColors.Accent,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.5.sp
        )
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 10.dp),
            color = ConsensiusColors.CardBorder
        )
        content()
    }
}

// ─── Styled text field ────────────────────────────────────────────────────────
@Composable
private fun StyledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = ConsensiusColors.TextSecondary, fontSize = 12.sp) },
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = ConsensiusColors.Accent,
            unfocusedBorderColor = ConsensiusColors.CardBorder,
            focusedTextColor     = ConsensiusColors.TextPrimary,
            unfocusedTextColor   = ConsensiusColors.TextPrimary,
            cursorColor          = ConsensiusColors.Accent,
            focusedContainerColor   = ConsensiusColors.Card,
            unfocusedContainerColor = ConsensiusColors.Card
        )
    )
}

// ─── QR Scanner ───────────────────────────────────────────────────────────────
@Composable
private fun QrScannerView(
    onQrDetected: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context       = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor       = remember { Executors.newSingleThreadExecutor() }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = modifier) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val provider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val scanner = BarcodeScanning.getClient()
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analysis.setAnalyzer(executor) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
                                val image = InputImage.fromMediaImage(
                                    mediaImage, imageProxy.imageInfo.rotationDegrees
                                )
                                scanner.process(image)
                                    .addOnSuccessListener { barcodes ->
                                        barcodes.firstOrNull()?.rawValue?.let { raw ->
                                            if (raw.startsWith("consensius://")) {
                                                onQrDetected(raw)
                                            }
                                        }
                                    }
                                    .addOnCompleteListener { imageProxy.close() }
                            } else {
                                imageProxy.close()
                            }
                        }
                        try {
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis
                            )
                        } catch (e: Exception) { /* ignore */ }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(ConsensiusColors.Card),
                contentAlignment = Alignment.Center
            ) {
                Text("Camera permission required", color = ConsensiusColors.TextSecondary)
            }
        }

        // Close button
        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(ConsensiusColors.Background.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}
