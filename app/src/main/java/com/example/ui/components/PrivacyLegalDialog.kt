package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PrivacyPolicyAndTermsDialog(
    onDismiss: () -> Unit,
    isDark: Boolean = false
) {
    val cardBackground = if (isDark) Color(0xFF18181B) else Color.White
    val textPrimary = if (isDark) Color(0xFFF4F4F5) else Color(0xFF1B2E22)
    val textSecondary = if (isDark) Color(0xFFA1A1AA) else Color(0xFF64748B)
    val accentGreen = if (isDark) Color(0xFF4CAF6E) else Color(0xFF379358)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cardBackground,
        icon = {
            Icon(
                imageVector = Icons.Default.PrivacyTip,
                contentDescription = null,
                tint = accentGreen
            )
        },
        title = {
            Text(
                text = "Privacy Policy & Terms",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = textPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PolicySection(
                    heading = "1. Data We Process",
                    body = "LedgerMate stores your business profile (store name, phone, address, GSTIN, UPI ID), customer and supplier ledger entries, item rate lists, invoices, and notes locally on your device. If you choose to sign in with Google or Email, your account email, display name, and synced ledger records are stored under your private user path in Google Firebase.",
                    titleColor = textPrimary,
                    bodyColor = textSecondary
                )
                PolicySection(
                    heading = "2. Security & Local Controls",
                    body = "Your app passcode is stored using salted SHA-256 hashing with lockout rate-limiting, and optional biometric unlock uses Android system hardware prompts. Cloud analytics collection is disabled, and Android auto-backup of local databases and preferences is disabled.",
                    titleColor = textPrimary,
                    bodyColor = textSecondary
                )
                PolicySection(
                    heading = "3. Your Rights, Export & Deletion",
                    body = "You can export a complete JSON or CSV copy of your records anytime from Settings > History & Data. You can permanently delete your Firebase account and all synced cloud and local records anytime from Settings > Account > Delete Account & Data.",
                    titleColor = textPrimary,
                    bodyColor = textSecondary
                )
                PolicySection(
                    heading = "4. Eligibility & Terms of Use (18+ Only)",
                    body = "LedgerMate is intended solely for business and adult users aged 18 years or older. By using the app, you confirm you are at least 18 years old. LedgerMate is a bookkeeping and billing utility, and you remain responsible for verifying accounting totals, tax/GST rates, and exported files before sharing or filing financial records.",
                    titleColor = textPrimary,
                    bodyColor = textSecondary
                )
                PolicySection(
                    heading = "5. Support & Privacy Requests",
                    body = "For privacy questions or support, please reach out using the developer support contact listed on the LedgerMate store listing.",
                    titleColor = textPrimary,
                    bodyColor = textSecondary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentGreen, contentColor = Color.White),
                modifier = Modifier.testTag("close_privacy_terms_dialog")
            ) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun PolicySection(
    heading: String,
    body: String,
    titleColor: Color,
    bodyColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = heading,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = titleColor
        )
        Text(
            text = body,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
            color = bodyColor
        )
    }
}
