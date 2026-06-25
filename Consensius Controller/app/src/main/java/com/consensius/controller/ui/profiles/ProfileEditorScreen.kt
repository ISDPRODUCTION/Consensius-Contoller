package com.consensius.controller.ui.profiles

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.consensius.controller.datastore.SettingsDataStore
import com.consensius.controller.model.ButtonElementConfig
import com.consensius.controller.model.CanvasElement
import com.consensius.controller.model.ControllerProfile
import com.consensius.controller.model.DpadElementConfig
import com.consensius.controller.model.ElementSize
import com.consensius.controller.model.ElementType
import com.consensius.controller.model.JoystickElementConfig
import com.consensius.controller.model.JoystickType
import com.consensius.controller.model.ProfilePage
import com.consensius.controller.model.TouchpadElementConfig
import com.consensius.controller.model.toDp
import com.consensius.controller.ui.theme.ConsensiusColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(
    profileId: String?,
    settingsDataStore: SettingsDataStore,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Force landscape orientation
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val original = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = original ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val isNew = profileId == "new" || profileId.isNullOrBlank()

    // ── Profile state ─────────────────────────────────────────────────────────
    var profileName by remember { mutableStateOf(if (isNew) "" else "Loading…") }
    var editingId   by remember { mutableStateOf(if (isNew) UUID.randomUUID().toString() else profileId ?: UUID.randomUUID().toString()) }
    val pages       = remember { mutableStateListOf<ProfilePage>() }
    var currentPage by remember { mutableIntStateOf(0) }

    // ── UI state ──────────────────────────────────────────────────────────────
    var menuExpanded        by remember { mutableStateOf(false) }
    var fabExpanded         by remember { mutableStateOf(false) }
    var settingsElement     by remember { mutableStateOf<CanvasElement?>(null) }
    var showSaveAsDialog    by remember { mutableStateOf(false) }
    var saveAsName          by remember { mutableStateOf("") }

    // Canvas size in px
    var canvasWidthPx  by remember { mutableFloatStateOf(1f) }
    var canvasHeightPx by remember { mutableFloatStateOf(1f) }

    // ── Load existing profile ─────────────────────────────────────────────────
    LaunchedEffect(profileId) {
        if (!isNew && profileId != null) {
            val profiles = settingsDataStore.profilesFlow.first()
            profiles.firstOrNull { it.id == profileId }?.let { p ->
                profileName = p.name
                editingId   = p.id
                pages.clear()
                pages.addAll(p.pages.map { page ->
                    page.copy(elements = page.elements.toMutableList())
                })
            }
        }
        if (pages.isEmpty()) {
            pages.add(ProfilePage(name = "Page 1"))
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    fun currentElements(): List<CanvasElement> =
        pages.getOrNull(currentPage)?.elements ?: emptyList()

    fun updateElement(updated: CanvasElement) {
        val pageIdx = currentPage
        if (pageIdx < 0 || pageIdx >= pages.size) return
        val elIdx = pages[pageIdx].elements.indexOfFirst { it.id == updated.id }
        if (elIdx < 0) return
        val newElems = pages[pageIdx].elements.toMutableList().also { it[elIdx] = updated }
        pages[pageIdx] = pages[pageIdx].copy(elements = newElems)
    }

    fun addElement(el: CanvasElement) {
        val pageIdx = currentPage
        if (pageIdx < 0 || pageIdx >= pages.size) return
        val newElems = pages[pageIdx].elements + el
        pages[pageIdx] = pages[pageIdx].copy(elements = newElems)
    }

    fun deleteElement(id: String) {
        val pageIdx = currentPage
        if (pageIdx < 0 || pageIdx >= pages.size) return
        val newElems = pages[pageIdx].elements.filter { it.id != id }
        pages[pageIdx] = pages[pageIdx].copy(elements = newElems)
    }

    fun saveProfile(name: String, newId: String = editingId) {
        coroutineScope.launch {
            val profile = ControllerProfile(
                id    = newId,
                name  = name.ifBlank { "Untitled" },
                pages = pages.toList()
            )
            settingsDataStore.saveProfile(profile)
        }
    }

    // ── Bottom sheet state ────────────────────────────────────────────────────
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // ─────────────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ConsensiusColors.Background)
    ) {

        // ── Dot-grid canvas ───────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    canvasWidthPx  = coords.size.width.toFloat()
                    canvasHeightPx = coords.size.height.toFloat()
                }
        ) {
            // Dot grid background
            Canvas(modifier = Modifier.fillMaxSize()) {
                val spacing = 28.dp.toPx()
                val dotR    = 1.5f
                val dotColor = Color(0xFF00C2FF).copy(alpha = 0.12f)
                var x = spacing
                while (x < size.width) {
                    var y = spacing
                    while (y < size.height) {
                        drawCircle(dotColor, dotR, Offset(x, y))
                        y += spacing
                    }
                    x += spacing
                }
            }

            // Render elements on current page
            currentElements().forEach { el ->
                val sizeDp = el.size.toDp().dp
                val sizePx = with(density) { sizeDp.toPx() }
                val initX  = (el.x * canvasWidthPx  - sizePx / 2).coerceAtLeast(0f)
                val initY  = (el.y * canvasHeightPx - sizePx / 2).coerceAtLeast(0f)

                var offsetX by remember(el.id) { mutableFloatStateOf(initX) }
                var offsetY by remember(el.id) { mutableFloatStateOf(initY) }

                // Sync position when canvasSize changes
                LaunchedEffect(canvasWidthPx, canvasHeightPx, el.id) {
                    offsetX = (el.x * canvasWidthPx  - sizePx / 2).coerceAtLeast(0f)
                    offsetY = (el.y * canvasHeightPx - sizePx / 2).coerceAtLeast(0f)
                }

                Box(
                    modifier = Modifier
                        .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                        .then(
                            if (el.type == ElementType.TOUCHPAD)
                                Modifier
                                    .width((sizeDp * 2.5f))
                                    .height(sizeDp)
                            else
                                Modifier.size(sizeDp)
                        )
                        .pointerInput(el.id + "_drag") {
                            detectDragGestures { _, delta ->
                                val w = if (el.type == ElementType.TOUCHPAD) sizePx * 2.5f else sizePx
                                val newX = (offsetX + delta.x).coerceIn(0f, canvasWidthPx - w)
                                val newY = (offsetY + delta.y).coerceIn(0f, canvasHeightPx - sizePx)
                                offsetX = newX
                                offsetY = newY
                                updateElement(
                                    el.copy(
                                        x = (newX + w / 2) / canvasWidthPx,
                                        y = (newY + sizePx / 2) / canvasHeightPx
                                    )
                                )
                            }
                        }
                ) {
                    CanvasElementWidget(
                        element = el,
                        onSettingsTap = { settingsElement = el }
                    )
                }
            }
        }

        // ── TOP BAR ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsensiusColors.Background.copy(alpha = 0.85f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile name text field
            OutlinedTextField(
                value = profileName,
                onValueChange = { profileName = it },
                placeholder = { Text("Profile name…", color = ConsensiusColors.TextSecondary, fontSize = 14.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f).height(52.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor    = ConsensiusColors.Accent,
                    unfocusedBorderColor  = ConsensiusColors.CardBorder,
                    focusedContainerColor = ConsensiusColors.Surface.copy(alpha = 0.7f),
                    unfocusedContainerColor = ConsensiusColors.Surface.copy(alpha = 0.5f),
                    cursorColor = ConsensiusColors.Accent
                )
            )

            Spacer(Modifier.width(8.dp))

            // 3-dot menu
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = Color.White)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(ConsensiusColors.Card)
                ) {
                    DropdownMenuItem(
                        text = { Text("Save", color = ConsensiusColors.TextPrimary) },
                        onClick = {
                            menuExpanded = false
                            saveProfile(profileName)
                            onNavigateBack()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Save As…", color = ConsensiusColors.TextPrimary) },
                        onClick = {
                            menuExpanded = false
                            saveAsName = profileName
                            showSaveAsDialog = true
                        }
                    )
                    HorizontalDivider(color = ConsensiusColors.CardBorder)
                    DropdownMenuItem(
                        text = { Text("Exit without saving", color = ConsensiusColors.Error) },
                        onClick = {
                            menuExpanded = false
                            onNavigateBack()
                        }
                    )
                }
            }
        }

        // ── PAGE INDICATOR ────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            pages.forEachIndexed { index, page ->
                val isActive = index == currentPage
                val dotSize by animateDpAsState(if (isActive) 10.dp else 6.dp, label = "dot")
                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .background(
                            if (isActive) ConsensiusColors.Accent else ConsensiusColors.TextSecondary,
                            CircleShape
                        )
                        .pointerInput(index) {
                            detectTapGestures { currentPage = index }
                        }
                )
            }
        }

        // ── FAB (bottom right) ────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Expanded FAB sub-options
            AnimatedVisibility(
                visible = fabExpanded,
                enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
                exit  = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut()
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FabSubItem("Add Page") {
                        fabExpanded = false
                        val newPageNum = pages.size + 1
                        pages.add(ProfilePage(name = "Page $newPageNum"))
                        currentPage = pages.size - 1
                    }
                    FabSubItem("Add Touchpad") {
                        fabExpanded = false
                        addElement(CanvasElement(
                            type = ElementType.TOUCHPAD, label = "TP",
                            x = 0.5f, y = 0.5f, size = ElementSize.L
                        ))
                    }
                    FabSubItem("Add D-Pad") {
                        fabExpanded = false
                        addElement(CanvasElement(
                            type = ElementType.DPAD, label = "DPAD",
                            x = 0.2f, y = 0.5f, size = ElementSize.L
                        ))
                    }
                    FabSubItem("Add Joystick") {
                        fabExpanded = false
                        addElement(CanvasElement(
                            type = ElementType.JOYSTICK, label = "JS",
                            x = 0.15f, y = 0.7f, size = ElementSize.L
                        ))
                    }
                    FabSubItem("Add Button") {
                        fabExpanded = false
                        addElement(CanvasElement(
                            type = ElementType.BUTTON, label = "BTN",
                            x = 0.5f, y = 0.5f, size = ElementSize.M
                        ))
                    }
                }
            }

            FloatingActionButton(
                onClick = { fabExpanded = !fabExpanded },
                containerColor = ConsensiusColors.Accent,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add element")
            }
        }
    }

    // ── Element settings bottom sheet ─────────────────────────────────────────
    settingsElement?.let { el ->
        ModalBottomSheet(
            onDismissRequest = { settingsElement = null },
            sheetState = sheetState,
            containerColor = ConsensiusColors.Card,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp, bottom = 4.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .background(ConsensiusColors.CardBorder, CircleShape)
                )
            }
        ) {
            ElementSettingsSheet(
                element = el,
                onUpdate = { updated ->
                    updateElement(updated)
                    settingsElement = updated
                },
                onDelete = {
                    deleteElement(el.id)
                    settingsElement = null
                },
                onDismiss = { settingsElement = null }
            )
        }
    }

    // ── Save As dialog ────────────────────────────────────────────────────────
    if (showSaveAsDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSaveAsDialog = false },
            containerColor = ConsensiusColors.Card,
            title = { Text("Save As", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = saveAsName,
                    onValueChange = { saveAsName = it },
                    label = { Text("New profile name", color = ConsensiusColors.TextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = ConsensiusColors.Accent,
                        unfocusedBorderColor = ConsensiusColors.CardBorder,
                        focusedTextColor     = Color.White,
                        unfocusedTextColor   = Color.White,
                        cursorColor          = ConsensiusColors.Accent,
                        focusedContainerColor   = ConsensiusColors.Surface,
                        unfocusedContainerColor = ConsensiusColors.Surface
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newId = UUID.randomUUID().toString()
                        saveProfile(saveAsName, newId)
                        showSaveAsDialog = false
                        onNavigateBack()
                    },
                    enabled = saveAsName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = ConsensiusColors.Accent)
                ) { Text("SAVE") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveAsDialog = false }) {
                    Text("CANCEL", color = ConsensiusColors.TextSecondary)
                }
            }
        )
    }
}

