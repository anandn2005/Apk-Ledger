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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.RateItemEntity
import com.example.ui.theme.LocalIsDarkMode
import org.json.JSONArray
import java.util.Locale
import kotlin.math.abs

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

/**
 * Lightweight pre-computed price trend model for RateItem mini sparkline badges.
 * Points are ordered chronologically (oldest -> newest).
 */
@Immutable
data class PriceTrendSummary(
    val points: List<Double>,
    val delta: Double,
    val isUp: Boolean,
    val isFlat: Boolean
)

fun computePriceTrend(item: RateItemEntity): PriceTrendSummary? {
    fun extractPrices(json: String): List<Pair<Long, Double>> {
        if (json.isBlank() || json == "[]") return emptyList()
        return try {
            val arr = JSONArray(json)
            val raw = ArrayList<Pair<Long, Double>>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val ts = obj.optLong("date", 0L)
                val sell = obj.optDouble("sellPrice", 0.0)
                val buy = obj.optDouble("buyRate", 0.0)
                val value = if (sell > 0.0) sell else buy
                if (value > 0.0) {
                    raw.add(ts to value)
                }
            }
            raw.distinctBy { "${it.second}_${it.first / 3000L}" }
                .sortedBy { it.first }
                .takeLast(8)
        } catch (_: Exception) {
            emptyList()
        }
    }

    var chrono = extractPrices(item.priceHistoryJson)
    if (chrono.size < 2 && item.variants.isNotEmpty()) {
        for (v in item.variants) {
            val vChrono = extractPrices(v.priceHistoryJson)
            if (vChrono.size >= 2) {
                chrono = vChrono
                break
            }
        }
    }
    if (chrono.size < 2) return null

    val values = chrono.map { it.second }
    val previous = values[values.size - 2]
    val latest = values.last()
    val delta = latest - previous
    val isFlat = abs(delta) < 0.01
    return PriceTrendSummary(
        points = values,
        delta = delta,
        isUp = delta > 0.0,
        isFlat = isFlat
    )
}

/**
 * Signature Motion Canvas mini price-trend sparkline badge for RateItemCard rows.
 */
@Composable
fun PriceTrendMiniBadge(
    trend: PriceTrendSummary,
    currencySymbol: String,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkMode.current
    val strokeColor = when {
        trend.isFlat -> if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
        trend.isUp -> if (isDark) Color(0xFF34D399) else Color(0xFF059669)
        else -> if (isDark) Color(0xFFF87171) else Color(0xFFDC2626)
    }
    val pillBg = when {
        trend.isFlat -> if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
        trend.isUp -> if (isDark) Color(0xFF064E3B).copy(alpha = 0.55f) else Color(0xFFECFDF5)
        else -> if (isDark) Color(0xFF7F1D1D).copy(alpha = 0.45f) else Color(0xFFFEF2F2)
    }
    val borderColor = strokeColor.copy(alpha = if (isDark) 0.4f else 0.25f)

    val formattedDelta = remember(trend.delta, trend.isFlat, currencySymbol) {
        if (trend.isFlat) {
            "Stable"
        } else {
            val absVal = abs(trend.delta)
            val numStr = if (absVal % 1.0 < 0.01 || absVal % 1.0 > 0.99) {
                Math.round(absVal).toString()
            } else {
                String.format(Locale.US, "%.1f", absVal)
            }
            val arrow = if (trend.isUp) "↗ +" else "↘ -"
            "$arrow$currencySymbol$numStr"
        }
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = pillBg,
        border = BorderStroke(0.6.dp, borderColor),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .width(26.dp)
                    .height(10.dp)
            ) {
                val pts = trend.points
                if (pts.size < 2) return@Canvas
                val minV = pts.minOrNull() ?: 0.0
                val maxV = pts.maxOrNull() ?: 0.0
                val span = (maxV - minV).coerceAtLeast(0.001)
                val padY = 1.5.dp.toPx()
                val usableH = (size.height - padY * 2f).coerceAtLeast(2f)
                val stepX = size.width / (pts.size - 1).coerceAtLeast(1)

                val linePath = Path()
                val fillPath = Path()
                var lastX = 0f
                var lastY = size.height / 2f

                pts.forEachIndexed { i, value ->
                    val x = i * stepX
                    val normalized = if (maxV - minV < 0.001) 0.5f else ((value - minV) / span).toFloat()
                    val y = padY + usableH * (1f - normalized)
                    if (i == 0) {
                        linePath.moveTo(x, y)
                        fillPath.moveTo(x, size.height)
                        fillPath.lineTo(x, y)
                    } else {
                        val midX = (lastX + x) / 2f
                        linePath.cubicTo(midX, lastY, midX, y, x, y)
                        fillPath.cubicTo(midX, lastY, midX, y, x, y)
                    }
                    lastX = x
                    lastY = y
                }
                fillPath.lineTo(lastX, size.height)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(strokeColor.copy(alpha = 0.28f), Color.Transparent)
                    )
                )
                drawPath(
                    path = linePath,
                    color = strokeColor,
                    style = Stroke(
                        width = 1.4.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
                drawCircle(
                    color = strokeColor,
                    radius = 1.8.dp.toPx(),
                    center = Offset(lastX, lastY)
                )
            }

            Text(
                text = formattedDelta,
                fontSize = 9.5.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Bold,
                color = strokeColor,
                maxLines = 1
            )
        }
    }
}

