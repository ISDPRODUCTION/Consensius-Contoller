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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.consensius.controller.model.ElementDefaults
import com.consensius.controller.model.ElementType
import com.consensius.controller.model.JoystickElementConfig
import com.consensius.controller.model.JoystickType
import com.consensius.controller.model.ProfilePage
import com.consensius.controller.model.TouchpadElementConfig
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
    val context           = LocalContext.current
    val coroutineScope    = rememberCoroutineScope()
    val density           = LocalDensity.current
    val snackbarHostState = remember { SnackbarHostState() }

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
    var profileName by remember { mutableStateOf(if (isNew) "New Profile" else "Loading…") }
    var editingId   by remember { mutableStateOf(if (isNew) UUID.randomUUID().toString() else profileId ?: UUID.randomUUID().toString()) }
    val pages       = remember { mutableStateListOf<ProfilePage>() }
    var currentPage by remember { mutableIntStateOf(0) }

    // ── UI state ──────────────────────────────────────────────────────────────
    // Top-right 3-dot menu (replaces the full top bar)
    var menuExpanded      by remember { mutableStateOf(false) }
    var showNameDialog    by remember { mutableStateOf(false) }
    var fabExpanded       by remember { mutableStateOf(false) }
    var settingsElement   by remember { mutableStateOf<CanvasElement?>(null) }
    var showSaveAsDialog  by remember { mutableStateOf(false) }
    var saveAsName        by remember { mutableStateOf("") }

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
        if (pages.isEmpty()) pages.add(ProfilePage(name = "Page 1"))
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
        pages[pageIdx] = pages[pageIdx].copy(elements = pages[pageIdx].elements + el)
    }

    fun deleteElement(id: String) {
        val pageIdx = currentPage
        if (pageIdx < 0 || pageIdx >= pages.size) return
        pages[pageIdx] = pages[pageIdx].copy(elements = pages[pageIdx].elements.filter { it.id != id })
    }

    fun saveProfile(name: String, newId: String = editingId) {
        coroutineScope.launch {
            settingsDataStore.saveProfile(
                ControllerProfile(id = newId, name = name.ifBlank { "Untitled" }, pages = pages.toList())
            )
            snackbarHostState.showSnackbar(message = "Profile saved!", duration = SnackbarDuration.Short)
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // ─────────────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ConsensiusColors.Background)
    ) {

        // ── Dot-grid canvas (full-screen, no top bar blocking) ─────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    canvasWidthPx  = coords.size.width.toFloat()
                    canvasHeightPx = coords.size.height.toFloat()
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val spacing  = 28.dp.toPx()
                val dotColor = Color(0xFF00C2FF).copy(alpha = 0.12f)
                var x = spacing
                while (x < size.width) {
                    var y = spacing
                    while (y < size.height) {
                        drawCircle(dotColor, 1.5f, Offset(x, y))
                        y += spacing
                    }
                    x += spacing
                }
            }

            // ── Element rendering ─────────────────────────────────────────────
            // Each element is wrapped in key(el.id) so Compose tracks state
            // correctly even when elements are added/removed/reordered.
            currentElements().forEach { el ->
                key(el.id) {
                    // Per-element size — never shared
                    val widthDp       = el.width.dp
                    val heightDp      = el.height.dp
                    val widthPx       = with(density) { widthDp.toPx() }
                    val heightPx      = with(density) { heightDp.toPx() }
                    val visualWidthPx = widthPx  // width is now the actual visual width for all element types

                    // Local drag offset — initialised from stored fraction position
                    var offsetX by remember { mutableFloatStateOf((el.x * canvasWidthPx - visualWidthPx / 2).coerceAtLeast(0f)) }
                    var offsetY by remember { mutableFloatStateOf((el.y * canvasHeightPx - heightPx / 2).coerceAtLeast(0f)) }

                    // Re-sync ONLY when canvas size changes (e.g. rotation).
                    // NOT keyed on el.x/el.y — that would cause the LaunchedEffect to
                    // fight with the drag handler on every frame, making drag heavy.
                    // NOT keyed on el.width/el.height — slider would snap position.
                    LaunchedEffect(canvasWidthPx, canvasHeightPx) {
                        offsetX = ((el.x * canvasWidthPx) - visualWidthPx / 2).coerceAtLeast(0f)
                        offsetY = ((el.y * canvasHeightPx) - heightPx / 2).coerceAtLeast(0f)
                    }

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                            .width(widthDp)
                            .height(heightDp)
                            // FIX DRAG PERFORMANCE: update model only on drag END, not every frame.
                            // FIX DRAG FLIES: pointerInput key includes el.width + el.height so the
                            //   lambda always captures the CURRENT visualWidthPx / heightPx after a
                            //   slider resize — prevents stale-closure position mismatch.
                            .pointerInput(el.id, el.width, el.height) {
                                detectDragGestures(
                                    onDragEnd = {
                                        // Write final position to model once, after drag finishes.
                                        val liveEl = pages.getOrNull(currentPage)
                                            ?.elements?.firstOrNull { e -> e.id == el.id }
                                            ?: return@detectDragGestures
                                        updateElement(
                                            liveEl.copy(
                                                x = ((offsetX + visualWidthPx / 2) / canvasWidthPx).coerceIn(0f, 1f),
                                                y = ((offsetY + heightPx / 2) / canvasHeightPx).coerceIn(0f, 1f)
                                            )
                                        )
                                    },
                                    onDragCancel = {
                                        // Snap back to last saved position on gesture cancel
                                        val liveEl = pages.getOrNull(currentPage)
                                            ?.elements?.firstOrNull { e -> e.id == el.id }
                                        if (liveEl != null) {
                                            offsetX = ((liveEl.x * canvasWidthPx) - visualWidthPx / 2).coerceAtLeast(0f)
                                            offsetY = ((liveEl.y * canvasHeightPx) - heightPx / 2).coerceAtLeast(0f)
                                        }
                                    }
                                ) { _, delta ->
                                    // Only update local state — ZERO model writes here.
                                    // This makes dragging buttery smooth (no recomposition per frame).
                                    offsetX = (offsetX + delta.x).coerceIn(0f, (canvasWidthPx - visualWidthPx).coerceAtLeast(0f))
                                    offsetY = (offsetY + delta.y).coerceIn(0f, (canvasHeightPx - heightPx).coerceAtLeast(0f))
                                }
                            }
                    ) {
                        CanvasElementWidget(
                            element       = el,
                            onSettingsTap = { settingsElement = el }
                        )
                    }
                }
            }
        }

        // ── PAGE INDICATOR (bottom-center) ────────────────────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            pages.forEachIndexed { index, _ ->
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

        // ── FAB cluster (bottom-right) ────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedVisibility(
                visible = fabExpanded,
                enter   = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
                exit    = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut()
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FabSubItem("Add Page") {
                        fabExpanded = false
                        pages.add(ProfilePage(name = "Page ${pages.size + 1}"))
                        currentPage = pages.size - 1
                    }
                    FabSubItem("Add Touchpad") {
                        fabExpanded = false
                        addElement(CanvasElement(type = ElementType.TOUCHPAD, label = "MOUSE", x = 0.5f, y = 0.7f,
                            width = ElementDefaults.TOUCHPAD_WIDTH, height = ElementDefaults.TOUCHPAD_HEIGHT))
                    }
                    FabSubItem("Add D-Pad") {
                        fabExpanded = false
                        addElement(CanvasElement(type = ElementType.DPAD, label = "DPAD", x = 0.2f, y = 0.5f,
                            width = ElementDefaults.DPAD_WIDTH, height = ElementDefaults.DPAD_HEIGHT))
                    }
                    FabSubItem("Add Joystick") {
                        fabExpanded = false
                        addElement(CanvasElement(type = ElementType.JOYSTICK, label = "MOVE", x = 0.15f, y = 0.7f,
                            width = ElementDefaults.JOYSTICK_SIZE, height = ElementDefaults.JOYSTICK_SIZE))
                    }
                    FabSubItem("Add Button") {
                        fabExpanded = false
                        addElement(CanvasElement(type = ElementType.BUTTON, label = "BTN", x = 0.5f, y = 0.5f,
                            width = ElementDefaults.BUTTON_WIDTH, height = ElementDefaults.BUTTON_HEIGHT))
                    }
                }
            }

            FloatingActionButton(
                onClick        = { fabExpanded = !fabExpanded },
                containerColor = ConsensiusColors.Accent,
                contentColor   = Color.White,
                shape          = CircleShape,
                modifier       = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add element")
            }
        }

        // ── TOP-RIGHT 3-DOT MENU — replaces the blocking full-width top bar ──
        // Small semi-transparent pill so the entire canvas is usable for layout.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp)
        ) {
            // Semi-transparent profile name chip + ⋮ icon
            Row(
                modifier = Modifier
                    .background(
                        ConsensiusColors.Card.copy(alpha = 0.75f),
                        RoundedCornerShape(20.dp)
                    )
                    .border(1.dp, ConsensiusColors.CardBorder.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text       = profileName.take(16).ifBlank { "Untitled" },
                    color      = ConsensiusColors.TextPrimary.copy(alpha = 0.85f),
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1
                )
                IconButton(
                    onClick  = { menuExpanded = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Menu",
                        tint    = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            DropdownMenu(
                expanded         = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier         = Modifier.background(ConsensiusColors.Card)
            ) {
                DropdownMenuItem(
                    text    = { Text("✏  Edit profile name", color = ConsensiusColors.TextPrimary) },
                    onClick = { menuExpanded = false; showNameDialog = true }
                )
                HorizontalDivider(color = ConsensiusColors.CardBorder)
                DropdownMenuItem(
                    text    = { Text("💾  Save", color = ConsensiusColors.TextPrimary) },
                    onClick = { menuExpanded = false; saveProfile(profileName) }
                )
                DropdownMenuItem(
                    text    = { Text("Save As…", color = ConsensiusColors.TextPrimary) },
                    onClick = { menuExpanded = false; saveAsName = profileName; showSaveAsDialog = true }
                )
                HorizontalDivider(color = ConsensiusColors.CardBorder)
                DropdownMenuItem(
                    text    = { Text("Exit without saving", color = ConsensiusColors.Error) },
                    onClick = { menuExpanded = false; onNavigateBack() }
                )
            }
        }

        // ── Snackbar ──────────────────────────────────────────────────────────
        SnackbarHost(
            hostState = snackbarHostState,
            modifier  = Modifier.align(Alignment.BottomCenter)
        )
    }

    // ── Edit Profile Name dialog ──────────────────────────────────────────────
    if (showNameDialog) {
        var nameInput by remember { mutableStateOf(profileName) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showNameDialog = false },
            containerColor   = ConsensiusColors.Card,
            title = { Text("Profile Name", color = Color.White, fontWeight = FontWeight.Bold) },
            text  = {
                OutlinedTextField(
                    value         = nameInput,
                    onValueChange = { nameInput = it },
                    singleLine    = true,
                    placeholder   = { Text("e.g. My Layout", color = ConsensiusColors.TextSecondary) },
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = ConsensiusColors.Accent,
                        unfocusedBorderColor    = ConsensiusColors.CardBorder,
                        focusedTextColor        = Color.White,
                        unfocusedTextColor      = Color.White,
                        cursorColor             = ConsensiusColors.Accent,
                        focusedContainerColor   = ConsensiusColors.Surface,
                        unfocusedContainerColor = ConsensiusColors.Surface
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = { profileName = nameInput.ifBlank { "Untitled" }; showNameDialog = false },
                    colors  = ButtonDefaults.buttonColors(containerColor = ConsensiusColors.Accent)
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text("CANCEL", color = ConsensiusColors.TextSecondary)
                }
            }
        )
    }

    // ── Element settings bottom sheet ─────────────────────────────────────────
    val liveSettingsElement = settingsElement?.id?.let { id ->
        pages.getOrNull(currentPage)?.elements?.firstOrNull { it.id == id }
    }
    liveSettingsElement?.let { el ->
        ModalBottomSheet(
            onDismissRequest = { settingsElement = null },
            sheetState       = sheetState,
            containerColor   = ConsensiusColors.Card,
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
                element  = el,
                onUpdate = { updated ->
                    updateElement(updated)
                    settingsElement = updated
                },
                onDelete  = { deleteElement(el.id); settingsElement = null },
                onDismiss = { settingsElement = null }
            )
        }
    }

    // ── Save As dialog ────────────────────────────────────────────────────────
    if (showSaveAsDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSaveAsDialog = false },
            containerColor   = ConsensiusColors.Card,
            title = { Text("Save As", color = Color.White, fontWeight = FontWeight.Bold) },
            text  = {
                OutlinedTextField(
                    value         = saveAsName,
                    onValueChange = { saveAsName = it },
                    label         = { Text("New profile name", color = ConsensiusColors.TextSecondary) },
                    singleLine    = true,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = ConsensiusColors.Accent,
                        unfocusedBorderColor    = ConsensiusColors.CardBorder,
                        focusedTextColor        = Color.White,
                        unfocusedTextColor      = Color.White,
                        cursorColor             = ConsensiusColors.Accent,
                        focusedContainerColor   = ConsensiusColors.Surface,
                        unfocusedContainerColor = ConsensiusColors.Surface
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick  = {
                        saveProfile(saveAsName, UUID.randomUUID().toString())
                        showSaveAsDialog = false
                        onNavigateBack()
                    },
                    enabled = saveAsName.isNotBlank(),
                    colors  = ButtonDefaults.buttonColors(containerColor = ConsensiusColors.Accent)
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
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text       = label,
            color      = Color.White,
            fontSize   = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier
                .shadow(4.dp, RoundedCornerShape(6.dp))
                .background(ConsensiusColors.Card, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
        SmallFloatingActionButton(
            onClick        = onClick,
            containerColor = ConsensiusColors.AccentSecondary,
            contentColor   = Color.Black,
            shape          = CircleShape,
            modifier       = Modifier.size(40.dp)
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
    val widthDp  = element.width.dp
    val heightDp = element.height.dp

    Box(contentAlignment = Alignment.Center) {
        when (element.type) {
            ElementType.BUTTON -> {
                Box(
                    modifier = Modifier
                        .width(widthDp)
                        .height(heightDp)
                        .shadow(6.dp, CircleShape)
                        .clip(CircleShape)
                        .background(ConsensiusColors.Accent.copy(alpha = 0.18f))
                        .border(1.5.dp, ConsensiusColors.Accent.copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val labelSize = (minOf(widthDp, heightDp).value * 0.22f).sp
                    Text(element.label, color = Color.White, fontSize = labelSize,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
            }

            ElementType.JOYSTICK -> {
                val sizeDp = widthDp
                Box(
                    modifier = Modifier
                        .size(sizeDp)
                        .shadow(6.dp, CircleShape)
                        .clip(CircleShape)
                        .background(ConsensiusColors.Surface.copy(alpha = 0.8f))
                        .border(1.5.dp, ConsensiusColors.AccentSecondary.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.size(sizeDp * 0.38f)
                        .background(ConsensiusColors.AccentSecondary.copy(alpha = 0.5f), CircleShape))
                    Text(
                        text       = if (element.joystickConfig.type == JoystickType.SKILL_AIM) "AIM" else "MOV",
                        color      = ConsensiusColors.AccentSecondary.copy(alpha = 0.8f),
                        fontSize   = (sizeDp.value * 0.16f).sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            ElementType.DPAD -> {
                val crossColor  = ConsensiusColors.TextSecondary.copy(alpha = 0.5f)
                val borderColor = ConsensiusColors.Accent.copy(alpha = 0.5f)
                Canvas(modifier = Modifier.width(widthDp).height(heightDp)) {
                    val w = size.width; val h = size.height
                    val arm = minOf(w, h) * 0.28f
                    val cx = w / 2f; val cy = h / 2f
                    drawRect(crossColor, Offset(cx - w * 0.5f, cy - arm / 2), Size(w, arm))
                    drawRect(crossColor, Offset(cx - arm / 2, cy - h * 0.5f), Size(arm, h))
                    drawRect(borderColor, Offset(cx - w * 0.5f, cy - arm / 2), Size(w, arm), style = Stroke(1.5f))
                    drawRect(borderColor, Offset(cx - arm / 2, cy - h * 0.5f), Size(arm, h), style = Stroke(1.5f))
                }
            }

            ElementType.TOUCHPAD -> {
                Box(
                    modifier = Modifier
                        .width(widthDp)
                        .height(heightDp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ConsensiusColors.Surface.copy(alpha = 0.5f))
                        .border(1.dp, ConsensiusColors.AccentSecondary.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TOUCHPAD", color = ConsensiusColors.TextSecondary.copy(alpha = 0.6f), fontSize = 9.sp, letterSpacing = 1.sp)
                        Text("${widthDp.value.roundToInt()}×${heightDp.value.roundToInt()}dp",
                            color = ConsensiusColors.AccentSecondary.copy(alpha = 0.5f), fontSize = 10.sp)
                    }
                }
            }
        }

        // ⚙ Gear icon
        IconButton(
            onClick  = onSettingsTap,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(20.dp)
                .offset(x = 6.dp, y = (-6).dp)
                .background(ConsensiusColors.Card.copy(alpha = 0.85f), CircleShape)
        ) {
            Icon(Icons.Default.Settings, contentDescription = "Settings",
                tint = ConsensiusColors.Accent, modifier = Modifier.size(12.dp))
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
        // Header row
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text = when (element.type) {
                    ElementType.BUTTON   -> "⬤  Button Settings"
                    ElementType.JOYSTICK -> "◎  Joystick Settings"
                    ElementType.DPAD     -> "✚  D-Pad Settings"
                    ElementType.TOUCHPAD -> "▭  Touchpad Settings"
                },
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp
            )
            TextButton(onClick = onDelete) {
                Text("DELETE", color = ConsensiusColors.Error, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        HorizontalDivider(color = ConsensiusColors.CardBorder)

        // ── Size sliders ─────────────────────────────────────────────────────
        // FIX SLIDER LAG: local state updates the slider visual INSTANTLY.
        // onValueChangeFinished writes to the model ONCE when user lifts finger —
        // this avoids triggering full recomposition on every slider pixel.
        when (element.type) {
            ElementType.JOYSTICK -> {
                var size by remember(element.id) { mutableFloatStateOf(element.width) }
                SheetLabel("Size: ${size.roundToInt()}dp")
                Slider(
                    value                 = size,
                    onValueChange         = { size = it },
                    onValueChangeFinished = { onUpdate(element.copy(width = size, height = size)) },
                    valueRange            = 80f..300f,
                    colors = SliderDefaults.colors(
                        thumbColor = ConsensiusColors.Accent, activeTrackColor = ConsensiusColors.Accent)
                )
            }
            ElementType.TOUCHPAD -> {
                // Width and Height sliders for touchpad
                var tpWidth  by remember(element.id) { mutableFloatStateOf(element.width) }
                var tpHeight by remember(element.id) { mutableFloatStateOf(element.height) }

                SheetLabel("Width: ${tpWidth.roundToInt()}dp")
                Slider(
                    value                 = tpWidth,
                    onValueChange         = { tpWidth = it },
                    onValueChangeFinished = { onUpdate(element.copy(width = tpWidth, height = tpHeight)) },
                    valueRange            = 150f..700f,
                    colors = SliderDefaults.colors(
                        thumbColor = ConsensiusColors.Accent, activeTrackColor = ConsensiusColors.Accent)
                )

                SheetLabel("Height: ${tpHeight.roundToInt()}dp")
                Slider(
                    value                 = tpHeight,
                    onValueChange         = { tpHeight = it },
                    onValueChangeFinished = { onUpdate(element.copy(width = tpWidth, height = tpHeight)) },
                    valueRange            = 60f..300f,
                    colors = SliderDefaults.colors(
                        thumbColor       = ConsensiusColors.AccentSecondary,
                        activeTrackColor = ConsensiusColors.AccentSecondary)
                )
            }
            else -> {
                // BUTTON and DPAD — separate Width and Height sliders
                var elWidth  by remember(element.id) { mutableFloatStateOf(element.width) }
                var elHeight by remember(element.id) { mutableFloatStateOf(element.height) }

                SheetLabel("Width: ${elWidth.roundToInt()}dp")
                Slider(
                    value                 = elWidth,
                    onValueChange         = { elWidth = it },
                    onValueChangeFinished = { onUpdate(element.copy(width = elWidth, height = elHeight)) },
                    valueRange            = 40f..300f,
                    colors = SliderDefaults.colors(
                        thumbColor = ConsensiusColors.Accent, activeTrackColor = ConsensiusColors.Accent)
                )

                SheetLabel("Height: ${elHeight.roundToInt()}dp")
                Slider(
                    value                 = elHeight,
                    onValueChange         = { elHeight = it },
                    onValueChangeFinished = { onUpdate(element.copy(width = elWidth, height = elHeight)) },
                    valueRange            = 40f..300f,
                    colors = SliderDefaults.colors(
                        thumbColor       = ConsensiusColors.AccentSecondary,
                        activeTrackColor = ConsensiusColors.AccentSecondary)
                )
            }
        }

        HorizontalDivider(color = ConsensiusColors.CardBorder.copy(alpha = 0.5f))

        // ── Type-specific settings ────────────────────────────────────────────
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
                var jsLabel by remember(element.id) { mutableStateOf(element.label) }
                SheetLabel("Element Label (max 8)")
                SheetTextField(jsLabel, {
                    jsLabel = it.take(8); onUpdate(element.copy(label = jsLabel))
                }, "e.g. MOVE, AIM")

                SheetLabel("Joystick Type")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    JoystickType.values().forEach { jsType ->
                        val selected = element.joystickConfig.type == jsType
                        Button(
                            onClick = {
                                val newLabel = if (jsLabel.isBlank()) (if (jsType == JoystickType.MOVEMENT) "MOVE" else "AIM") else jsLabel
                                onUpdate(element.copy(joystickConfig = element.joystickConfig.copy(type = jsType), label = newLabel))
                                jsLabel = newLabel
                            },
                            shape  = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (selected) ConsensiusColors.Accent else ConsensiusColors.Surface),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (jsType == JoystickType.MOVEMENT) "Movement" else "Skill-Aim",
                                color = if (selected) Color.White else ConsensiusColors.TextSecondary, fontSize = 12.sp)
                        }
                    }
                }

                if (element.joystickConfig.type == JoystickType.SKILL_AIM) {
                    var skillKey by remember(element.id) { mutableStateOf(element.joystickConfig.skillKey) }
                    SheetLabel("Skill Key Mapping")
                    SheetTextField(skillKey, {
                        skillKey = it; onUpdate(element.copy(joystickConfig = element.joystickConfig.copy(skillKey = it)))
                    }, "e.g. k, q")
                }
            }

            ElementType.DPAD -> {
                var dpadLabel by remember(element.id) { mutableStateOf(element.label) }
                SheetLabel("Element Label (max 8)")
                SheetTextField(dpadLabel, {
                    dpadLabel = it.take(8); onUpdate(element.copy(label = dpadLabel))
                }, "e.g. DPAD")

                var upKey    by remember(element.id) { mutableStateOf(element.dpadConfig.upKey) }
                var downKey  by remember(element.id) { mutableStateOf(element.dpadConfig.downKey) }
                var leftKey  by remember(element.id) { mutableStateOf(element.dpadConfig.leftKey) }
                var rightKey by remember(element.id) { mutableStateOf(element.dpadConfig.rightKey) }

                SheetLabel("Key Mappings")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        SheetMiniKeyField("Up", upKey) { upKey = it; onUpdate(element.copy(dpadConfig = DpadElementConfig(upKey, downKey, leftKey, rightKey))) }
                    }
                    Column(Modifier.weight(1f)) {
                        SheetMiniKeyField("Down", downKey) { downKey = it; onUpdate(element.copy(dpadConfig = DpadElementConfig(upKey, downKey, leftKey, rightKey))) }
                    }
                    Column(Modifier.weight(1f)) {
                        SheetMiniKeyField("Left", leftKey) { leftKey = it; onUpdate(element.copy(dpadConfig = DpadElementConfig(upKey, downKey, leftKey, rightKey))) }
                    }
                    Column(Modifier.weight(1f)) {
                        SheetMiniKeyField("Right", rightKey) { rightKey = it; onUpdate(element.copy(dpadConfig = DpadElementConfig(upKey, downKey, leftKey, rightKey))) }
                    }
                }
            }

            ElementType.TOUCHPAD -> {
                var tpLabel     by remember(element.id) { mutableStateOf(element.label) }
                var sensitivity by remember(element.id) { mutableFloatStateOf(element.touchpadConfig.sensitivityMultiplier) }
                var lClick      by remember(element.id) { mutableStateOf(element.touchpadConfig.leftClickKey) }
                var rClick      by remember(element.id) { mutableStateOf(element.touchpadConfig.rightClickKey) }

                SheetLabel("Element Label (max 8)")
                SheetTextField(tpLabel, { tpLabel = it.take(8); onUpdate(element.copy(label = tpLabel)) }, "e.g. MOUSE")

                SheetLabel("Sensitivity x${String.format("%.1f", sensitivity)}")
                Slider(
                    value                 = sensitivity,
                    onValueChange         = { sensitivity = it },
                    onValueChangeFinished = { onUpdate(element.copy(touchpadConfig = TouchpadElementConfig(sensitivity, lClick, rClick))) },
                    valueRange            = 0.5f..5.0f,
                    colors = SliderDefaults.colors(thumbColor = ConsensiusColors.Accent, activeTrackColor = ConsensiusColors.Accent)
                )

                SheetLabel("Left Click Key")
                SheetTextField(lClick, { lClick = it; onUpdate(element.copy(touchpadConfig = TouchpadElementConfig(sensitivity, it, rClick))) }, "e.g. mouse1")

                SheetLabel("Right Click Key")
                SheetTextField(rClick, { rClick = it; onUpdate(element.copy(touchpadConfig = TouchpadElementConfig(sensitivity, lClick, it))) }, "e.g. mouse2")
            }
        }

        Spacer(Modifier.height(4.dp))
        Button(
            onClick  = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(10.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = ConsensiusColors.Accent)
        ) {
            Text("DONE", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ─── Sheet helpers ─────────────────────────────────────────────────────────────
@Composable
private fun SheetLabel(text: String) {
    Text(text = text, color = ConsensiusColors.Accent, fontSize = 10.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
}

@Composable
private fun SheetTextField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        placeholder   = { Text(placeholder, color = ConsensiusColors.TextSecondary.copy(alpha = 0.6f), fontSize = 13.sp) },
        singleLine    = true,
        modifier      = Modifier.fillMaxWidth(),
        textStyle     = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = Color.White),
        colors        = OutlinedTextFieldDefaults.colors(
            focusedBorderColor      = ConsensiusColors.Accent,
            unfocusedBorderColor    = ConsensiusColors.CardBorder,
            focusedContainerColor   = ConsensiusColors.Surface,
            unfocusedContainerColor = ConsensiusColors.Surface,
            cursorColor             = ConsensiusColors.Accent
        )
    )
}

@Composable
private fun SheetMiniKeyField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value         = value,
        onValueChange = { onValueChange(it.take(10)) },
        label         = { Text(label, fontSize = 9.sp, color = ConsensiusColors.TextSecondary) },
        singleLine    = true,
        modifier      = Modifier.fillMaxWidth().height(60.dp),
        textStyle     = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White),
        colors        = OutlinedTextFieldDefaults.colors(
            focusedBorderColor      = ConsensiusColors.Accent,
            unfocusedBorderColor    = ConsensiusColors.CardBorder,
            focusedContainerColor   = ConsensiusColors.Surface,
            unfocusedContainerColor = ConsensiusColors.Surface,
            cursorColor             = ConsensiusColors.Accent
        )
    )
}
