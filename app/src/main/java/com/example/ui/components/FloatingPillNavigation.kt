package com.example.ui.components

import com.example.ui.theme.StatusEmerald
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.CompositingStrategy
import com.example.ui.theme.SpringMotion

@Immutable
data class NavigationTabItem(
    val title: String,
    val outlinedIcon: ImageVector,
    val filledIcon: ImageVector,
    val contentDescription: String
)

/**
 * Premium Floating Navigation Container combining the pill-shaped tab menu
 * with an active "Ball Movement" indicator and a contextual Floating Action Button (+).
 * Isolated in its own offscreen hardware layer so scrolling lists underneath never trigger dock re-renders.
 */
@Composable
fun FloatingPillNavigation(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onFabClick: () -> Unit,
    isDarkMode: Boolean = false,
    isExpanded: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 12.dp, start = 12.dp, end = 12.dp)
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Floating Pill Navigation Bar
            FloatingPillNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
                isDarkMode = isDarkMode
            )

            // Contextual Action Button (+) - always visible across all tabs
            ContextualActionFab(
                activeTab = selectedTab,
                isDarkMode = isDarkMode,
                isExpanded = isExpanded,
                onClick = onFabClick
            )
        }
    }
}

/**
 * High-Performance Frosted Translucent Dock with Dynamic Active Label Expansion,
 * Reflective Specular Rim, Static Diffuse Shadow, and Offscreen Hardware Layer Isolation.
 */
