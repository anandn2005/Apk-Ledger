package com.example.ui.theme

import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
// 5) TABULAR BALANCE / NUMBER DISPLAY TEXT (120 FPS Zero-Jank)
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

    Text(
        text = formatted,
        style = style.tabularNumbers(),
        color = color,
        maxLines = 1,
        modifier = modifier
    )
}

// ============================================================
// 6) SWIPE-TO-REVEAL COMPONENT (iOS Mail-Style with spring resistance)
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
    onQuickSale: (() -> Unit)? = null,
    onQuickDeposit: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "swipeRevealSpring"
    )
    val view = LocalView.current
    var hasTriggeredHaptic by remember { mutableStateOf(false) }
    val canSwipeRight = onQuickSale != null || onQuickDeposit != null

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
    ) {
        // Revealed Left-Side Actions Backdrop (Swipe Right Action Tray: + Sale / + Got)
        if (animatedOffsetX > 10f && canSwipeRight) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0xFFF0FDF4), RoundedCornerShape(14.dp))
                    .padding(start = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onQuickSale != null) {
                        Box(
                            modifier = Modifier
                                .height(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DebitRed)
                                .clickable {
                                    AppHaptics.performTap(view)
                                    offsetX = 0f
                                    onQuickSale()
                                }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+ Sale", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    if (onQuickDeposit != null) {
                        Box(
                            modifier = Modifier
                                .height(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(CreditGreen)
                                .clickable {
                                    AppHaptics.performTap(view)
                                    offsetX = 0f
                                    onQuickDeposit()
                                }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+ Got", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        // Revealed Right-Side Actions Backdrop (Swipe Left Action Tray: Remind / Edit / Delete)
        if (animatedOffsetX < -10f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0xFFFEF2F2), RoundedCornerShape(14.dp))
                    .padding(end = 12.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onRemind != null) {
                        Box(
                            modifier = Modifier
                                .height(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0EA5E9))
                                .clickable {
                                    AppHaptics.performTap(view)
                                    offsetX = 0f
                                    onRemind()
                                }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Remind", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    if (onEdit != null) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Green50)
                                .clickable {
                                    AppHaptics.performTap(view)
                                    offsetX = 0f
                                    onEdit()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Edit", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Green700)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
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

        // Foreground Content Card with Draggable Drag Gestures & Rubber Band Resistance
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .draggable(
                    state = rememberDraggableState { delta ->
                        val minBound = -maxSwipeOffset
                        val maxBound = if (canSwipeRight) maxSwipeOffset else 0f
                        val newOffset = (offsetX + delta).coerceIn(minBound, maxBound)
                        offsetX = newOffset
                        if ((newOffset <= -swipeThreshold || (canSwipeRight && newOffset >= swipeThreshold)) && !hasTriggeredHaptic) {
                            AppHaptics.performSwipeThreshold(view)
                            hasTriggeredHaptic = true
                        } else if (newOffset > -swipeThreshold && newOffset < swipeThreshold && hasTriggeredHaptic) {
                            hasTriggeredHaptic = false
                        }
                    },
                    orientation = Orientation.Horizontal,
                    onDragStopped = {
                        when {
                            offsetX <= -swipeThreshold * 0.70f -> {
                                offsetX = if (onRemind != null) -180f else -swipeThreshold
                            }
                            canSwipeRight && offsetX >= swipeThreshold * 0.70f -> {
                                offsetX = swipeThreshold
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
        }
    }
}
