package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Green50
import com.example.ui.theme.Green500

/**
 * Empty State Illustration & Content types for LedgerMate.
 */
enum class EmptyStateType {
    CUSTOMERS,
    DATE_ENTRIES,
    ITEMS,
    SEARCH,
    INVOICES,
    GENERIC
}

/**
 * Premium, minimalist empty state component with geometric tinted icon badge,
 * warm specific copy, and optional action CTA.
 */
@Composable
fun PremiumEmptyState(
    modifier: Modifier = Modifier,
    type: EmptyStateType = EmptyStateType.GENERIC,
    icon: ImageVector? = null,
    title: String? = null,
    subtitle: String? = null,
    actionButton: (@Composable () -> Unit)? = null
) {
    val resolvedIcon: ImageVector = icon ?: when (type) {
        EmptyStateType.CUSTOMERS -> Icons.Outlined.Group
        EmptyStateType.DATE_ENTRIES -> Icons.Outlined.CalendarToday
        EmptyStateType.ITEMS -> Icons.Outlined.Inventory2
        EmptyStateType.SEARCH -> Icons.Outlined.Search
        EmptyStateType.INVOICES -> Icons.Outlined.ReceiptLong
        EmptyStateType.GENERIC -> Icons.Outlined.AccountBalanceWallet
    }

    val resolvedTitle = title ?: when (type) {
        EmptyStateType.CUSTOMERS -> "No customer khata records yet"
        EmptyStateType.DATE_ENTRIES -> "No entries recorded for this day"
        EmptyStateType.ITEMS -> "Your Item List is empty"
        EmptyStateType.SEARCH -> "No matching records found"
        EmptyStateType.INVOICES -> "No invoices or receipts yet"
        EmptyStateType.GENERIC -> "No records found"
    }

    val resolvedSubtitle = subtitle ?: when (type) {
        EmptyStateType.CUSTOMERS -> "Add your first customer or vendor to start tracking credits and payments."
        EmptyStateType.DATE_ENTRIES -> "Tap below to quickly record a sale or cash deposit for this date."
        EmptyStateType.ITEMS -> "Add commodity items and rates for instant billing and lookup."
        EmptyStateType.SEARCH -> "Try searching with a different name, phone number, or bill number."
        EmptyStateType.INVOICES -> "Generate clean PDF bills & receipts with payment QR code."
        EmptyStateType.GENERIC -> "Records and history will appear here once added."
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp, horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Geometric Line Illustration Badge (Green Tinted Circular Container)
            Surface(
                shape = CircleShape,
                color = Green50,
                border = BorderStroke(1.5.dp, Green500.copy(alpha = 0.25f)),
                modifier = Modifier.size(76.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = resolvedIcon,
                        contentDescription = null,
                        tint = Green500,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = resolvedTitle,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    letterSpacing = (-0.2).sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            if (resolvedSubtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = resolvedSubtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }

            if (actionButton != null) {
                Spacer(modifier = Modifier.height(18.dp))
                actionButton()
            }
        }
    }
}
