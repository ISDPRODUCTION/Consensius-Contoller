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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import com.consensius.controller.ui.components.GlassCard
import com.consensius.controller.ui.components.SectionHeader
import com.consensius.controller.ui.theme.NeoColors
import com.consensius.controller.ui.theme.drawAmbientParticles
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
    val coroutineScope = rememberCoroutineScope()
    val connectionState by webSocketManager.connectionState.collectAsState()
    val isConnected = connectionState is ConnectionState.Connected
    val isConnecting = connectionState is ConnectionState.Connecting

    var ipAddress by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8765") }
    var showQrScanner by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        ipAddress = settingsDataStore.serverIpFlow.first()
        port = settingsDataStore.serverPortFlow.first().toString()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "conn_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NeoColors.Background)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawAmbientParticles(
                particleCount = 5,
                primaryColor = NeoColors.ElectricBlue.copy(alpha = 0.04f),
                secondaryColor = NeoColors.Amethyst.copy(alpha = 0.03f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // ── Top Bar ─────────────────────────────────────────────────────
            Row(
                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
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
                    text = "Connection",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ── Status Indicator ────────────────────────────────────────
                val dotColor = when (connectionState) {
                    is ConnectionState.Connected -> NeoColors.Success
                    is ConnectionState.Connecting -> NeoColors.ElectricBlue
                    is ConnectionState.Error -> NeoColors.Error
                    else -> NeoColors.TextTertiary
                }
                val statusLabel = when (connectionState) {
                    is ConnectionState.Connected -> "Connected"
                    is ConnectionState.Connecting -> "Connecting…"
                    is ConnectionState.Error -> "Error: ${(connectionState as ConnectionState.Error).message}"
                    else -> "Disconnected"
                }

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isConnected || isConnecting) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .scale(pulseScale)
                                            .background(dotColor.copy(alpha = 0.12f), CircleShape)
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
                                Text(
                                    statusLabel,
                                    color = dotColor,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                if (isConnected) {
                                    Text(
                                        "$ipAddress:$port",
                                        color = NeoColors.TextTertiary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                        Icon(
                            imageVector = if (isConnected) Icons.Default.Link else Icons.Default.LinkOff,
                            contentDescription = null,
                            tint = if (isConnected) NeoColors.Success else NeoColors.TextMuted,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // ── Manual Connection ───────────────────────────────────────
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SectionHeader(text = "MANUAL CONNECTION")
                        Spacer(Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        val portInt = port.toIntOrNull() ?: 8765
                                        settingsDataStore.saveConnectionSettings(ipAddress, portInt)
                                        webSocketManager.connect(ipAddress, portInt)
                                    }
                                },
                                enabled = !isConnected && !isConnecting,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeoColors.ElectricBlue,
                                    disabledContainerColor = NeoColors.SurfaceLight
                                ),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Connect", fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                            }
                            Button(
                                onClick = { webSocketManager.disconnect() },
                                enabled = isConnected || isConnecting,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeoColors.Error,
                                    disabledContainerColor = NeoColors.SurfaceLight
                                ),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Disconnect", fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                            }
                        }
                    }
                }

                // ── QR Scanner ──────────────────────────────────────────────
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SectionHeader(text = "QR SCANNER")
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Scan a QR code in the format: consensius://[IP]:[PORT]",
                            color = NeoColors.TextTertiary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                        Spacer(Modifier.height(12.dp))

                        if (!showQrScanner) {
                            Button(
                                onClick = { showQrScanner = true },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeoColors.ElectricBlue.copy(alpha = 0.1f)
                                ),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Icon(
                                    Icons.Default.QrCode,
                                    contentDescription = null,
                                    tint = NeoColors.ElectricBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Open Camera Scanner",
                                    fontWeight = FontWeight.Bold,
                                    color = NeoColors.ElectricBlue
                                )
                            }
                        } else {
                            QrScannerView(
                                onQrDetected = { raw ->
                                    val content = raw.removePrefix("consensius://")
                                    val parts = content.split(":")
                                    if (parts.size == 2) {
                                        ipAddress = parts[0]
                                        port = parts[1]
                                    }
                                    showQrScanner = false
                                },
                                onClose = { showQrScanner = false },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .clip(RoundedCornerShape(16.dp))
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─── Styled Text Field ────────────────────────────────────────────────────────
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
        label = { Text(label, color = NeoColors.TextTertiary, fontSize = 12.sp) },
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = NeoColors.ElectricBlue,
            unfocusedBorderColor = NeoColors.GlassBorder,
            focusedTextColor     = NeoColors.TextPrimary,
            unfocusedTextColor   = NeoColors.TextPrimary,
            cursorColor          = NeoColors.ElectricBlue,
            focusedContainerColor   = NeoColors.Surface,
            unfocusedContainerColor = NeoColors.Surface
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
                modifier = Modifier.fillMaxSize().background(NeoColors.Surface),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = NeoColors.TextTertiary, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Camera permission required", color = NeoColors.TextSecondary)
                }
            }
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(NeoColors.Background.copy(alpha = 0.75f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
