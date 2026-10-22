package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ============================================================
// BRAND GREEN TONAL SCALE (Derivation of #379358)
// ============================================================
val Green50 = Color(0xFFF0FAF3)   // Lightest tint, subtle card backgrounds & badge surfaces
val Green100 = Color(0xFFD9F0DF)  // Light tint for icons & selected pill backgrounds
val Green200 = Color(0xFFBCE3C6)  // Soft border tint
val Green300 = Color(0xFF7DCB94)  // Mid tint for accents & chart secondary lines
val Green400 = Color(0xFF4FA96E)  // Hover & secondary CTA
val Green500 = Color(0xFF379358)  // Primary brand accent (#379358)
val Green600 = Color(0xFF2E7D4C)  // Pressed state on light mode
val Green700 = Color(0xFF276B40)  // Active/pressed states & dark mode accent
val Green800 = Color(0xFF1E5231)  // Dark mode active tint
val Green900 = Color(0xFF143621)  // Deep accents, dark mode high-contrast text on green

// ============================================================
// SECONDARY / SEMANTIC HIGHLIGHT (WARM AMBER / GOLD FOR PRO & BADGES)
// ============================================================
val Gold50 = Color(0xFFFFFBEB)
val Gold100 = Color(0xFFFEF3C7)
val Gold300 = Color(0xFFFCD34D)
val Gold500 = Color(0xFFD97706)   // Warm Amber / Gold
val Gold700 = Color(0xFFB45309)

// ============================================================
// STATUS & SEMANTIC ACCENTS
// ============================================================
val StatusEmerald = Green500
val StatusEmeraldDark = Green700
val StatusEmeraldLight = Green50
val StatusEmeraldDarkBg = Color(0xFF0F2A1E)

val EmeraldGradient = Brush.linearGradient(
    colors = listOf(Green500, Green700)
)
val EmeraldGradientLight = Brush.linearGradient(
    colors = listOf(Green300, Green500)
)

val StatusCoral = Color(0xFFF97316)            // Vibrant Coral - Unpaid / Pending
val StatusCoralLight = Color(0xFFFFF7ED)       // Light Tint
val StatusCoralDarkBg = Color(0xFF2A1A0E)      // Dark Tint

val StatusRed = Color(0xFFEF4444)              // True Red - Overdue / Debit / Danger
val StatusRedLight = Color(0xFFFEF2F2)         // Light Tint
val StatusRedDarkBg = Color(0xFF2A0F0F)        // Dark Tint

val CreditGreen = Green500
val CreditGreenLight = Green50
val CreditGreenDark = Green700

val DebitRed = StatusRed
val DebitRedLight = StatusRedLight
val DebitRedDark = Color(0xFFB91C1C)

val AmberWarning = StatusCoral
val AmberWarningLight = StatusCoralLight

// ============================================================
// LIGHT MODE CANVAS & SURFACES (#FAFCF9 Base)
// ============================================================
val BackgroundLight = Color(0xFFFAFCF9)        // Signature warm soft mint canvas
val SurfaceLight = Color(0xFFFFFFFF)           // Pure White Cards
val SurfaceVariantLight = Color(0xFFF2F6F3)     // Soft tinted sub-surfaces & inputs
val TextPrimary = Color(0xFF1B2E22)            // Deep slate-green text
val TextSecondary = Color(0xFF6B7280)          // Slate 500
val TextMuted = Color(0xFF9CA3AF)              // Slate 400
val BorderLight = Color(0xFFE3EFE6)            // Brand 1px border (#E3EFE6)
val BorderOutline = Color(0xFFCBD5E1)          // Slate 300

// ============================================================
// AMOLED DARK MODE PALETTE
// ============================================================
val DarkBackground = Color(0xFF0C0C0E)         // Deep dark background
val DarkSurface = Color(0xFF18181B)            // Sleek Dark Surface Card
val DarkSurfaceVariant = Color(0xFF202024)     // Input Fields, Chips & Sub-surfaces
val DarkTextPrimary = Color(0xFFF4F4F5)        // Crisp Off-White
val DarkTextSecondary = Color(0xFFA1A1AA)      // Muted Slate 400
val DarkTextMuted = Color(0xFF71717A)          // Slate 500
val DarkBorder = Color(0xFF27272A)             // Subtle hairline border
val DarkBorderOutline = Color(0xFF3F3F46)
val DarkGreenTint = Color(0xFF132A1C)          // Dark Green Container

// Gradients
val GradientReceivableCard = Brush.verticalGradient(
    colors = listOf(Color(0xFF143621), Color(0xFF1E5231))
)
val GradientPayableCard = Brush.verticalGradient(
    colors = listOf(Color(0xFF7F1D1D), Color(0xFF991B1B))
)
val GradientHeaderCard = Brush.linearGradient(
    colors = listOf(Green900, Green700)
)

// Backwards compatibility aliases mapped directly to brand green design tokens
val PrimaryBlue = Green500
val PrimaryIndigo = Green600
val PrimaryNavy = Green900
val PrimaryPurpleDark = Green700
val PrimaryContainerLight = Green50
val PrimaryTeal = Green300

