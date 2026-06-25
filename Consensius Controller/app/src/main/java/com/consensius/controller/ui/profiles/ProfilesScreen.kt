package com.consensius.controller.ui.profiles

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.consensius.controller.datastore.SettingsDataStore
import com.consensius.controller.model.ControllerProfile
import com.consensius.controller.ui.theme.ConsensiusColors
import kotlinx.coroutines.launch

@Composable
fun ProfilesScreen(
    settingsDataStore: SettingsDataStore,
    onNavigateBack: () -> Unit,
    onNavigateToEditor: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val profiles by settingsDataStore.profilesFlow.collectAsState(initial = emptyList())
    val selectedProfileId by settingsDataStore.selectedProfileIdFlow.collectAsState(initial = "")

    var profileToDelete by remember { mutableStateOf<ControllerProfile?>(null) }

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
                    text = "PROFILES",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    letterSpacing = 2.sp
                )
            }

            Spacer(Modifier.height(20.dp))

            if (profiles.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No profiles yet", color = ConsensiusColors.TextSecondary, fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Tap + to create one", color = ConsensiusColors.TextSecondary, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(profiles, key = { it.id }) { profile ->
                        val isActive = profile.id == selectedProfileId
                        ProfileCard(
                            profile = profile,
                            isActive = isActive,
                            onActivate = {
                                coroutineScope.launch {
                                    settingsDataStore.saveSelectedProfileId(profile.id)
                                }
                            },
                            onEdit = { onNavigateToEditor(profile.id) },
                            onDelete = { profileToDelete = profile }
                        )
                    }
                }
            }
        }

        // FAB
        FloatingActionButton(
            onClick = { onNavigateToEditor("new") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = ConsensiusColors.Accent,
            contentColor = Color.White,
            shape = CircleShape
        ) {
            Icon(Icons.Default.Add, contentDescription = "New Profile")
        }
    }

    // Delete confirmation dialog
    profileToDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { profileToDelete = null },
            containerColor = ConsensiusColors.Card,
            title = {
                Text("Delete Profile", color = ConsensiusColors.TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Delete \"${profile.name}\"? This action cannot be undone.",
                    color = ConsensiusColors.TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            settingsDataStore.deleteProfile(profile.id)
                        }
                        profileToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ConsensiusColors.Error)
                ) { Text("DELETE") }
            },
            dismissButton = {
                TextButton(onClick = { profileToDelete = null }) {
                    Text("CANCEL", color = ConsensiusColors.TextSecondary)
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfileCard(
    profile: ControllerProfile,
    isActive: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isActive) 1.5.dp else 1.dp,
                color = if (isActive) ConsensiusColors.Accent else ConsensiusColors.CardBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .background(
                if (isActive)
                    Brush.horizontalGradient(
                        listOf(
                            ConsensiusColors.Accent.copy(alpha = 0.1f),
                            ConsensiusColors.Card
                        )
                    )
                else
                    Brush.linearGradient(listOf(ConsensiusColors.Card, ConsensiusColors.Card))
            )
            .combinedClickable(
                onClick = onActivate,
                onLongClick = { menuExpanded = true }
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isActive) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(ConsensiusColors.Accent, CircleShape)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = profile.name,
                        color = if (isActive) Color.White else ConsensiusColors.TextPrimary,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val totalElements = profile.pages.sumOf { it.elements.size }
                    BadgeText("${profile.pages.size} page${if (profile.pages.size != 1) "s" else ""}")
                    BadgeText("$totalElements elements")
                }
            }

            Row {
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = ConsensiusColors.Accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = ConsensiusColors.Error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (isActive) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Active",
                tint = ConsensiusColors.Accent,
                modifier = Modifier
                    .size(16.dp)
                    .align(Alignment.TopEnd)
            )
        }
    }
}

@Composable
private fun BadgeText(text: String) {
    Text(
        text = text,
        color = ConsensiusColors.TextSecondary,
        fontSize = 11.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(ConsensiusColors.Background.copy(alpha = 0.5f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
