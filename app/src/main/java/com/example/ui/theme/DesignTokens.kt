package com.example.ui.theme

import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppHaptics
import kotlin.math.roundToInt

// ============================================================
// 1) 4PX / 8PX SPACING GRID TOKENS
// ============================================================
object Spacing {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 24.dp
    val xxxl: Dp = 32.dp
}

// ============================================================
// 2) SPRING MOTION SYSTEM TOKENS
// ============================================================
object SpringMotion {
    // Default interactive spring: 300 stiffness, 0.75 damping (snappy feel with subtle bounce)
    val Default = spring<Float>(
        dampingRatio = 0.75f,
        stiffness = Spring.StiffnessMedium
    )

    // Bouncy spring for modal appearances and FAB expansion
    val Bouncy = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    // Snappy spring for quick toggles and tabs
    val Snappy = spring<Float>(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessMedium
    )

    // Dp Spring
    val DpDefault = spring<Dp>(
        dampingRatio = 0.75f,
        stiffness = Spring.StiffnessMedium
    )
}

// ============================================================
// 3) REUSABLE SPRING PRESSABLE MODIFIER
// Scales down on press (0.97f) and springs back to 1.0f on release
// with optional subtle haptic feedback
// ============================================================
fun Modifier.springPressable(
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
    hapticFeedback: Boolean = true,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val view = LocalView.current

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1.0f,
        animationSpec = SpringMotion.Default,
        label = "springPressScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(
            if (onClick != null && enabled) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) {
                    if (hapticFeedback) {
                        AppHaptics.performTap(view)
                    }
                    onClick()
                }
            } else Modifier
        )
}

// ============================================================
// 4) CARD ELEVATION & SHADOW SYSTEM
// ============================================================
object CardElevations {
    val Resting = 0.5.dp
    val Hover = 2.dp
    val Modal = 8.dp
    val Floating = 6.dp
}

fun Modifier.softCardShadow(
    isDark: Boolean = false,
    shape: RoundedCornerShape = RoundedCornerShape(14.dp),
    elevation: Dp = CardElevations.Resting
): Modifier = this.shadow(
    elevation = elevation,
    shape = shape,
    spotColor = if (isDark) Color.Black.copy(alpha = 0.6f) else Color(0x150F172A),
    ambientColor = if (isDark) Color.Black.copy(alpha = 0.4f) else Color(0x0A0F172A)
)

