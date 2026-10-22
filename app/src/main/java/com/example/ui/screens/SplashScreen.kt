package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    val progress = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        onSplashFinished()
    }

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val splashBgColor = if (isDark) Color(0xFF111827) else Color(0xFFFAFCF9)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = splashBgColor
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Centered App Logo Badge
                Icon(
                    painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_app_logo),
                    contentDescription = "App Logo",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(108.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "LedgerMate",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground
                )

                // Sleek loading indicator placed exactly 24px (24dp) below the logo
                Spacer(modifier = Modifier.height(24.dp))

                // Progress Bar
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress.value)
                            .clip(RoundedCornerShape(2.dp))
                            .background(androidx.compose.material3.MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}
