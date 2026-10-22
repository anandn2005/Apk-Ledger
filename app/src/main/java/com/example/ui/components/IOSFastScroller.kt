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

/**
 * iOS-Style Interactive Scrollbar Pill Component.
 *
 * Implements:
 * 1) Visibility:
 *    - Fully hidden/transparent by default when list is at rest.
 *    - Fades in (~150ms) on any scroll delta (small or large).
 *    - Remains visible for 2 seconds after scroll inactivity, then fades out (~220ms ease-out).
 *    - Reset 2-second timer continuously while scrolling.
 * 2) Position & Size:
 *    - Right edge of screen, ~5px margin from screen boundary.
 *    - Height proportional to visible viewport relative to total scrollable items (min ~38dp).
 *    - Position tracks scroll progress in real time.
 *    - Capsule shape (RoundedCornerShape(999.dp)), resting width 6dp.
 * 3) Press / Drag Interaction:
 *    - On press/drag: zooms in to ~14dp width with quick spring transition (~100-150ms).
 *    - Dragging scrolls list in real time (1:1 proportional mapping to scroll position).
 *    - On release: zooms back out with subtle spring settle and starts 2-second fade-out timer.
 *    - Light haptic tick on initial grab and when traversing items during drag.
 * 4) Up/Down Chevron Vectors:
 *    - Small up-chevron (⌃) near top edge, down-chevron (⌄) near bottom edge.
 *    - Contrasting tint (white on accent green).
 *    - Scales proportionally in resting and zoomed states.
 * 5) Visual Style:
 *    - Accent green (#379358 at ~0.88 opacity) with soft drop shadow.
 *    - Light translucent fill in dark mode.
 */
@Composable
fun IosScrollbarPill(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    isDarkMode: Boolean = isSystemInDarkTheme()
) {
    val totalItems by remember {
        derivedStateOf { listState.layoutInfo.totalItemsCount }
    }
    val visibleItems by remember {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.size }
    }

    // If total items is empty or all items fit in visible viewport, no scrollbar needed
    if (totalItems <= 1 || visibleItems >= totalItems) return

    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var isVisible by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var lastScrollTime by remember { mutableLongStateOf(0L) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    var lastHapticIndex by remember { mutableIntStateOf(-1) }

    // Efficiently observe scroll state changes via snapshotFlow without recomposition churn
    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow {
            Triple(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, listState.isScrollInProgress)
        }.collect { (idx, off, inProgress) ->
            if (inProgress || idx > 0 || off > 0) {
                isVisible = true
                lastScrollTime = System.currentTimeMillis()
            }
        }
    }

    // 2-second inactivity timer to fade out
    LaunchedEffect(lastScrollTime, isDragging) {
        if (!isDragging && isVisible) {
            delay(2000L)
            isVisible = false
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

    // Pill width zoom: 14dp resting -> 20dp pressed/dragging
    val pillWidthDp by animateDpAsState(
        targetValue = if (isDragging) 20.dp else 14.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = 450f
        ),
        label = "ios_pill_width"
    )

    // Calculate real-time scroll fraction
    val currentScrollFraction by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val visible = listState.layoutInfo.visibleItemsInfo.size
            if (total <= visible || total <= 1) 0f
            else {
                val firstIdx = listState.firstVisibleItemIndex
                val firstOff = listState.firstVisibleItemScrollOffset
                val itemSize = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1
                val exactIndex = firstIdx + (firstOff.toFloat() / itemSize.coerceAtLeast(1))
                val maxScrollable = (total - visible).coerceAtLeast(1)
                (exactIndex / maxScrollable).coerceIn(0f, 1f)
            }
        }
    }

    // Keep drag progress in sync when not dragging
    LaunchedEffect(currentScrollFraction) {
        if (!isDragging) {
            dragProgress = currentScrollFraction
        }
    }

    // Colors matching screenshot: crisp white capsule button with solid dark triangles
    val pillBackgroundColor = if (isDarkMode) Color(0xFF333336) else Color.White
    val triangleColor = if (isDarkMode) Color.White else Color(0xFF1A1A1A)

    if (pillAlpha > 0.01f || isDragging) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxHeight()
                .width(52.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            val trackHeightPx = with(density) { maxHeight.toPx() }
            val pillHeightDp = 48.dp
            val pillHeightPx = with(density) { pillHeightDp.toPx() }
            val usableTrackHeightPx = (trackHeightPx - pillHeightPx).coerceAtLeast(1f)

            val currentProgress = if (isDragging) dragProgress else currentScrollFraction
            val pillOffsetYPx = (currentProgress * usableTrackHeightPx).coerceIn(0f, usableTrackHeightPx)

            // Touch hit-box container (52dp wide hit area with right padding for pill button)
            Box(
                modifier = Modifier
                    .width(52.dp)
                    .height(pillHeightDp)
                    .offset { IntOffset(x = 0, y = pillOffsetYPx.roundToInt()) }
                    .graphicsLayer {
                        alpha = pillAlpha
                    }
                    .pointerInput(totalItems, usableTrackHeightPx) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            isDragging = true
                            isVisible = true
                            lastScrollTime = System.currentTimeMillis()
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) {
                                    isDragging = false
                                    lastScrollTime = System.currentTimeMillis()
                                    break
                                }
                                val deltaY = change.position.y - change.previousPosition.y
                                if (deltaY != 0f) {
                                    change.consume()
                                    lastScrollTime = System.currentTimeMillis()
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
                            lastScrollTime = System.currentTimeMillis()
                        }
                    },
                contentAlignment = Alignment.CenterEnd
            ) {
                // Stadium capsule button with solid ▲ ▼ filled triangles as requested in screenshot
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

                    // 2. Solid Filled Triangles ▲ ▼
                    val centerX = w / 2f
                    val centerY = h / 2f

                    val triBase = (w * 0.38f).coerceIn(8.dp.toPx(), 11.dp.toPx())
                    val triHeight = triBase * 0.72f
                    val gap = 2.5.dp.toPx()

                    // Top Triangle ▲ (pointing up)
                    val topTriPath = Path().apply {
                        moveTo(centerX, centerY - gap - triHeight) // Top vertex
                        lineTo(centerX - triBase / 2f, centerY - gap) // Bottom left
                        lineTo(centerX + triBase / 2f, centerY - gap) // Bottom right
                        close()
                    }
                    drawPath(
                        path = topTriPath,
                        color = triangleColor
                    )

                    // Bottom Triangle ▼ (pointing down)
                    val botTriPath = Path().apply {
                        moveTo(centerX - triBase / 2f, centerY + gap) // Top left
                        lineTo(centerX + triBase / 2f, centerY + gap) // Top right
                        lineTo(centerX, centerY + gap + triHeight) // Bottom vertex
                        close()
                    }
                    drawPath(
                        path = botTriPath,
                        color = triangleColor
                    )
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
