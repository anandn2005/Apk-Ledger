package com.example.ui.components

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Reusable Motion & Compose Performance Utilities.
 * Provides unified spring press scaling, API-level backward compatible blur,
 * and high-performance GPU-accelerated graphicLayer transforms.
 */
object AppMotion {
    val SpringBouncy = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )

    val SpringSnappy = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )
}

/**
 * Universal press scale feedback for buttons, cards, and list rows.
 * Uses GPU-accelerated graphicsLayer scaleX/scaleY with spring easing.
 */
@Composable
fun Modifier.pressScale(
    targetScale: Float = 0.96f,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    enabled: Boolean = true
): Modifier {
    if (!enabled) return this
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) targetScale else 1f,
        animationSpec = AppMotion.SpringBouncy,
        label = "press_scale_anim"
    )

    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Reusable bounce-click modifier combining spring scale animation and haptic tick.
 */
@Composable
fun Modifier.bounceClick(
    targetScale: Float = 0.95f,
    hapticType: HapticFeedbackType? = HapticFeedbackType.TextHandleMove,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current

    return this
        .pressScale(targetScale = targetScale, interactionSource = interactionSource, enabled = enabled)
        .clickable(
            interactionSource = interactionSource,
            indication = ripple(bounded = true),
            enabled = enabled
        ) {
            hapticType?.let { haptic.performHapticFeedback(it) }
            onClick()
        }
}

/**
 * API 31+ (Android 12+) Safe Blur modifier with graceful fallback on older Android versions.
 * Prevents RenderEffect crashes/unsupported operations on API 24-30.
 */
fun Modifier.blurCompat(radius: Dp): Modifier {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && radius > 0.dp) {
        this.blur(radius)
    } else {
        this
    }
}