@Composable
fun FloatingPillNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    isDarkMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val darkActive = isDarkMode
    val currentLang = com.example.ui.i18n.LocalAppLanguage.current

    val tabs = remember(currentLang) {
        listOf(
            NavigationTabItem(com.example.ui.i18n.AppStrings.navItems(currentLang), Icons.Outlined.Inventory2, Icons.Filled.Inventory2, com.example.ui.i18n.AppStrings.navItems(currentLang)),
            NavigationTabItem(com.example.ui.i18n.AppStrings.navCustomers(currentLang), Icons.Outlined.PeopleOutline, Icons.Filled.People, com.example.ui.i18n.AppStrings.navCustomers(currentLang)),
            NavigationTabItem(com.example.ui.i18n.AppStrings.navInvoices(currentLang), Icons.AutoMirrored.Outlined.ReceiptLong, Icons.AutoMirrored.Filled.ReceiptLong, com.example.ui.i18n.AppStrings.navInvoices(currentLang)),
            NavigationTabItem(com.example.ui.i18n.AppStrings.navSettings(currentLang), Icons.Outlined.Settings, Icons.Filled.Settings, com.example.ui.i18n.AppStrings.navSettings(currentLang))
        )
    }

    val inactiveTabWidth = 62.dp
    val activeTabWidth = 78.dp
    val tabSlotHeight = 52.dp
    val pillPadding = 4.dp

    // Swipe Gesture State
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    val bounceX = remember { Animatable(0f) }
    val thresholdPx = remember(density) { with(density) { 42.dp.toPx() } }

    // Live preview index during swipe
    val visualActiveTab = remember(selectedTab, dragAccumulator, thresholdPx) {
        val halfThreshold = thresholdPx * 0.4f
        when {
            dragAccumulator < -halfThreshold && selectedTab < tabs.lastIndex -> selectedTab + 1
            dragAccumulator > halfThreshold && selectedTab > 0 -> selectedTab - 1
            else -> selectedTab
        }
    }

    // Reflective Specular Rim: Fine 1.dp gradient border memoized by theme
    val specularRimBorder = remember(darkActive) {
        BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = if (darkActive) {
                    listOf(
                        Color.White.copy(alpha = 0.28f),
                        Color.White.copy(alpha = 0.08f)
                    )
                } else {
                    listOf(
                        Color.White.copy(alpha = 0.85f),
                        Color.White.copy(alpha = 0.30f)
                    )
                }
            )
        )
    }

    // High-performance frosted translucent color overlay (Color(0xF2FFFFFF) Light / Color(0xF0121212) Dark)
    // with static diffuse shadow — zero dynamic backdrop blur recalculation during scroll
    val containerColor = if (darkActive) {
        Color(0xF0121212)
    } else {
        Color(0xF2FFFFFF)
    }

    Surface(
        modifier = modifier
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                translationX = bounceX.value
            }
            .shadow(
                elevation = if (darkActive) 6.dp else 4.dp,
                shape = CircleShape,
                spotColor = if (darkActive) Color.Black.copy(alpha = 0.55f) else Color(0x240F172A),
                ambientColor = if (darkActive) Color.Black.copy(alpha = 0.35f) else Color(0x140F172A)
            )
            .border(
                border = specularRimBorder,
                shape = CircleShape
            )
            .pointerInput(selectedTab) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragAccumulator = 0f
                    },
                    onDragEnd = {
                        if (bounceX.value != 0f) {
                            coroutineScope.launch {
                                bounceX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                    )
                                )
                            }
                        } else {
                            if (dragAccumulator < -thresholdPx && selectedTab < tabs.lastIndex) {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                onTabSelected(selectedTab + 1)
                            } else if (dragAccumulator > thresholdPx && selectedTab > 0) {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                onTabSelected(selectedTab - 1)
                            }
                        }
                        dragAccumulator = 0f
                    },
                    onDragCancel = {
                        if (bounceX.value != 0f) {
                            coroutineScope.launch {
                                bounceX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                    )
                                )
                            }
                        }
                        dragAccumulator = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val isAtLeftBoundary = selectedTab == 0 && dragAmount > 0
                        val isAtRightBoundary = selectedTab == tabs.lastIndex && dragAmount < 0

                        if (isAtLeftBoundary || isAtRightBoundary) {
                            coroutineScope.launch {
                                val currentBounce = bounceX.value
                                val resisted = currentBounce + (dragAmount * 0.22f)
                                bounceX.snapTo(resisted.coerceIn(-30f, 30f))
                            }
                        } else {
                            dragAccumulator += dragAmount
                        }
                    }
                )
            }
            .clip(CircleShape),
        color = containerColor,
        shape = CircleShape
    ) {
        val activeBgColor = if (darkActive) Color(0xFF132A1C) else Color(0xFFEAF6ED)
        val activeBorder = if (darkActive) {
            BorderStroke(1.dp, Color(0xFF4CAF6E).copy(alpha = 0.38f))
        } else {
            BorderStroke(1.dp, Color(0xFF379358).copy(alpha = 0.32f))
        }
        val activeColor = if (darkActive) Color(0xFF4CAF6E) else Color(0xFF379358)
        val inactiveColor = if (darkActive) Color(0xFFA1A1AA) else Color(0xFF8E8E93)

        // Liquid sliding active pill horizontal offset (each preceding inactive tab is inactiveTabWidth + 2.dp gap)
        val targetPillOffset = (inactiveTabWidth + 2.dp) * visualActiveTab
        val animatedPillOffset by animateDpAsState(
            targetValue = targetPillOffset,
            animationSpec = spring(
                dampingRatio = 0.72f,
                stiffness = 420f
            ),
            label = "liquidPillOffset"
        )

        Box(
            modifier = Modifier
                .padding(pillPadding)
                .height(tabSlotHeight)
        ) {
            // Gliding Liquid Pill Indicator
            Box(
                modifier = Modifier
                    .offset(x = animatedPillOffset)
                    .width(activeTabWidth)
                    .height(tabSlotHeight)
                    .clip(CircleShape)
                    .background(activeBgColor, CircleShape)
                    .border(activeBorder, CircleShape)
            )

            Row(
                modifier = Modifier.height(tabSlotHeight),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                tabs.forEachIndexed { index, tab ->
                    val isSelected = visualActiveTab == index

                    val targetWidth = if (isSelected) activeTabWidth else inactiveTabWidth
                    val animatedWidth by animateDpAsState(
                        targetValue = targetWidth,
                        animationSpec = spring(
                            dampingRatio = 0.74f,
                            stiffness = 420f
                        ),
                        label = "tabWidth_${tab.title}"
                    )

                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val tabScale by animateFloatAsState(
                        targetValue = when {
                            isPressed -> 0.90f
                            isSelected -> 1.0f
                            else -> 0.98f
                        },
                        animationSpec = spring(dampingRatio = 0.62f, stiffness = 500f),
                        label = "tabPressScale"
                    )

                    val iconPopScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.12f else 1.0f,
                        animationSpec = spring(dampingRatio = 0.55f, stiffness = 460f),
                        label = "iconPopScale_${tab.title}"
                    )

                    val iconLiftY by animateFloatAsState(
                        targetValue = if (isSelected) -1.5f else 0f,
                        animationSpec = spring(dampingRatio = 0.65f, stiffness = 420f),
                        label = "iconLiftY_${tab.title}"
                    )

                    Box(
                        modifier = Modifier
                            .width(animatedWidth)
                            .height(tabSlotHeight)
                            .graphicsLayer {
                                scaleX = tabScale
                                scaleY = tabScale
                            }
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) {
                                if (selectedTab != index) {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    onTabSelected(index)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (isSelected) tab.filledIcon else tab.outlinedIcon,
                                contentDescription = tab.contentDescription,
                                tint = if (isSelected) activeColor else inactiveColor,
                                modifier = Modifier
                                    .size(20.dp)
                                    .graphicsLayer {
                                        scaleX = iconPopScale
                                        scaleY = iconPopScale
                                        translationY = iconLiftY
                                    }
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = tab.title,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) activeColor else inactiveColor,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Clip
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Contextual Floating Action Button with Refined Accent Color,
 * Specular Rim, Subtle Elevation, and Tactile Spring Feedback.
 */
@Composable
fun ContextualActionFab(
    activeTab: Int,
    isDarkMode: Boolean = false,
    isExpanded: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val darkActive = isDarkMode

    // Tactile micro-interaction: scale(0.88) with spring bounce
    val fabScale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else if (isExpanded) 1.05f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.56f,
            stiffness = 480f
        ),
        label = "appleFabTactileScale"
    )

    // Rotation angle for FAB '+' icon when speed dial is expanded or tab changes
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded && activeTab != 3) 135f else (activeTab * 90f),
        animationSpec = spring(
            dampingRatio = 0.68f,
            stiffness = 380f
        ),
        label = "fabRotation"
    )

    // Specular border for FAB
    val fabSpecularBorder = BorderStroke(
        1.dp,
        if (darkActive) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.65f)
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = fabScale
                scaleY = fabScale
            }
            .size(50.dp)
            .shadow(
                elevation = if (isExpanded) 8.dp else 4.dp,
                shape = CircleShape,
                spotColor = StatusEmerald.copy(alpha = 0.38f)
            )
            .border(
                border = fabSpecularBorder,
                shape = CircleShape
            )
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF43A867),
                        StatusEmerald
                    )
                ),
                shape = CircleShape
            )
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onClick()
                }
            )
            .testTag("contextual_action_fab"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Quick Add Action",
            tint = Color.White,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    rotationZ = rotationAngle
                }
        )
    }
}

/**
 * Contextual modal popup for the 'Ledger' tab, allowing the user to select
 * between recording a transaction/entry or adding a new customer/person.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionOrPersonSheet(
    onDismiss: () -> Unit,
    onAddTransaction: () -> Unit,
    onAddPerson: () -> Unit,
    onGenerateReceipt: () -> Unit = {}
) {
    BackHandler { onDismiss() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { false }
        ),
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Surface(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                shape = CircleShape
            ) {
                Box(modifier = Modifier.size(width = 40.dp, height = 4.dp))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            Text(
                text = "Add Entry",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Option 1: Record Transaction / Payment
            Surface(
                onClick = {
                    onDismiss()
                    onAddTransaction()
                },
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Receipt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Transaction",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Option 2: Add New Customer / Party
            Surface(
                onClick = {
                    onDismiss()
                    onAddPerson()
                },
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFDCFCE7), // Soft emerald tint
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = null,
                                tint = Color(0xFF16A34A),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Customer",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Option 3: Generate Receipt / Invoice
            Surface(
                onClick = {
                    onDismiss()
                    onGenerateReceipt()
                },
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Generate Receipt",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