// ─── FAB Sub-item ──────────────────────────────────────────────────────────────
@Composable
private fun FabSubItem(label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Label chip
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .shadow(4.dp, RoundedCornerShape(6.dp))
                .background(ConsensiusColors.Card, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = ConsensiusColors.AccentSecondary,
            contentColor = Color.Black,
            shape = CircleShape,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = label, modifier = Modifier.size(18.dp))
        }
    }
}

// ─── Canvas Element Widget ────────────────────────────────────────────────────
@Composable
private fun CanvasElementWidget(
    element: CanvasElement,
    onSettingsTap: () -> Unit
) {
    val sizeDp = element.size.toDp().dp

    Box(contentAlignment = Alignment.Center) {
        when (element.type) {
            ElementType.BUTTON -> {
                Box(
                    modifier = Modifier
                        .size(sizeDp)
                        .shadow(6.dp, CircleShape)
                        .clip(CircleShape)
                        .background(ConsensiusColors.Accent.copy(alpha = 0.18f))
                        .border(1.5.dp, ConsensiusColors.Accent.copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = element.label,
                        color = Color.White,
                        fontSize = (sizeDp.value * 0.22f).sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            ElementType.JOYSTICK -> {
                Box(
                    modifier = Modifier
                        .size(sizeDp)
                        .shadow(6.dp, CircleShape)
                        .clip(CircleShape)
                        .background(ConsensiusColors.Surface.copy(alpha = 0.8f))
                        .border(1.5.dp, ConsensiusColors.AccentSecondary.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // Inner circle thumb indicator
                    Box(
                        modifier = Modifier
                            .size(sizeDp * 0.38f)
                            .background(ConsensiusColors.AccentSecondary.copy(alpha = 0.5f), CircleShape)
                    )
                    Text(
                        text = if (element.joystickConfig.type == JoystickType.SKILL_AIM) "AIM" else "MOV",
                        color = ConsensiusColors.AccentSecondary.copy(alpha = 0.8f),
                        fontSize = (sizeDp.value * 0.16f).sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            ElementType.DPAD -> {
                // Cross shape via Canvas draw
                val crossColor = ConsensiusColors.TextSecondary.copy(alpha = 0.5f)
                val borderColor = ConsensiusColors.Accent.copy(alpha = 0.5f)
                Canvas(modifier = Modifier.size(sizeDp)) {
                    val s = size.minDimension
                    val arm = s * 0.28f
                    val cx = s / 2f
                    val cy = s / 2f
                    // Horizontal bar
                    drawRect(crossColor, topLeft = Offset(cx - s * 0.5f, cy - arm / 2), size = Size(s, arm))
                    // Vertical bar
                    drawRect(crossColor, topLeft = Offset(cx - arm / 2, cy - s * 0.5f), size = Size(arm, s))
                    // Border
                    drawRect(borderColor, topLeft = Offset(cx - s * 0.5f, cy - arm / 2), size = Size(s, arm), style = Stroke(1.5f))
                    drawRect(borderColor, topLeft = Offset(cx - arm / 2, cy - s * 0.5f), size = Size(arm, s), style = Stroke(1.5f))
                }
            }

            ElementType.TOUCHPAD -> {
                Box(
                    modifier = Modifier
                        .width(sizeDp * 2.5f)
                        .height(sizeDp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ConsensiusColors.Surface.copy(alpha = 0.5f))
                        .border(1.dp, ConsensiusColors.AccentSecondary.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TOUCHPAD", color = ConsensiusColors.TextSecondary.copy(alpha = 0.6f), fontSize = 9.sp, letterSpacing = 1.sp)
                        Text(
                            "×${element.touchpadConfig.sensitivityMultiplier}",
                            color = ConsensiusColors.AccentSecondary.copy(alpha = 0.5f),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        // ⚙ Gear icon on top-right corner
        IconButton(
            onClick = onSettingsTap,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(20.dp)
                .offset(x = 6.dp, y = (-6).dp)
                .background(ConsensiusColors.Card.copy(alpha = 0.85f), CircleShape)
        ) {
            Icon(
                Icons.Default.Settings,
                contentDescription = "Element settings",
                tint = ConsensiusColors.Accent,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

// ─── Element Settings Bottom Sheet ───────────────────────────────────────────
@Composable
private fun ElementSettingsSheet(
    element: CanvasElement,
    onUpdate: (CanvasElement) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when (element.type) {
                    ElementType.BUTTON   -> "⬤  Button Settings"
                    ElementType.JOYSTICK -> "◎  Joystick Settings"
                    ElementType.DPAD     -> "✚  D-Pad Settings"
                    ElementType.TOUCHPAD -> "▭  Touchpad Settings"
                },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            TextButton(onClick = onDelete) {
                Text("DELETE", color = ConsensiusColors.Error, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        HorizontalDivider(color = ConsensiusColors.CardBorder)

        // Size selector (all types)
        SheetLabel("Size")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ElementSize.values().forEach { size ->
                val selected = element.size == size
                Button(
                    onClick = { onUpdate(element.copy(size = size)) },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) ConsensiusColors.Accent else ConsensiusColors.Surface
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(size.name, color = if (selected) Color.White else ConsensiusColors.TextSecondary, fontSize = 13.sp)
                }
            }
        }

        // Type-specific settings
        when (element.type) {
            ElementType.BUTTON -> {
                var label by remember(element.id) { mutableStateOf(element.label) }
                var key   by remember(element.id) { mutableStateOf(element.buttonConfig.key) }

                SheetLabel("Label (max 4 chars)")
                SheetTextField(label, { label = it.take(4); onUpdate(element.copy(label = label)) }, "e.g. S1")

                SheetLabel("Key Mapping")
                SheetTextField(key, { key = it; onUpdate(element.copy(buttonConfig = ButtonElementConfig(key))) }, "e.g. k, space, f1")
            }

            ElementType.JOYSTICK -> {
                SheetLabel("Joystick Type")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    JoystickType.values().forEach { jsType ->
                        val selected = element.joystickConfig.type == jsType
                        Button(
                            onClick = { onUpdate(element.copy(joystickConfig = element.joystickConfig.copy(type = jsType))) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) ConsensiusColors.Accent else ConsensiusColors.Surface
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                if (jsType == JoystickType.MOVEMENT) "Movement" else "Skill-Aim",
                                color = if (selected) Color.White else ConsensiusColors.TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                if (element.joystickConfig.type == JoystickType.SKILL_AIM) {
                    var skillKey by remember(element.id) { mutableStateOf(element.joystickConfig.skillKey) }
                    SheetLabel("Skill Key Mapping")
                    SheetTextField(skillKey, {
                        skillKey = it
                        onUpdate(element.copy(joystickConfig = element.joystickConfig.copy(skillKey = it)))
                    }, "e.g. k, q")
                }
            }

            ElementType.DPAD -> {
                var upKey    by remember(element.id) { mutableStateOf(element.dpadConfig.upKey) }
                var downKey  by remember(element.id) { mutableStateOf(element.dpadConfig.downKey) }
                var leftKey  by remember(element.id) { mutableStateOf(element.dpadConfig.leftKey) }
                var rightKey by remember(element.id) { mutableStateOf(element.dpadConfig.rightKey) }

                SheetLabel("Key Mappings")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        SheetMiniKeyField("↑ Up", upKey) {
                            upKey = it
                            onUpdate(element.copy(dpadConfig = DpadElementConfig(upKey, downKey, leftKey, rightKey)))
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        SheetMiniKeyField("↓ Down", downKey) {
                            downKey = it
                            onUpdate(element.copy(dpadConfig = DpadElementConfig(upKey, downKey, leftKey, rightKey)))
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        SheetMiniKeyField("← Left", leftKey) {
                            leftKey = it
                            onUpdate(element.copy(dpadConfig = DpadElementConfig(upKey, downKey, leftKey, rightKey)))
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        SheetMiniKeyField("→ Right", rightKey) {
                            rightKey = it
                            onUpdate(element.copy(dpadConfig = DpadElementConfig(upKey, downKey, leftKey, rightKey)))
                        }
                    }
                }
            }

            ElementType.TOUCHPAD -> {
                var sensitivity by remember(element.id) { mutableFloatStateOf(element.touchpadConfig.sensitivityMultiplier) }
                var lClick      by remember(element.id) { mutableStateOf(element.touchpadConfig.leftClickKey) }
                var rClick      by remember(element.id) { mutableStateOf(element.touchpadConfig.rightClickKey) }

                SheetLabel("Sensitivity ×${String.format("%.1f", sensitivity)}")
                Slider(
                    value = sensitivity,
                    onValueChange = {
                        sensitivity = it
                        onUpdate(element.copy(touchpadConfig = TouchpadElementConfig(it, lClick, rClick)))
                    },
                    valueRange = 0.5f..5.0f,
                    colors = SliderDefaults.colors(
                        thumbColor       = ConsensiusColors.Accent,
                        activeTrackColor = ConsensiusColors.Accent
                    )
                )

                SheetLabel("Left Click Key")
                SheetTextField(lClick, {
                    lClick = it
                    onUpdate(element.copy(touchpadConfig = TouchpadElementConfig(sensitivity, it, rClick)))
                }, "e.g. mouse1")

                SheetLabel("Right Click Key")
                SheetTextField(rClick, {
                    rClick = it
                    onUpdate(element.copy(touchpadConfig = TouchpadElementConfig(sensitivity, lClick, it)))
                }, "e.g. mouse2")
            }
        }

        // Bottom close button
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ConsensiusColors.Accent)
        ) {
            Text("DONE", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ─── Sheet helpers ─────────────────────────────────────────────────────────────
@Composable
private fun SheetLabel(text: String) {
    Text(
        text = text,
        color = ConsensiusColors.Accent,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp
    )
}

@Composable
private fun SheetTextField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = ConsensiusColors.TextSecondary.copy(alpha = 0.6f), fontSize = 13.sp) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = 14.sp, color = Color.White
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor    = ConsensiusColors.Accent,
            unfocusedBorderColor  = ConsensiusColors.CardBorder,
            focusedContainerColor = ConsensiusColors.Surface,
            unfocusedContainerColor = ConsensiusColors.Surface,
            cursorColor = ConsensiusColors.Accent
        )
    )
}

@Composable
private fun SheetMiniKeyField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(10)) },
        label = { Text(label, fontSize = 9.sp, color = ConsensiusColors.TextSecondary) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().height(60.dp),
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor    = ConsensiusColors.Accent,
            unfocusedBorderColor  = ConsensiusColors.CardBorder,
            focusedContainerColor = ConsensiusColors.Surface,
            unfocusedContainerColor = ConsensiusColors.Surface,
            cursorColor = ConsensiusColors.Accent
        )
    )
}
