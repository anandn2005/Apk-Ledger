package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CallMade
import androidx.compose.material.icons.outlined.CallReceived
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val AccentGreen = Color(0xFF379358)
private val TextPrimary = Color(0xFF1C1C1E)

/**
 * Expandable Speed-Dial FAB for LedgerMate.
 * Animates 3 mini-FABs upward with label chips on tap.
 */
@Composable
fun ExpandableSpeedDialFab(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onAddCustomerClick: () -> Unit,
    onAddSaleClick: () -> Unit,
    onAddDepositClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    // System Back Handler to collapse menu
    if (isExpanded) {
        BackHandler {
            onToggleExpand()
        }
    }

    // Staggered visibility for mini-FAB items
    var item1Visible by remember { mutableStateOf(false) } // Add Customer
    var item2Visible by remember { mutableStateOf(false) } // Add Sale
    var item3Visible by remember { mutableStateOf(false) } // Add Deposit

    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            item3Visible = true // Add Deposit pops up first (closest to main FAB)
            delay(45)
            item2Visible = true // Add Sale
            delay(45)
            item1Visible = true // Add Customer (topmost)
        } else {
            item1Visible = false
            item2Visible = false
            item3Visible = false
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomEnd
    ) {
        // Fullscreen Scrim Backdrop when expanded
        if (isExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.22f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onToggleExpand()
                    }
            )
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(end = 16.dp, bottom = 80.dp)
        ) {
            // 1. Add Customer (Topmost Mini-FAB)
            AnimatedSpeedDialRow(
                visible = item1Visible,
                label = "Add Customer",
                icon = Icons.Outlined.PersonAdd,
                testTag = "speed_dial_add_customer",
                onClick = {
                    onToggleExpand()
                    onAddCustomerClick()
                }
            )

            // 2. Add Sale (Middle Mini-FAB)
            AnimatedSpeedDialRow(
                visible = item2Visible,
                label = "Add Sale",
                icon = Icons.Outlined.CallMade,
                testTag = "speed_dial_add_sale",
                onClick = {
                    onToggleExpand()
                    onAddSaleClick()
                }
            )

            // 3. Add Deposit (Bottom Mini-FAB)
            AnimatedSpeedDialRow(
                visible = item3Visible,
                label = "Add Deposit",
                icon = Icons.Outlined.CallReceived,
                testTag = "speed_dial_add_deposit",
                onClick = {
                    onToggleExpand()
                    onAddDepositClick()
                }
            )
        }
    }
}

@Composable
private fun AnimatedSpeedDialRow(
    visible: Boolean,
    label: String,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f)
        ) + fadeIn(animationSpec = tween(150)) + scaleIn(
            initialScale = 0.7f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f)
        ),
        exit = slideOutVertically(
            targetOffsetY = { it / 2 },
            animationSpec = tween(100)
        ) + fadeOut(animationSpec = tween(100)) + scaleOut(targetScale = 0.7f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Label Chip (White Pill with Soft Shadow)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onClick()
                    }
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }

            // Mini-FAB (44dp Circle with White Background & Accent Green Icon)
            Surface(
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 5.dp,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onClick()
                    }
                    .testTag(testTag)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = AccentGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
