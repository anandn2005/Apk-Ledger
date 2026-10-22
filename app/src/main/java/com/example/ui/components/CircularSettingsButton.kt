package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.springPressable

/**
 * Circular iOS Shortcuts / Control Center style action widget for Settings.
 * Displays a 64-68dp circle with soft green background and accent green icon,
 * accompanied by a centered 2-line label underneath.
 */
@Composable
fun CircularSettingsButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = false,
    badgeText: String? = null,
    testTag: String = ""
) {
    val circleBg = if (isDark) Color(0xFF132A1C) else Color(0xFFEAF6ED)
    val circleBorder = if (isDark) Color(0xFF1F4D2F) else Color(0xFFD2EBD9)
    val iconTint = if (isDark) Color(0xFF4CAF6E) else Color(0xFF379358)
    val labelColor = if (isDark) Color(0xFFE4E4E7) else Color(0xFF2C2C2E)

    Column(
        modifier = modifier
            .widthIn(min = 68.dp, max = 84.dp)
            .then(if (testTag.isNotBlank()) Modifier.testTag(testTag) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .shadow(
                        elevation = 3.dp,
                        shape = CircleShape,
                        ambientColor = Color(0x0A000000),
                        spotColor = Color(0x14000000)
                    )
                    .clip(CircleShape)
                    .background(circleBg)
                    .border(1.dp, circleBorder, CircleShape)
                    .springPressable(
                        hapticFeedback = true,
                        pressedScale = 0.95f,
                        onClick = onClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Optional status badge (e.g. "ON" for Passcode or active count)
            if (!badgeText.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .offset(x = 4.dp, y = (-2).dp)
                        .background(iconTint, CircleShape)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badgeText,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 10.sp
                    )
                }
            }
        }

        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 14.sp,
            color = labelColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
