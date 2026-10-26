package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

import androidx.compose.ui.graphics.graphicsLayer

/**
 * Renders a soft, static gradient shadow overlay when a LazyColumn or Scrollable
 * is scrolled, using memoized Brush and GPU graphicsLayer alpha to avoid recomposition churn.
 */
@Composable
fun ScrollEdgeShadow(
    isScrolled: Boolean,
    modifier: Modifier = Modifier,
    isTop: Boolean = true,
    shadowHeight: Dp = 16.dp,
    baseColor: Color = MaterialTheme.colorScheme.background
) {
    val shadowAlpha by animateFloatAsState(
        targetValue = if (isScrolled) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "ScrollShadowAlpha"
    )

    val gradientBrush = remember(isTop, baseColor) {
        val gradientColors = if (isTop) {
            listOf(
                baseColor.copy(alpha = 0.95f),
                baseColor.copy(alpha = 0.60f),
                baseColor.copy(alpha = 0.20f),
                Color.Transparent
            )
        } else {
            listOf(
                Color.Transparent,
                baseColor.copy(alpha = 0.20f),
                baseColor.copy(alpha = 0.60f),
                baseColor.copy(alpha = 0.95f)
            )
        }
        Brush.verticalGradient(colors = gradientColors)
    }

    if (shadowAlpha > 0.01f) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(shadowHeight)
                .graphicsLayer { alpha = shadowAlpha }
                .background(gradientBrush)
        )
    }
}

/**
 * Convenient wrapper for LazyListState to determine top scroll state
 */
@Composable
fun rememberCanScrollBack(lazyListState: LazyListState): Boolean {
    return remember(lazyListState) {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0
        }
    }.value
}

/**
 * Convenient wrapper for ScrollState to determine top scroll state
 */
@Composable
fun rememberCanScrollBack(scrollState: ScrollState): Boolean {
    return remember(scrollState) {
        derivedStateOf {
            scrollState.value > 0
        }
    }.value
}
