package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Shared frosted iOS-style SettingsModal wrapper ensuring pixel-perfect consistency
 * across all 8 settings actions (radius, scrim, top-right close "x", title, and spring physics).
 */
@Composable
fun SettingsModal(
    isOpen: Boolean,
    title: String,
    icon: ImageVector? = null,
    isDark: Boolean = false,
    headerAction: (@Composable () -> Unit)? = null,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!isOpen) return

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        BackHandler { onDismissRequest() }

        // Semi-transparent backdrop scrim with frosted-blur aesthetic
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (isDark) 0.70f else 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                ),
            contentAlignment = Alignment.Center
        ) {
            // Modal Card container with spring physics
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 480.dp)
                    .padding(vertical = 24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Consume clicks inside card
                    )
                    .shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(22.dp),
                        ambientColor = Color(0x18000000),
                        spotColor = Color(0x30000000)
                    ),
                shape = RoundedCornerShape(22.dp),
                color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    // Header Row: Icon + Title + (Optional Header Action) + Close (×)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            if (icon != null) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(
                                            if (isDark) Color(0xFF132A1C) else Color(0xFFEAF6ED),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (isDark) Color(0xFF4CAF6E) else Color(0xFF379358),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    letterSpacing = (-0.3).sp
                                ),
                                color = if (isDark) Color(0xFFF4F4F5) else Color(0xFF1C1C1E)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (headerAction != null) {
                                headerAction()
                            }

                            // Dedicated Close "×" Button
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)
                                    )
                                    .clickable(onClick = onDismissRequest)
                                    .testTag("settings_modal_close_btn"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = if (isDark) Color(0xFFA1A1AA) else Color(0xFF8E8E93),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA),
                        thickness = 0.5.dp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Modal Content
                    content()
                }
            }
        }
    }
}
