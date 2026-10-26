package com.example.ui.components

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Reusable Flagship Motion & Compose Performance Utilities.
 * Provides unified spring press scaling, odometer number transitions,
 * breathing status indicators, and GPU-accelerated graphicsLayer transforms.
 */
object AppMotion {
    val SpringBouncy = spring<Float>(
        dampingRatio = 0.62f,
        stiffness = 520f
    )

    val SpringSnappy = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    val SpringFluid = spring<Float>(
        dampingRatio = 0.76f,
        stiffness = 380f
    )
}

/**
 * Universal press scale feedback for buttons, cards, and list rows.
 * Uses GPU-accelerated graphicsLayer scaleX/scaleY with spring easing.
 */
@Composable
fun Modifier.pressScale(
    targetScale: Float = 0.95f,
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
    targetScale: Float = 0.94f,
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
 * API 31+ (Android 12+) Safe Hardware-Cached Blur modifier with graceful fallback on older Android versions.
 * Isolated inside CompositingStrategy.Offscreen so it never recalculates against active scroll containers.
 */
fun Modifier.blurCompat(radius: Dp): Modifier {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && radius > 0.dp) {
        this
            .graphicsLayer {
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
            }
            .blur(radius)
    } else {
        this
    }
}

/**
 * Smooth Odometer / Vertical Rolling Text transition for counters, badges, and summary totals.
 */
@Composable
fun AnimatedRollingText(
    text: String,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            if (targetState > initialState) {
                (slideInVertically(
                    initialOffsetY = { height -> height / 2 },
                    animationSpec = spring(dampingRatio = 0.75f, stiffness = 420f)
                ) + fadeIn(tween(150))).togetherWith(
                    slideOutVertically(
                        targetOffsetY = { height -> -height / 2 },
                        animationSpec = tween(130)
                    ) + fadeOut(tween(120))
                )
            } else {
                (slideInVertically(
                    initialOffsetY = { height -> -height / 2 },
                    animationSpec = spring(dampingRatio = 0.75f, stiffness = 420f)
                ) + fadeIn(tween(150))).togetherWith(
                    slideOutVertically(
                        targetOffsetY = { height -> height / 2 },
                        animationSpec = tween(130)
                    ) + fadeOut(tween(120))
                )
            }
        },
        label = "animatedRollingText",
        modifier = modifier
    ) { targetText ->
        Text(
            text = targetText,
            style = style,
            color = color,
            maxLines = 1
        )
    }
}

/**
 * Breathing live status indicator dot with a soft pulsing halo.
 */
@Composable
fun PulsingStatusDot(
    color: Color = Color(0xFF10B981),
    dotSize: Dp = 8.dp,
    isPulsing: Boolean = true,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "statusPulse")
    val haloScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isPulsing) 1.85f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "haloScale"
    )
    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = if (isPulsing) 0.45f else 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "haloAlpha"
    )

    Box(
        modifier = modifier.size(dotSize * 2f),
        contentAlignment = Alignment.Center
    ) {
        if (isPulsing) {
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .graphicsLayer {
                        scaleX = haloScale
                        scaleY = haloScale
                        alpha = haloAlpha
                    }
                    .clip(CircleShape)
                    .background(color)
            )
        }
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(color)
        )
    }
}

/**
 * Vector-drawn official 4-color Google "G" emblem for Sign in with Google buttons.
 */
@Composable
fun GoogleLogoIcon(
    modifier: Modifier = Modifier.size(20.dp)
) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.20f
        val diameter = size.minDimension - strokeWidth
        val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
        val arcSize = Size(diameter, diameter)
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Butt)

        // Red top arc
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = -145f,
            sweepAngle = 105f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke
        )
        // Yellow left arc
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 145f,
            sweepAngle = 70f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke
        )
        // Green bottom arc
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 45f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke
        )
        // Blue right arc
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = -12f,
            sweepAngle = 57f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke
        )
        // Blue horizontal crossbar
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(size.width * 0.50f, size.height * 0.50f),
            end = Offset(size.width * 0.92f, size.height * 0.50f),
            strokeWidth = strokeWidth * 0.92f,
            cap = StrokeCap.Butt
        )
    }
}
