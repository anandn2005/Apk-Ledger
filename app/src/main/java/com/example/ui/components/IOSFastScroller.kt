package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * iOS-Style Interactive Scrollbar Pill Component.
 * Optimized for 120 FPS:
 * - Zero recompositions during scroll (reads scroll fraction strictly inside the layout-phase .offset {} lambda).
 * - Uses Box + onSizeChanged instead of SubcomposeLayout (BoxWithConstraints).
 * - Uses distinctUntilChanged on isScrollInProgress so coroutines are not cancelled/relaunched on every pixel.
 */
@Composable
fun IosScrollbarPill(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    isDarkMode: Boolean = isSystemInDarkTheme()
) {
    val totalItems by remember(listState) {
        derivedStateOf { listState.layoutInfo.totalItemsCount }
    }
    val visibleItems by remember(listState) {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.size }
    }

    // If total items is empty or all items fit in visible viewport, no scrollbar needed
    if (totalItems <= 1 || visibleItems >= totalItems) return

    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var isVisible by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    var lastHapticIndex by remember { mutableIntStateOf(-1) }
    var trackHeightPx by remember { mutableFloatStateOf(0f) }

    // Observe only scroll start/stop transitions — ZERO per-pixel coroutine restarts during fling
    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow {
            listState.isScrollInProgress
        }.distinctUntilChanged().collect { inProgress ->
            if (inProgress) {
                isVisible = true
            } else if (!isDragging && isVisible) {
                delay(1800L)
                if (!listState.isScrollInProgress && !isDragging) {
                    isVisible = false
                }
            }
        }
    }

    // Fade out after drag release
    LaunchedEffect(isDragging) {
        if (!isDragging && isVisible && !listState.isScrollInProgress) {
            delay(1800L)
            if (!listState.isScrollInProgress && !isDragging) {
                isVisible = false
            }
        }
    }

    // Opacity animation: fade in 150ms, fade out 220ms
    val pillAlpha by animateFloatAsState(
        targetValue = if (isVisible || isDragging) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isVisible || isDragging) 150 else 220,
            easing = FastOutSlowInEasing
        ),
        label = "ios_pill_alpha"
    )

    // Helper function evaluated inside layout phase (.offset {}) to avoid composition-phase state reads
    val computeScrollFraction: () -> Float = remember(listState) {
        {
            val layoutInfo = listState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val visible = layoutInfo.visibleItemsInfo.size
            if (total <= visible || total <= 1) {
                0f
            } else {
                val firstIdx = listState.firstVisibleItemIndex
                val firstOff = listState.firstVisibleItemScrollOffset
                val itemSize = layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1
                val exactIndex = firstIdx + (firstOff.toFloat() / itemSize.coerceAtLeast(1))
                val maxScrollable = (total - visible).coerceAtLeast(1)
                (exactIndex / maxScrollable).coerceIn(0f, 1f)
            }
        }
    }

    val pillBackgroundColor = if (isDarkMode) Color(0xFF333336) else Color.White
    val triangleColor = if (isDarkMode) Color.White else Color(0xFF1A1A1A)
    val pillHeightDp = 48.dp
    val pillHeightPx = remember(density) { with(density) { pillHeightDp.toPx() } }

    if (pillAlpha > 0.01f || isDragging) {
        Box(
            modifier = modifier
                .fillMaxHeight()
                .width(52.dp)
                .onSizeChanged { trackHeightPx = it.height.toFloat() },
            contentAlignment = Alignment.TopEnd
        ) {
            val usableTrackHeightPx = (trackHeightPx - pillHeightPx).coerceAtLeast(1f)

            // Touch hit-box container (52dp wide hit area with right padding for pill button)
            Box(
                modifier = Modifier
                    .width(52.dp)
                    .height(pillHeightDp)
                    .offset {
                        val progress = if (isDragging) dragProgress else computeScrollFraction()
                        val yPx = (progress * usableTrackHeightPx).coerceIn(0f, usableTrackHeightPx)
                        IntOffset(x = 0, y = yPx.roundToInt())
                    }
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                        alpha = pillAlpha
                    }
                    .pointerInput(totalItems, usableTrackHeightPx) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            dragProgress = computeScrollFraction()
                            isDragging = true
                            isVisible = true
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) {
                                    isDragging = false
                                    break
                                }
                                val deltaY = change.position.y - change.previousPosition.y
                                if (deltaY != 0f) {
                                    change.consume()
                                    val newProgress = (dragProgress + (deltaY / usableTrackHeightPx)).coerceIn(0f, 1f)
                                    dragProgress = newProgress

                                    val targetIndex = (newProgress * (totalItems - 1)).roundToInt().coerceIn(0, totalItems - 1)
                                    if (targetIndex != lastHapticIndex) {
                                        lastHapticIndex = targetIndex
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }

                                    coroutineScope.launch {
                                        listState.scrollToItem(targetIndex)
                                    }
                                }
                            }
                            isDragging = false
                        }
                    },
                contentAlignment = Alignment.CenterEnd
            ) {
                val topTriPath = remember { Path() }
                val botTriPath = remember { Path() }

                Canvas(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .width(if (isDragging) 30.dp else 26.dp)
                        .height(pillHeightDp)
                        .shadow(
                            elevation = if (isDragging) 8.dp else 4.dp,
                            shape = RoundedCornerShape(percent = 50),
                            spotColor = Color(0x3D000000),
                            ambientColor = Color(0x1A000000)
                        )
                        .clip(RoundedCornerShape(percent = 50))
                ) {
                    val w = size.width
                    val h = size.height

                    // 1. White Stadium Capsule Background
                    val cornerRadiusPx = w / 2f
                    drawRoundRect(
                        color = pillBackgroundColor,
                        size = size,
                        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                    )

                    // 2. Solid Filled Triangles ▲ ▼ (reusing cached Path instances)
                    val centerX = w / 2f
                    val centerY = h / 2f

                    val triBase = (w * 0.38f).coerceIn(8.dp.toPx(), 11.dp.toPx())
                    val triHeight = triBase * 0.72f
                    val gap = 2.5.dp.toPx()

                    topTriPath.reset()
                    topTriPath.moveTo(centerX, centerY - gap - triHeight)
                    topTriPath.lineTo(centerX - triBase / 2f, centerY - gap)
                    topTriPath.lineTo(centerX + triBase / 2f, centerY - gap)
                    topTriPath.close()
                    drawPath(path = topTriPath, color = triangleColor)

                    botTriPath.reset()
                    botTriPath.moveTo(centerX - triBase / 2f, centerY + gap)
                    botTriPath.lineTo(centerX + triBase / 2f, centerY + gap)
                    botTriPath.lineTo(centerX, centerY + gap + triHeight)
                    botTriPath.close()
                    drawPath(path = botTriPath, color = triangleColor)
                }
            }
        }
    }
}

/**
 * Backward-compatible wrapper for existing usages.
 */
@Composable
fun IOSFastScroller(
    listState: LazyListState,
    totalItemsCount: Int = listState.layoutInfo.totalItemsCount,
    getItemCategoryOrInitial: (index: Int) -> String = { "" },
    modifier: Modifier = Modifier,
    isDarkMode: Boolean = isSystemInDarkTheme()
) {
    IosScrollbarPill(
        listState = listState,
        modifier = modifier,
        isDarkMode = isDarkMode
    )
}
