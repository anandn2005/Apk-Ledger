package com.example.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalIsDarkMode = compositionLocalOf { false }

@Composable
fun isAppInDarkTheme(): Boolean = LocalIsDarkMode.current

// Premium Green Design System Base Palette
val EmeraldPrimary = Green500
val EmeraldDarkPrimary = Green700
val EmeraldLightContainer = Green50
val EmeraldDarkContainer = Green900

private val DarkColorScheme = darkColorScheme(
    primary = Green300,                  // Bright Green 300
    onPrimary = Green900,
    primaryContainer = DarkGreenTint,
    onPrimaryContainer = Green100,
    secondary = DarkTextSecondary,       // Slate 400
    onSecondary = DarkBackground,
    secondaryContainer = Color(0xFF1F1F24),
    onSecondaryContainer = DarkTextPrimary,
    tertiary = Gold500,                  // Warm Gold
    onTertiary = Color.White,
    background = DarkBackground,         // #0C0C0E
    surface = DarkSurface,               // #18181B
    surfaceVariant = DarkSurfaceVariant, // #202024
    onBackground = DarkTextPrimary,      // #F4F4F5
    onSurface = DarkTextPrimary,         // #F4F4F5
    onSurfaceVariant = DarkTextSecondary,// #A1A1AA
    outline = DarkBorder,                // #27272A
    outlineVariant = DarkBorderOutline
)

private val LightColorScheme = lightColorScheme(
    primary = Green500,                  // Brand Green 500 #379358
    onPrimary = Color.White,
    primaryContainer = Green50,          // Green 50 #F0FAF3
    onPrimaryContainer = Green900,       // Deep green text on tint
    secondary = Color(0xFF475569),       // Slate 600
    onSecondary = Color.White,
    secondaryContainer = Green100,       // Soft pill container
    onSecondaryContainer = Green900,
    tertiary = Gold500,                  // Warm Gold Accent
    onTertiary = Color.White,
    background = BackgroundLight,        // #FAFCF9
    surface = SurfaceLight,              // #FFFFFF
    surfaceVariant = SurfaceVariantLight,// #F2F6F3
    onBackground = TextPrimary,          // #1B2E22
    onSurface = TextPrimary,             // #1B2E22
    onSurfaceVariant = TextSecondary,    // #6B7280
    outline = BorderLight,               // #E3EFE6
    outlineVariant = BorderOutline
)

@Composable
fun LedgerMateTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalIsDarkMode provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