// ============================================================
// 5) PER-DIGIT ROLLING ODOMETER BALANCE DISPLAY TEXT
// ============================================================
@Composable
fun AnimatedBalanceText(
    amount: Double,
    currencySymbol: String = "₹",
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    val formatted = remember(amount, currencySymbol) {
        com.example.ui.Formatters.formatCurrency(amount, currencySymbol)
    }
    var previousAmount by remember { mutableDoubleStateOf(amount) }
    val isIncreasing = amount >= previousAmount
    SideEffect {
        previousAmount = amount
    }
    val tabularStyle = remember(style) { style.tabularNumbers() }

    Row(
        modifier = modifier.animateContentSize(
            animationSpec = spring(
                dampingRatio = 0.82f,
                stiffness = 440f
            )
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        formatted.forEachIndexed { index, ch ->
            val reverseKey = formatted.length - 1 - index
            key(reverseKey) {
                if (ch.isDigit()) {
                    AnimatedContent(
                        targetState = ch,
                        transitionSpec = {
                            val rollUp = isIncreasing || targetState > initialState
                            val enterOffset: (Int) -> Int = { h -> if (rollUp) h else -h }
                            val exitOffset: (Int) -> Int = { h -> if (rollUp) -h else h }
                            (slideInVertically(
                                initialOffsetY = enterOffset,
                                animationSpec = spring(
                                    dampingRatio = 0.78f,
                                    stiffness = 480f
                                )
                            ) + fadeIn(animationSpec = tween(140))).togetherWith(
                                slideOutVertically(
                                    targetOffsetY = exitOffset,
                                    animationSpec = tween(140)
                                ) + fadeOut(animationSpec = tween(110))
                            )
                        },
                        modifier = Modifier.clipToBounds(),
                        label = "odometer_digit_$reverseKey"
                    ) { digitChar ->
                        Text(
                            text = digitChar.toString(),
                            style = tabularStyle,
                            color = color,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                } else {
                    Text(
                        text = ch.toString(),
                        style = tabularStyle,
                        color = color,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

// ============================================================
// 6) SWIPE-TO-REVEAL COMPONENT (Density-Aware iOS Mail-Style)
// Properly calculates Dp-to-Px widths so all action buttons
// (Delete, Edit, Remind on right; Pin, + Sale, + Got on left)
// are 100% visible and never hidden behind the cell.
// ============================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeRevealRow(
    modifier: Modifier = Modifier,
    swipeThreshold: Float = 140f,
    maxSwipeOffset: Float = 195f,
    onDelete: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onRemind: (() -> Unit)? = null,
    onPin: (() -> Unit)? = null,
    isPinned: Boolean = false,
    onQuickSale: (() -> Unit)? = null,
    onQuickDeposit: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val isDark = LocalIsDarkMode.current
    val view = LocalView.current

    // Calculate exact Dp width required to reveal ALL buttons on the right (swipe left)
    val rightTrayWidthDp = remember(onRemind != null, onEdit != null) {
        var width = 12.dp + 44.dp + 8.dp // end padding + Delete button + leading gap
        if (onEdit != null) width += 52.dp + 8.dp // Edit button + gap
        if (onRemind != null) width += 68.dp + 8.dp // Remind button + gap
        width
    }

    // Calculate exact Dp width required to reveal ALL buttons on the left (swipe right)
    val canSwipeRight = onQuickSale != null || onQuickDeposit != null || onPin != null
    val leftTrayWidthDp = remember(onPin != null, onQuickSale != null, onQuickDeposit != null) {
        var width = 12.dp // start padding
        if (onPin != null) width += 44.dp + 8.dp
        if (onQuickSale != null) width += 64.dp + 8.dp
        if (onQuickDeposit != null) width += 62.dp + 8.dp
        width
    }

    val rightSnapPx = with(density) { rightTrayWidthDp.toPx() }
    val leftSnapPx = with(density) { leftTrayWidthDp.toPx() }
    val maxRightOverscrollPx = with(density) { (rightTrayWidthDp + 28.dp).toPx() }
    val maxLeftOverscrollPx = with(density) { (leftTrayWidthDp + 28.dp).toPx() }
    val triggerThresholdPx = with(density) { 52.dp.toPx() }

    var offsetX by remember { mutableFloatStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
        label = "swipeRevealSpring"
    )
    var hasTriggeredHaptic by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
    ) {
        // Revealed Left-Side Actions Backdrop (Swipe Right Action Tray: Pin / + Sale / + Got)
        if (animatedOffsetX > 4f && canSwipeRight) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        if (isDark) Color(0xFF0F291E) else Color(0xFFECFDF5),
                        RoundedCornerShape(14.dp)
                    )
                    .padding(start = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onPin != null) {
                        Box(
                            modifier = Modifier
                                .size(width = 44.dp, height = 42.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(if (isPinned) Color(0xFFF59E0B) else Color(0xFF16A34A))
                                .clickable {
                                    AppHaptics.performConfirm(view)
                                    offsetX = 0f
                                    onPin()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = if (isPinned) "Unpin" else "Pin",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (onQuickSale != null) {
                        Box(
                            modifier = Modifier
                                .size(width = 64.dp, height = 42.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(DebitRed)
                                .clickable {
                                    AppHaptics.performTap(view)
                                    offsetX = 0f
                                    onQuickSale()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+ Sale", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    if (onQuickDeposit != null) {
                        Box(
                            modifier = Modifier
                                .size(width = 62.dp, height = 42.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(CreditGreen)
                                .clickable {
                                    AppHaptics.performTap(view)
                                    offsetX = 0f
                                    onQuickDeposit()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+ Got", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        // Revealed Right-Side Actions Backdrop (Swipe Left Action Tray: Remind / Edit / Delete)
        if (animatedOffsetX < -4f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        if (isDark) Color(0xFF2A1215) else Color(0xFFFEF2F2),
                        RoundedCornerShape(14.dp)
                    )
                    .padding(end = 10.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onRemind != null) {
                        Box(
                            modifier = Modifier
                                .size(width = 68.dp, height = 42.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(Color(0xFF0EA5E9))
                                .clickable {
                                    AppHaptics.performTap(view)
                                    offsetX = 0f
                                    onRemind()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Outlined.NotificationsActive,
                                    contentDescription = "Remind",
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text("Remind", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    if (onEdit != null) {
                        Box(
                            modifier = Modifier
                                .size(width = 52.dp, height = 42.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(if (isDark) Color(0xFF064E3B) else Green50)
                                .clickable {
                                    AppHaptics.performTap(view)
                                    offsetX = 0f
                                    onEdit()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = "Edit",
                                    tint = if (isDark) Color(0xFF6EE7B7) else Green700,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    "Edit",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color(0xFF6EE7B7) else Green700
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(width = 44.dp, height = 42.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(Color(0xFFEF4444))
                            .clickable {
                                AppHaptics.performConfirm(view)
                                offsetX = 0f
                                onDelete()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Delete",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Foreground Content Card with Density-Aware Drag Gestures
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .draggable(
                    state = rememberDraggableState { delta ->
                        val minBound = -maxRightOverscrollPx
                        val maxBound = if (canSwipeRight) maxLeftOverscrollPx else 0f
                        val newOffset = (offsetX + delta).coerceIn(minBound, maxBound)
                        offsetX = newOffset
                        if ((newOffset <= -triggerThresholdPx || (canSwipeRight && newOffset >= triggerThresholdPx)) && !hasTriggeredHaptic) {
                            AppHaptics.performSwipeThreshold(view)
                            hasTriggeredHaptic = true
                        } else if (newOffset > -triggerThresholdPx && newOffset < triggerThresholdPx && hasTriggeredHaptic) {
                            hasTriggeredHaptic = false
                        }
                    },
                    orientation = Orientation.Horizontal,
                    onDragStopped = {
                        when {
                            offsetX <= -triggerThresholdPx -> {
                                offsetX = -rightSnapPx
                            }
                            canSwipeRight && offsetX >= triggerThresholdPx -> {
                                offsetX = leftSnapPx
                            }
                            else -> {
                                offsetX = 0f
                                hasTriggeredHaptic = false
                            }
                        }
                    }
                )
        ) {
            content()
            // When swipe tray is open, tapping anywhere on the shifted card closes the tray cleanly
            if (offsetX != 0f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            AppHaptics.performTap(view)
                            offsetX = 0f
                            hasTriggeredHaptic = false
                        }
                )
            }
        }
    }
}

// ============================================================
// 7) UNIFIED BALANCED TOAST / NOTIFICATION BANNER
// Ensures every toast, snackbar, and undo bar in the entire app
// has identical, fixed proportions, symmetry, and visual polish.
// ============================================================
@Composable
fun UnifiedToastBanner(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    leadingIcon: ImageVector? = null,
    accentColor: Color? = null
) {
    val isDark = LocalIsDarkMode.current
    val view = LocalView.current

    val lowerMsg = remember(message) { message.lowercase() }
    val resolvedAccent = accentColor ?: remember(lowerMsg) {
        when {
            lowerMsg.contains("error") || lowerMsg.contains("fail") || lowerMsg.contains("invalid") || lowerMsg.contains("cannot") -> Color(0xFFF87171)
            lowerMsg.contains("delet") || lowerMsg.contains("remov") || lowerMsg.contains("trash") -> Color(0xFFFBBF24)
            lowerMsg.contains("pin") -> Color(0xFF34D399)
            lowerMsg.contains("sync") || lowerMsg.contains("cloud") || lowerMsg.contains("backup") || lowerMsg.contains("export") -> Color(0xFF38BDF8)
            else -> Color(0xFF34D399)
        }
    }

    val resolvedIcon = leadingIcon ?: remember(lowerMsg) {
        when {
            lowerMsg.contains("error") || lowerMsg.contains("fail") || lowerMsg.contains("invalid") || lowerMsg.contains("cannot") -> Icons.Filled.ErrorOutline
            lowerMsg.contains("pin") -> Icons.Filled.PushPin
            lowerMsg.contains("sync") || lowerMsg.contains("cloud") || lowerMsg.contains("backup") -> Icons.Filled.CloudDone
            lowerMsg.contains("delet") || lowerMsg.contains("remov") -> Icons.Filled.Info
            else -> Icons.Filled.CheckCircle
        }
    }

    val containerBg = if (isDark) Color(0xFF22252A) else Color(0xFF18181B)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.10f)

    Surface(
        modifier = modifier
            .widthIn(min = 280.dp, max = 420.dp)
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = Color.Black.copy(alpha = 0.35f)
            ),
        shape = RoundedCornerShape(16.dp),
        color = containerBg,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Balanced Left Icon Badge
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(resolvedAccent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = resolvedIcon,
                    contentDescription = null,
                    tint = resolvedAccent,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Balanced Center Message
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp
                ),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Optional Right Action Pill Button (e.g., Undo)
            if (!actionLabel.isNullOrBlank() && onActionClick != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = resolvedAccent.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, resolvedAccent.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            AppHaptics.performConfirm(view)
                            onActionClick()
                        }
                ) {
                    Text(
                        text = actionLabel,
                        color = resolvedAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

