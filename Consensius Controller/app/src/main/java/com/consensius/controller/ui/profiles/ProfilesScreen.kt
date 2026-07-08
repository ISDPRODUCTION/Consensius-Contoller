package com.consensius.controller.ui.profiles

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.consensius.controller.ui.components.CompactCard
import com.consensius.controller.ui.components.GlassCard
import com.consensius.controller.ui.components.ValueText
import com.consensius.controller.ui.theme.NeoColors
import com.consensius.controller.ui.theme.drawAmbientParticles
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
            .background(NeoColors.Background)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawAmbientParticles(
                particleCount = 5,
                primaryColor = NeoColors.ElectricBlue.copy(alpha = 0.03f),
                secondaryColor = NeoColors.Amethyst.copy(alpha = 0.02f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ── Top Bar ─────────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
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
                    text = "Profiles",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.weight(1f))
                if (profiles.isNotEmpty()) {
                    Text(
                        text = "${profiles.size} total",
                        color = NeoColors.TextTertiary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (profiles.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            NeoColors.ElectricBlue.copy(alpha = 0.1f),
                                            NeoColors.Amethyst.copy(alpha = 0.06f)
                                        )
                                    ),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.VideogameAsset,
                                contentDescription = null,
                                tint = NeoColors.TextSecondary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        ValueText(
                            text = "No profiles yet",
                            fontSize = 18.sp,
                            color = NeoColors.TextSecondary
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Tap + to create your first layout",
                            color = NeoColors.TextTertiary,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(profiles, key = { it.id }) { profile ->
                        val isActive = profile.id == selectedProfileId
                        ProfileGlassCard(
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

        // ── FAB ─────────────────────────────────────────────────────────────
        FloatingActionButton(
            onClick = { onNavigateToEditor("new") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = NeoColors.ElectricBlue,
            contentColor = Color.White,
            shape = CircleShape
        ) {
            Icon(Icons.Default.Add, contentDescription = "New Profile")
        }
    }

    // ── Delete Confirmation ─────────────────────────────────────────────────
    profileToDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { profileToDelete = null },
            containerColor = NeoColors.GlassCard,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text("Delete Profile", color = NeoColors.TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Delete \"${profile.name}\"? This cannot be undone.",
                    color = NeoColors.TextSecondary
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
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeoColors.Error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { profileToDelete = null }) {
                    Text("Cancel", color = NeoColors.TextSecondary)
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfileGlassCard(
    profile: ControllerProfile,
    isActive: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val cardRow = @Composable {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onActivate,
                    onLongClick = onEdit
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Profile icon
                val iconBgModifier = if (isActive) {
                    Modifier.background(
                        Brush.linearGradient(
                            colors = listOf(
                                NeoColors.ElectricBlue.copy(alpha = 0.2f),
                                NeoColors.Amethyst.copy(alpha = 0.12f)
                            )
                        ),
                        RoundedCornerShape(12.dp)
                    )
                } else {
                    Modifier.background(NeoColors.Surface, RoundedCornerShape(12.dp))
                }
                Box(
                    modifier = Modifier.size(44.dp).then(iconBgModifier),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = if (isActive) NeoColors.ElectricBlue else NeoColors.TextTertiary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = profile.name,
                            color = if (isActive) Color.White else NeoColors.TextPrimary,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        if (isActive) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Active",
                                tint = NeoColors.ElectricBlue,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val totalElements = profile.pages.sumOf { it.elements.size }
                        ProfileBadge("${profile.pages.size} page${if (profile.pages.size != 1) "s" else ""}")
                        ProfileBadge("$totalElements element${if (totalElements != 1) "s" else ""}")
                    }
                }
            }

            Row {
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = NeoColors.ElectricBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = NeoColors.Error.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    if (isActive) {
        GlassCard(glowColor = NeoColors.ElectricBlue, modifier = Modifier.fillMaxWidth()) {
            cardRow()
        }
    } else {
        CompactCard(modifier = Modifier.fillMaxWidth()) {
            cardRow()
        }
    }
}

@Composable
private fun ProfileBadge(text: String) {
    Text(
        text = text,
        color = NeoColors.TextTertiary,
        fontSize = 11.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(NeoColors.Background.copy(alpha = 0.5f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
