package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auth.AuthUser
import com.example.sync.SyncStatus
import com.example.sync.SyncUiState
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CloudSyncStatusBadge(
    syncState: SyncUiState,
    authUser: AuthUser?,
    onTriggerSync: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    // Infinite rotation for syncing status
    val infiniteTransition = rememberInfiniteTransition(label = "sync_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Pulse animation when transitioning to SYNCED
    val syncPulseScale by animateFloatAsState(
        targetValue = if (syncState.status == SyncStatus.SYNCED) 1.08f else 1.0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "syncPulseScale"
    )

    val (iconColor, bgColor) = when (syncState.status) {
        SyncStatus.SYNCING -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        SyncStatus.SYNCED -> Color(0xFF10B981) to Color(0xFFECFDF5)
        SyncStatus.OFFLINE -> Color(0xFFE65100) to Color(0xFFFFF3E0)
        SyncStatus.NOT_AUTHENTICATED -> MaterialTheme.colorScheme.outline to MaterialTheme.colorScheme.surfaceVariant
        SyncStatus.ERROR -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bgColor,
        modifier = modifier
            .graphicsLayer {
                scaleX = syncPulseScale
                scaleY = syncPulseScale
            }
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onTriggerSync()
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showDialog = true
                }
            )
            .testTag("cloud_sync_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            androidx.compose.animation.AnimatedContent(
                targetState = syncState.status,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(200)) + scaleIn(initialScale = 0.8f, animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f)))
                        .togetherWith(fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 0.8f))
                },
                label = "syncIconTransition"
            ) { targetStatus ->
                when (targetStatus) {
                    SyncStatus.SYNCING -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Syncing in progress",
                                tint = iconColor,
                                modifier = Modifier
                                    .size(16.dp)
                                    .rotate(rotation)
                            )
                            Text(
                                text = "Syncing...",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = iconColor
                            )
                        }
                    }
                    SyncStatus.SYNCED -> {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = "Cloud synced",
                            tint = iconColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    SyncStatus.OFFLINE -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = "Offline - Local active",
                                tint = iconColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Offline",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = iconColor
                            )
                        }
                    }
                    SyncStatus.NOT_AUTHENTICATED -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.CloudQueue,
                                contentDescription = "Sign in to sync",
                                tint = iconColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Backup",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                color = iconColor
                            )
                        }
                    }
                    SyncStatus.ERROR -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                imageVector = Icons.Default.SyncProblem,
                                contentDescription = "Sync warning",
                                tint = iconColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Alert",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = iconColor
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (syncState.status == SyncStatus.SYNCED) Icons.Default.CloudDone else Icons.Default.Cloud,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Cloud Sync", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Account card
                    if (authUser != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = (authUser.displayName?.firstOrNull() ?: authUser.email?.firstOrNull() ?: 'G').uppercase(),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                                Column {
                                    Text(
                                        text = authUser.displayName ?: "Google User",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = authUser.email ?: "Connected",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Column {
                                    Text(
                                        text = "Offline Mode",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Local storage active.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Sync details
                    Column(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Status:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = when (syncState.status) {
                                    SyncStatus.SYNCING -> "Synchronizing..."
                                    SyncStatus.SYNCED -> "Up to date"
                                    SyncStatus.OFFLINE -> "Offline"
                                    SyncStatus.NOT_AUTHENTICATED -> "Signed out"
                                    SyncStatus.ERROR -> "Sync Warning"
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = iconColor
                            )
                        }

                        if (syncState.lastSyncTimestamp > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Last sync:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val df = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                                Text(
                                    text = df.format(Date(syncState.lastSyncTimestamp)),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        if (syncState.message != null) {
                            Text(
                                text = syncState.message,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (authUser != null) {
                    Button(
                        onClick = {
                            onTriggerSync()
                            showDialog = false
                        },
                        modifier = Modifier.testTag("dialog_sync_now_button")
                    ) {
                        Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sync Now")
                    }
                } else {
                    Button(
                        onClick = {
                            showDialog = false
                            onOpenSettings()
                        },
                        modifier = Modifier.testTag("dialog_go_to_settings_button")
                    ) {
                        Text("Sign In")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
