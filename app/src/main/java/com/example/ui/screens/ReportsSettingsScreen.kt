package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.example.data.AppPreferencesManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.AppStrings
import com.example.data.AuditLogEntity
import com.example.sync.SyncStatus
import com.example.ui.Formatters
import com.example.ui.LedgerUiState
import com.example.ui.components.ScrollEdgeShadow
import com.example.ui.components.pressScale
import com.example.ui.components.rememberCanScrollBack
import com.example.ui.theme.*
import com.example.utils.ExportUtils
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.example.ui.components.CategoryIconPickerDialog
import com.example.ui.components.VectorIconRegistry
import com.example.ui.components.VectorIconView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsSettingsScreen(
    state: LedgerUiState,
    onUpdateBusinessDetails: (name: String, phone: String, address: String, currency: String, upiId: String, gstin: String) -> Unit,
    onSetPasscode: (pin: String, enable: Boolean) -> Unit,
    onRestoreAuditLog: (AuditLogEntity) -> Unit = {},
    onDeleteAuditLog: (String) -> Unit = {},
    onClearAuditLogs: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onSignInWithGoogle: (android.content.Context) -> Unit = {},
    onSignInWithEmail: (String, String) -> Unit = { _, _ -> },
    onSignUpWithEmail: (String, String, String) -> Unit = { _, _, _ -> },
    onTriggerCloudSync: () -> Unit = {},
    onToggleDarkMode: (Boolean) -> Unit = {},
    onSetLanguage: (AppLanguage) -> Unit = {},
    onAddUnit: (String) -> Unit = {},
    onDeleteUnit: (String) -> Unit = {},
    onMoveUnitUp: (Int) -> Unit = {},
    onMoveUnitDown: (Int) -> Unit = {},
    onReorderUnits: (List<String>) -> Unit = {},
    onToggleCategoryVisibility: (String) -> Unit = {},
    onRenameCategory: (String, String) -> Unit = { _, _ -> },
    onAddCategory: (String) -> Unit = {},
    onDeleteCategory: (String) -> Unit = {},
    onReorderCategories: (List<String>) -> Unit = {},
    onSetCategoryIcon: (String, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Dynamically derive theme state: responds instantly to the global dark mode toggle
    val isDark = state.isDarkMode

    // LedgerMate Theme-Aware Color Tokens
    val pageBackground = if (isDark) Color(0xFF0C0C0E) else Color(0xFFFAFCF9)
    val cardBackground = if (isDark) Color(0xFF18181B) else Color(0xFFFFFFFF)
    val cardBorder = if (isDark) Color(0xFF27272A) else Color(0xFFE3EFE6)
    val textPrimary = if (isDark) Color(0xFFF4F4F5) else Color(0xFF1C1C1E)
    val textSecondary = if (isDark) Color(0xFFA1A1AA) else Color(0xFF8E8E93)
    val dividerColor = if (isDark) Color(0xFF27272A) else Color(0xFFE3EFE6)
    val accentGreen = if (isDark) Color(0xFF4CAF6E) else Color(0xFF379358)
    val accentGreenLight = if (isDark) Color(0xFF132A1C) else Color(0xFFEAF6ED)
    val surfaceVariantBg = if (isDark) Color(0xFF202024) else Color(0xFFF2F2F7)
    val switchUncheckedTrack = if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E5EA)

    var showBusinessDetailsDialog by remember { mutableStateOf(false) }
    var showPasscodeDialog by remember { mutableStateOf(false) }
    var showAuditHistoryDialog by remember { mutableStateOf(false) }
    var showConfirmClearAllDialog by remember { mutableStateOf(false) }
    var showAutoSyncDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showReminderTemplateDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showManageUnitsDialog by remember { mutableStateOf(false) }
    var showManageCategoriesDialog by remember { mutableStateOf(false) }
    var showEmailAuthDialog by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current

    var isBiometricEnabled by remember { mutableStateOf(false) }
    var autoSyncFrequency by remember { mutableStateOf("Every time") }
    var reminderTemplateText by remember {
        mutableStateOf("Dear Customer, gentle reminder regarding balance payment of {AMOUNT} to {BUSINESS_NAME}. Thank you!")
    }

    val listState = rememberLazyListState()
    val canScrollBack = rememberCanScrollBack(listState)

    Scaffold(
        containerColor = pageBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        ),
                        color = textPrimary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = pageBackground
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item { Spacer(modifier = Modifier.height(2.dp)) }

                // ============================================================
                // 1) ACCOUNT & CLOUD SYNC
                // ============================================================
                item {
                    SettingsSectionHeader("ACCOUNT", textSecondary)

                    Spacer(modifier = Modifier.height(8.dp))

                    SoftSettingCard(
                        backgroundColor = cardBackground,
                        borderColor = cardBorder
                    ) {
                        if (state.authUser != null) {
                            // Signed in User Profile Card
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Green Gradient Avatar Circle ("A")
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(Color(0xFF4CAF6E), accentGreen)
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = (state.authUser.displayName?.firstOrNull() ?: state.authUser.email?.firstOrNull() ?: 'G').uppercase(),
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = state.authUser.displayName ?: "Google Account",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        ),
                                        color = textPrimary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = state.authUser.email ?: "",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 13.sp
                                        ),
                                        color = textSecondary
                                    )
                                    if (state.syncState.lastSyncTimestamp > 0) {
                                        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Synced: ${sdf.format(Date(state.syncState.lastSyncTimestamp))}",
                                            fontSize = 12.sp,
                                            color = textSecondary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                // Synced Status Badge (Green tint)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = when (state.syncState.status) {
                                        SyncStatus.SYNCING -> accentGreenLight
                                        SyncStatus.SYNCED -> accentGreenLight
                                        SyncStatus.OFFLINE -> Color(0xFFFFF3E0)
                                        else -> accentGreenLight
                                    }
                                ) {
                                    Text(
                                        text = when (state.syncState.status) {
                                            SyncStatus.SYNCING -> "Syncing"
                                            SyncStatus.SYNCED -> "Synced"
                                            SyncStatus.OFFLINE -> "Offline"
                                            else -> "Idle"
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when (state.syncState.status) {
                                            SyncStatus.SYNCING -> accentGreen
                                            SyncStatus.SYNCED -> accentGreen
                                            SyncStatus.OFFLINE -> Color(0xFFE65100)
                                            else -> accentGreen
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = dividerColor, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Sync Now Solid Accent Green Button
                                Button(
                                    onClick = onTriggerCloudSync,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = accentGreen,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(vertical = 12.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Sync Now", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }

                                // Sign Out Neutral Ghost Button
                                OutlinedButton(
                                    onClick = onSignOut,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, cardBorder),
                                    contentPadding = PaddingValues(vertical = 12.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp), tint = textPrimary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Sign Out", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                                }
                            }
                        } else {
                            // Signed out / Offline mode with 1-tap Google Sign-In
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = accentGreenLight,
                                        modifier = Modifier.size(46.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.CloudSync,
                                                contentDescription = null,
                                                tint = accentGreen,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Cloud Sync & Backup",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            ),
                                            color = textPrimary
                                        )
                                        Text(
                                            text = "Sign in with Google to sync with Firebase (ledger--mate)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = textSecondary
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onSignInWithGoogle(context)
                                    },
                                    enabled = !state.isSigningIn,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = accentGreen,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(vertical = 12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .pressScale(targetScale = 0.96f)
                                        .testTag("settings_google_sign_in_button")
                                ) {
                                    if (state.isSigningIn) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Signing in...", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    } else {
                                        Surface(
                                            shape = CircleShape,
                                            color = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                com.example.ui.components.GoogleLogoIcon(modifier = Modifier.size(14.dp))
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text("Sign in with Google", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        showEmailAuthDialog = true
                                    },
                                    enabled = !state.isSigningIn,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, if (isDark) Color(0xFF3F3F46) else Color(0xFFD1D5DB)),
                                    contentPadding = PaddingValues(vertical = 11.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .pressScale(targetScale = 0.96f)
                                        .testTag("settings_email_sign_in_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Email,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = textPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Sign in with Email", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                                }
                            }
                        }
                    }
                }

                // ============================================================
                // 2) SUMMARY OVERVIEW
                // ============================================================
                item {
                    SettingsSectionHeader("SUMMARY", textSecondary)

                    Spacer(modifier = Modifier.height(8.dp))

                    SoftSettingCard(
                        backgroundColor = cardBackground,
                        borderColor = cardBorder
                    ) {
                        // Customers Row
                        SummaryRowItem(
                            icon = Icons.Default.People,
                            iconBg = accentGreenLight,
                            iconTint = accentGreen,
                            label = "Customers",
                            value = "${state.summary.customerCount}",
                            textColor = textPrimary,
                            valueColor = textPrimary
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = dividerColor.copy(alpha = 0.6f), thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(10.dp))

                        // Receivables Row
                        SummaryRowItem(
                            icon = Icons.Default.ArrowUpward,
                            iconBg = accentGreenLight,
                            iconTint = accentGreen,
                            label = "Receivables",
                            value = Formatters.formatCurrency(state.summary.totalReceivable, state.currencySymbol),
                            textColor = textPrimary,
                            valueColor = accentGreen
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = dividerColor.copy(alpha = 0.6f), thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(10.dp))

                        // Payables Row
                        SummaryRowItem(
                            icon = Icons.Default.ArrowDownward,
                            iconBg = if (isDark) Color(0xFF2E1414) else DebitRedLight,
                            iconTint = DebitRed,
                            label = "Payables",
                            value = Formatters.formatCurrency(state.summary.totalPayable, state.currencySymbol),
                            textColor = textPrimary,
                            valueColor = DebitRed
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = dividerColor.copy(alpha = 0.6f), thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(10.dp))

                        // Unpaid Invoices Row
                        SummaryRowItem(
                            icon = Icons.Default.Description,
                            iconBg = if (isDark) Color(0xFF2E2412) else Color(0xFFFFF8E1),
                            iconTint = AmberWarning,
                            label = "Unpaid Invoices",
                            value = "${state.summary.unpaidInvoiceCount}",
                            textColor = textPrimary,
                            valueColor = AmberWarning
                        )
                    }
                }

                // ============================================================
                // 3) HISTORY & DATA BACKUP
                // ============================================================
                item {
                    SettingsSectionHeader(AppStrings.historyAndData(state.selectedLanguage), textSecondary)

                    Spacer(modifier = Modifier.height(8.dp))

                    SoftSettingCard(
                        backgroundColor = cardBackground,
                        borderColor = cardBorder
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.Top
                        ) {
                            CircularSettingsActionButton(
                                customIcon = { TrashWithClockIcon(isDark) },
                                label = AppStrings.actionTrash(state.selectedLanguage),
                                isDark = isDark,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showAuditHistoryDialog = true
                                }
                            )

                            CircularSettingsActionButton(
                                icon = Icons.AutoMirrored.Filled.DriveFileMove,
                                label = AppStrings.actionExport(state.selectedLanguage),
                                isDark = isDark,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showExportDialog = true
                                }
                            )

                            CircularSettingsActionButton(
                                icon = Icons.Default.CloudSync,
                                label = AppStrings.actionSync(state.selectedLanguage),
                                isDark = isDark,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showAutoSyncDialog = true
                                }
                            )
                        }
                    }
                }

                // ============================================================
                // 4) SECURITY
                // ============================================================
                item {
                    SettingsSectionHeader(AppStrings.security(state.selectedLanguage), textSecondary)

                    Spacer(modifier = Modifier.height(8.dp))

                    SoftSettingCard(
                        backgroundColor = cardBackground,
                        borderColor = cardBorder
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            CircularSettingsActionButton(
                                icon = if (state.isPasscodeEnabled) Icons.Filled.Lock else Icons.Outlined.Lock,
                                label = if (state.isPasscodeEnabled) "${AppStrings.actionPasscode(state.selectedLanguage)}\n(Active)" else AppStrings.actionPasscode(state.selectedLanguage),
                                isDark = isDark,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showPasscodeDialog = true
                                }
                            )

                            CircularSettingsActionButton(
                                icon = Icons.Default.Chat,
                                label = AppStrings.actionReminder(state.selectedLanguage),
                                isDark = isDark,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showReminderTemplateDialog = true
                                }
                            )
                        }
                    }
                }

                // ============================================================
                // 5) PREFERENCES & LOCALIZATION
                // ============================================================
                item {
                    SettingsSectionHeader(AppStrings.preferencesAndLocalization(state.selectedLanguage), textSecondary)

                    Spacer(modifier = Modifier.height(8.dp))

                    SoftSettingCard(
                        backgroundColor = cardBackground,
                        borderColor = cardBorder
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.Top
                        ) {
                            CircularSettingsActionButton(
                                icon = Icons.Default.Language,
                                label = "${AppStrings.actionLanguage(state.selectedLanguage)}\n(${state.selectedLanguage.name.take(3)})",
                                isDark = isDark,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showLanguageDialog = true
                                }
                            )

                            CircularSettingsActionButton(
                                icon = Icons.Default.Straighten,
                                label = "${AppStrings.actionUnits(state.selectedLanguage)}\n(${state.availableUnits.size})",
                                isDark = isDark,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showManageUnitsDialog = true
                                }
                            )

                            CircularSettingsActionButton(
                                icon = Icons.Default.ViewColumn,
                                label = AppStrings.actionCategories(state.selectedLanguage),
                                isDark = isDark,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showManageCategoriesDialog = true
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = dividerColor, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Ordinary Dark Mode Switch Row (#19)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onToggleDarkMode(!isDark)
                                }
                                .padding(vertical = 4.dp)
                                .testTag("settings_dark_mode_row"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = accentGreenLight,
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                                            contentDescription = null,
                                            tint = accentGreen,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "Dark Theme",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary
                                    )
                                    Text(
                                        text = if (isDark) "Dark mode is active" else "Light mode is active",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = textSecondary
                                    )
                                }
                            }
                            Switch(
                                checked = isDark,
                                onCheckedChange = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onToggleDarkMode(it)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = accentGreen
                                )
                            )
                        }
                    }
                }

                // ============================================================
                // 6) BUSINESS & RECEIPT SETTINGS
                // ============================================================
                item {
                    SettingsSectionHeader(AppStrings.businessAndReceiptSettings(state.selectedLanguage), textSecondary)

                    Spacer(modifier = Modifier.height(8.dp))

                    SoftSettingCard(
                        backgroundColor = cardBackground,
                        borderColor = cardBorder
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = accentGreenLight,
                                        modifier = Modifier.size(42.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Receipt,
                                                contentDescription = null,
                                                tint = accentGreen,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = state.businessName.ifBlank { "Anand Provision Store" },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = textPrimary
                                        )
                                        Text(
                                            text = "Receipt Header & Payment QR Config",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = textSecondary
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showBusinessDetailsDialog = true
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("edit_receipt_settings_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Business Details",
                                        tint = accentGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            HorizontalDivider(color = dividerColor, thickness = 1.dp)

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                DetailRow("Business Name", state.businessName.ifBlank { "Not set" }, textSecondary, textPrimary)
                                DetailRow("Business Address", state.businessAddress.ifBlank { "Not set" }, textSecondary, textPrimary)
                                DetailRow("Contact Number", state.businessPhone.ifBlank { "Not set" }, textSecondary, textPrimary)
                                DetailRow("GSTIN", state.businessGstin.ifBlank { "Not set" }, textSecondary, textPrimary)

                                // UPI ID row with copy icon
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("UPI ID (for QR Code)", style = MaterialTheme.typography.bodySmall, color = textSecondary)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.clickable {
                                            if (state.upiId.isNotBlank()) {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("UPI ID", state.upiId))
                                                Toast.makeText(context, "UPI ID copied", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    ) {
                                        Text(
                                            text = state.upiId.ifBlank { "Not set" },
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = textPrimary
                                        )
                                        if (state.upiId.isNotBlank()) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy UPI ID",
                                                tint = accentGreen,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(145.dp)) }
            }

            ScrollEdgeShadow(
                isScrolled = canScrollBack,
                isTop = true,
                shadowHeight = 20.dp,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            ScrollEdgeShadow(
                isScrolled = true,
                isTop = false,
                shadowHeight = 24.dp,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    // ============================================================
    // DIALOG 0: EMAIL AUTH (SIGN IN / CREATE ACCOUNT)
    // ============================================================
    if (showEmailAuthDialog) {
        var emailAuthMode by remember { mutableStateOf("SIGN_IN") }
        var emailText by remember { mutableStateOf("") }
        var passText by remember { mutableStateOf("") }
        var nameText by remember { mutableStateOf("") }
        var passVisible by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showEmailAuthDialog = false },
            containerColor = cardBackground,
            title = {
                Text(
                    text = if (emailAuthMode == "SIGN_IN") "Sign In with Email" else "Create Account",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(surfaceVariantBg)
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val isSignIn = emailAuthMode == "SIGN_IN"
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSignIn) (if (isDark) Color(0xFF2C2C2E) else Color.White) else Color.Transparent)
                                .clickable { emailAuthMode = "SIGN_IN" },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Sign In",
                                fontSize = 12.5.sp,
                                fontWeight = if (isSignIn) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSignIn) accentGreen else textSecondary
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (!isSignIn) (if (isDark) Color(0xFF2C2C2E) else Color.White) else Color.Transparent)
                                .clickable { emailAuthMode = "SIGN_UP" },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Create Account",
                                fontSize = 12.5.sp,
                                fontWeight = if (!isSignIn) FontWeight.Bold else FontWeight.Medium,
                                color = if (!isSignIn) accentGreen else textSecondary
                            )
                        }
                    }

                    if (emailAuthMode == "SIGN_UP") {
                        OutlinedTextField(
                            value = nameText,
                            onValueChange = { nameText = it },
                            label = { Text("Your Name (optional)") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = textSecondary) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = emailText,
                        onValueChange = { emailText = it },
                        label = { Text("Email Address") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = textSecondary) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = passText,
                        onValueChange = { passText = it },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = textSecondary) },
                        trailingIcon = {
                            IconButton(onClick = { passVisible = !passVisible }) {
                                Icon(
                                    imageVector = if (passVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passVisible) "Hide password" else "Show password",
                                    tint = textSecondary
                                )
                            }
                        },
                        visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (emailAuthMode == "SIGN_IN") {
                            onSignInWithEmail(emailText.trim(), passText)
                        } else {
                            onSignUpWithEmail(emailText.trim(), passText, nameText.trim())
                        }
                        showEmailAuthDialog = false
                    },
                    enabled = emailText.isNotBlank() && passText.isNotBlank() && !state.isSigningIn,
                    colors = ButtonDefaults.buttonColors(containerColor = accentGreen)
                ) {
                    Text(if (emailAuthMode == "SIGN_IN") "Sign In" else "Create Account")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmailAuthDialog = false }) {
                    Text("Cancel", color = textSecondary)
                }
            }
        )
    }

    // ============================================================
    // DIALOG 1: BUSINESS DETAILS EDIT DIALOG
    // ============================================================
    if (showBusinessDetailsDialog) {
        var name by remember { mutableStateOf(state.businessName) }
        var phone by remember { mutableStateOf(state.businessPhone) }
        var address by remember { mutableStateOf(state.businessAddress) }
        var upiId by remember { mutableStateOf(state.upiId) }
        var gstin by remember { mutableStateOf(state.businessGstin) }
        var selectedCurrency by remember { mutableStateOf(state.currencySymbol) }

        AlertDialog(
            onDismissRequest = { showBusinessDetailsDialog = false },
            containerColor = cardBackground,
            title = {
                Text(
                    "Business & Receipt Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "These details populate receipt headers and payment QR codes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = textSecondary
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Business Name") },
                        placeholder = { Text("e.g. Anand Provision Store") },
                        modifier = Modifier.fillMaxWidth().testTag("settings_business_name"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentGreen,
                            unfocusedBorderColor = cardBorder,
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary,
                            focusedContainerColor = cardBackground,
                            unfocusedContainerColor = cardBackground
                        )
                    )
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Business Address") },
                        placeholder = { Text("e.g. 124 Main Market, Pune") },
                        modifier = Modifier.fillMaxWidth().testTag("settings_business_address"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentGreen,
                            unfocusedBorderColor = cardBorder,
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary,
                            focusedContainerColor = cardBackground,
                            unfocusedContainerColor = cardBackground
                        )
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Contact Number") },
                        placeholder = { Text("e.g. +91 98765 43210") },
                        modifier = Modifier.fillMaxWidth().testTag("settings_contact_number"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentGreen,
                            unfocusedBorderColor = cardBorder,
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary,
                            focusedContainerColor = cardBackground,
                            unfocusedContainerColor = cardBackground
                        )
                    )
                    OutlinedTextField(
                        value = gstin,
                        onValueChange = { gstin = it },
                        label = { Text("GSTIN (Tax ID)") },
                        placeholder = { Text("e.g. 27AAAAA0000A1Z5") },
                        modifier = Modifier.fillMaxWidth().testTag("settings_gstin"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentGreen,
                            unfocusedBorderColor = cardBorder,
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary,
                            focusedContainerColor = cardBackground,
                            unfocusedContainerColor = cardBackground
                        )
                    )
                    OutlinedTextField(
                        value = upiId,
                        onValueChange = { upiId = it },
                        label = { Text("UPI ID") },
                        placeholder = { Text("e.g. merchant@upi") },
                        modifier = Modifier.fillMaxWidth().testTag("settings_upi_id"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentGreen,
                            unfocusedBorderColor = cardBorder,
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary,
                            focusedContainerColor = cardBackground,
                            unfocusedContainerColor = cardBackground
                        )
                    )

                    Text("Currency Symbol:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = textPrimary)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("₹", "$", "€", "£", "A$", "C$").forEach { symbol ->
                            FilterChip(
                                selected = selectedCurrency == symbol,
                                onClick = { selectedCurrency = symbol },
                                label = { Text(symbol, fontWeight = FontWeight.Bold) },
                                shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accentGreen,
                                    selectedLabelColor = Color.White,
                                    containerColor = surfaceVariantBg,
                                    labelColor = textSecondary
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateBusinessDetails(name, phone, address, selectedCurrency, upiId, gstin)
                        showBusinessDetailsDialog = false
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentGreen, contentColor = Color.White),
                    modifier = Modifier.testTag("settings_save_business")
                ) {
                    Text("Save Changes", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBusinessDetailsDialog = false }) { Text("Cancel", color = textSecondary) }
            }
        )
    }

    // ============================================================
    // DIALOG 2: PASSCODE CONFIGURATION MODAL
    // ============================================================
    if (showPasscodeDialog) {
        var pin: String by remember { mutableStateOf("") }
        var isEditingPin by remember { mutableStateOf(!state.isPasscodeEnabled) }

        FrostedIosModal(
            onDismissRequest = { showPasscodeDialog = false },
            title = "Passcode & Security",
            icon = if (state.isPasscodeEnabled) Icons.Filled.Lock else Icons.Outlined.Lock,
            isDark = isDark
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Passcode Toggle Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = surfaceVariantBg,
                    border = BorderStroke(1.dp, cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Require Passcode on Startup",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = textPrimary
                            )
                            Text(
                                text = if (state.isPasscodeEnabled) "PIN lock active" else "Require 4-digit PIN to open LedgerMate",
                                fontSize = 12.sp,
                                color = textSecondary
                            )
                        }
                        Switch(
                            checked = state.isPasscodeEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    isEditingPin = true
                                } else {
                                    onSetPasscode("", false)
                                    isEditingPin = false
                                    Toast.makeText(context, "Passcode lock disabled", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = accentGreen,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = switchUncheckedTrack
                            )
                        )
                    }
                }

                if (state.isPasscodeEnabled || isEditingPin) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = if (state.isPasscodeEnabled) "Change 4-Digit PIN:" else "Enter 4-Digit PIN:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textPrimary
                        )
                        OutlinedTextField(
                            value = pin,
                            onValueChange = { input: String ->
                                if (input.length <= 4 && input.all { char -> char.isDigit() }) {
                                    pin = input
                                }
                            },
                            placeholder = { Text("••••") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accentGreen,
                                unfocusedBorderColor = cardBorder,
                                focusedTextColor = textPrimary,
                                unfocusedTextColor = textPrimary,
                                focusedContainerColor = surfaceVariantBg,
                                unfocusedContainerColor = surfaceVariantBg
                            )
                        )
                        Button(
                            onClick = {
                                if (pin.length == 4) {
                                    onSetPasscode(pin, true)
                                    showPasscodeDialog = false
                                    Toast.makeText(context, "Passcode saved successfully", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = pin.length == 4,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accentGreen, contentColor = Color.White)
                        ) {
                            Text("Save PIN", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    if (state.isPasscodeEnabled) {
                        HorizontalDivider(color = dividerColor.copy(alpha = 0.5f), thickness = 0.5.dp)

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = surfaceVariantBg,
                            border = BorderStroke(1.dp, cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.Fingerprint, contentDescription = null, tint = accentGreen, modifier = Modifier.size(24.dp))
                                    Column {
                                        Text("Biometric Unlock", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = textPrimary)
                                        Text("Fingerprint or Face unlock", fontSize = 11.sp, color = textSecondary)
                                    }
                                }
                                Switch(
                                    checked = isBiometricEnabled,
                                    onCheckedChange = { isBiometricEnabled = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = accentGreen,
                                        uncheckedThumbColor = Color.White,
                                        uncheckedTrackColor = switchUncheckedTrack
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ============================================================
    // DIALOG 3: AUTO-SYNC FREQUENCY MODAL
    // ============================================================
    if (showAutoSyncDialog) {
        FrostedIosModal(
            onDismissRequest = { showAutoSyncDialog = false },
            title = "Auto-Sync Frequency",
            icon = Icons.Default.CloudSync,
            isDark = isDark
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Choose how frequently LedgerMate synchronizes local records with cloud backup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = textSecondary
                )

                Spacer(modifier = Modifier.height(4.dp))

                val options = listOf(
                    "Every change (Instant)",
                    "Every 15 minutes",
                    "Hourly",
                    "Daily",
                    "Manual only"
                )
                options.forEach { opt ->
                    val isSelected = autoSyncFrequency == opt
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) accentGreenLight else surfaceVariantBg,
                        border = if (isSelected) BorderStroke(1.5.dp, accentGreen) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                autoSyncFrequency = opt
                                showAutoSyncDialog = false
                                Toast.makeText(context, "Auto-sync set to $opt", Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = opt,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) accentGreen else textPrimary
                            )
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = accentGreen, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // ============================================================
    // DIALOG: EXPORT LEDGER DATA MODAL
    // ============================================================
    if (showExportDialog) {
        FrostedIosModal(
            onDismissRequest = { showExportDialog = false },
            title = "Export Ledger Data",
            icon = Icons.AutoMirrored.Filled.DriveFileMove,
            isDark = isDark
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Export customer pending balances (Customer Name, Phone, Total Pending Amount) to a standardized CSV.",
                    style = MaterialTheme.typography.bodySmall,
                    color = textSecondary
                )

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = surfaceVariantBg),
                    border = BorderStroke(1.dp, cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Customers:", fontSize = 13.sp, color = textSecondary)
                            Text("${state.customers.size}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Customers with Dues:", fontSize = 13.sp, color = textSecondary)
                            Text("${state.customers.count { it.netBalance > 0 }}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DebitRed)
                        }
                        HorizontalDivider(color = dividerColor.copy(alpha = 0.5f), thickness = 0.5.dp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Outstanding:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                            Text(
                                Formatters.formatCurrency(state.summary.totalReceivable, state.currencySymbol),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = DebitRed
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        showExportDialog = false
                        val csvContent = ExportUtils.generateLedgerCsv(state.customers)
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            putExtra(Intent.EXTRA_TEXT, csvContent)
                            type = "text/csv"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Export Ledger CSV"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentGreen, contentColor = Color.White),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export & Share CSV", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }

    // ============================================================
    // DIALOG 4: REMINDER TEMPLATE EDITOR MODAL
    // ============================================================
    if (showReminderTemplateDialog) {
        var tempText by remember { mutableStateOf(reminderTemplateText) }

        FrostedIosModal(
            onDismissRequest = { showReminderTemplateDialog = false },
            title = "WhatsApp Reminder",
            icon = Icons.Default.Chat,
            isDark = isDark
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Customize the automated reminder message sent to customers. Tap a variable chip to insert it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = textSecondary
                )

                // Variable Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val variables = listOf("{CUSTOMER_NAME}", "{AMOUNT}", "{BUSINESS_NAME}")
                    variables.forEach { v ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accentGreenLight,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    tempText = if (tempText.isBlank()) v else "$tempText $v"
                                }
                        ) {
                            Text(
                                text = v,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentGreen,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = tempText,
                    onValueChange = { tempText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentGreen,
                        unfocusedBorderColor = cardBorder,
                        focusedTextColor = textPrimary,
                        unfocusedTextColor = textPrimary,
                        focusedContainerColor = surfaceVariantBg,
                        unfocusedContainerColor = surfaceVariantBg
                    )
                )

                // Live Preview Bubble
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF142E1F) else Color(0xFFDCF8C6)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "PREVIEW",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentGreen
                        )
                        val sampleMsg = tempText
                            .replace("{CUSTOMER_NAME}", "Rahul Sharma")
                            .replace("{AMOUNT}", "${state.currencySymbol}1,250")
                            .replace("{BUSINESS_NAME}", state.businessName.ifBlank { "Anand Provision Store" })
                        Text(
                            text = sampleMsg,
                            fontSize = 12.sp,
                            color = Color(0xFF111B21)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            tempText = "Dear {CUSTOMER_NAME}, gentle reminder regarding balance payment of {AMOUNT} to {BUSINESS_NAME}. Thank you!"
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, cardBorder)
                    ) {
                        Text("Reset", color = textPrimary, fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            reminderTemplateText = tempText
                            showReminderTemplateDialog = false
                            Toast.makeText(context, "Reminder template saved", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accentGreen, contentColor = Color.White)
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    // ============================================================
    // DIALOG 5: DELETED HISTORY & TRASH DIALOG
    // ============================================================
    if (showConfirmClearAllDialog) {
        val deletedCount = state.auditLogs.count {
            it.actionType == "DELETE_INVOICE" || it.actionType == "DELETE_RECEIPT" ||
            it.title.contains("Invoice", ignoreCase = true) || it.title.contains("Receipt", ignoreCase = true)
        }

        AlertDialog(
            onDismissRequest = { showConfirmClearAllDialog = false },
            containerColor = cardBackground,
            title = {
                Text(
                    text = "Delete All Receipts?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
            },
            text = {
                Text(
                    text = "Permanently delete all $deletedCount deleted receipt(s)? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAuditLogs()
                        showConfirmClearAllDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DebitRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("confirm_delete_all_history_btn")
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete All", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmClearAllDialog = false }) {
                    Text("Cancel", color = textSecondary)
                }
            }
        )
    }

    if (showAuditHistoryDialog) {
        val deletedReceiptLogs = remember(state.auditLogs) {
            state.auditLogs.filter { log ->
                log.actionType == "DELETE_INVOICE" ||
                log.actionType == "DELETE_RECEIPT" ||
                log.title.contains("Invoice", ignoreCase = true) ||
                log.title.contains("Receipt", ignoreCase = true)
            }
        }

        FrostedIosModal(
            onDismissRequest = { showAuditHistoryDialog = false },
            title = "Event History & Trash",
            icon = Icons.Outlined.Delete,
            isDark = isDark,
            actionButton = {
                if (deletedReceiptLogs.isNotEmpty()) {
                    Button(
                        onClick = { showConfirmClearAllDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = DebitRed, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("empty_trash_button")
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = "Delete All", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete All", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        ) {
            if (deletedReceiptLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = accentGreenLight,
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ReceiptLong,
                                    contentDescription = null,
                                    tint = accentGreen,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Text(
                            text = "No deleted receipts",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                        Text(
                            text = "Deleted receipts will appear here for recovery.",
                            style = MaterialTheme.typography.bodySmall,
                            color = textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "${deletedReceiptLogs.size} deleted receipt(s) stored",
                        fontSize = 12.sp,
                        color = textSecondary
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(deletedReceiptLogs, key = { it.id }) { log ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF222328) else Color(0xFFF9FAFB)),
                                border = BorderStroke(1.dp, cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = accentGreenLight,
                                            modifier = Modifier.size(38.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.ReceiptLong,
                                                    contentDescription = null,
                                                    tint = accentGreen,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                        Column {
                                            Text(
                                                text = log.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (log.description.isNotBlank()) {
                                                Text(
                                                    text = log.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = textSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Text(
                                                text = Formatters.formatDate(log.timestamp) + " • " + Formatters.formatTime(log.timestamp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = textSecondary
                                            )
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Button(
                                            onClick = { onRestoreAuditLog(log) },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = accentGreenLight,
                                                contentColor = accentGreen
                                            ),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.Restore, contentDescription = "Restore", modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Restore", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        IconButton(
                                            onClick = { onDeleteAuditLog(log.id) },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isDark) Color(0xFF2E1414) else DebitRedLight)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Permanently delete",
                                                tint = DebitRed,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ============================================================
    // DIALOG: LANGUAGE SELECTION MODAL
    // ============================================================
    if (showLanguageDialog) {
        FrostedIosModal(
            onDismissRequest = { showLanguageDialog = false },
            title = "Select Language",
            icon = Icons.Default.Language,
            isDark = isDark
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Changes take effect immediately across all screens and bills.",
                    fontSize = 13.sp,
                    color = textSecondary
                )

                Spacer(modifier = Modifier.height(4.dp))

                AppLanguage.entries.forEach { lang ->
                    val isSelected = state.selectedLanguage == lang
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSetLanguage(lang)
                                showLanguageDialog = false
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) accentGreenLight else surfaceVariantBg,
                        border = if (isSelected) BorderStroke(1.5.dp, accentGreen) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = lang.nativeName,
                                    fontSize = 16.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) accentGreen else textPrimary
                                )
                                if (lang != AppLanguage.ENGLISH) {
                                    Text(
                                        text = lang.displayName,
                                        fontSize = 12.sp,
                                        color = textSecondary
                                    )
                                }
                            }

                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSetLanguage(lang)
                                    showLanguageDialog = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = accentGreen,
                                    unselectedColor = textSecondary
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    // ============================================================
    // DIALOG: MANAGE UNITS & REORDERING MODAL
    // ============================================================
    if (showManageUnitsDialog) {
        var newUnitText by remember { mutableStateOf("") }
        var unitToDelete by remember { mutableStateOf<String?>(null) }

        FrostedIosModal(
            onDismissRequest = { showManageUnitsDialog = false },
            title = "Manage Units",
            icon = Icons.Default.Straighten,
            isDark = isDark
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Reorder units to control their sequence in rate items and bill selectors.",
                    fontSize = 13.sp,
                    color = textSecondary
                )

                // Add New Unit Inline Form
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newUnitText,
                        onValueChange = { newUnitText = it },
                        placeholder = { Text("e.g. Dozen, Roll, Bdl", fontSize = 13.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentGreen,
                            unfocusedBorderColor = cardBorder,
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary,
                            focusedContainerColor = surfaceVariantBg,
                            unfocusedContainerColor = surfaceVariantBg
                        )
                    )

                    Button(
                        onClick = {
                            if (newUnitText.isNotBlank()) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onAddUnit(newUnitText.trim())
                                newUnitText = ""
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accentGreen, contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        enabled = newUnitText.isNotBlank()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Unit", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                HorizontalDivider(color = dividerColor.copy(alpha = 0.6f), thickness = 0.5.dp)

                // Units List with reorder arrows and delete
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(
                        items = state.availableUnits,
                        key = { _, unit -> "unit_item_$unit" }
                    ) { index, unit ->
                        val isDefault = AppPreferencesManager.defaultSystemUnits.contains(unit)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = surfaceVariantBg,
                            border = BorderStroke(1.dp, cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Dedicated Drag Handle Icon on far left edge
                                    Icon(
                                        imageVector = Icons.Default.DragIndicator,
                                        contentDescription = "Drag to reorder",
                                        tint = textSecondary.copy(alpha = 0.7f),
                                        modifier = Modifier
                                            .size(24.dp)
                                            .pointerInput(unit, state.availableUnits) {
                                                var accumulatedY = 0f
                                                detectDragGestures(
                                                    onDragStart = { accumulatedY = 0f },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        accumulatedY += dragAmount.y
                                                        val threshold = 36.dp.toPx()
                                                        val currentIndex = state.availableUnits.indexOf(unit)
                                                        if (accumulatedY > threshold && currentIndex < state.availableUnits.size - 1) {
                                                            accumulatedY = 0f
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            val mutable = state.availableUnits.toMutableList()
                                                            val item = mutable.removeAt(currentIndex)
                                                            mutable.add(currentIndex + 1, item)
                                                            onReorderUnits(mutable)
                                                        } else if (accumulatedY < -threshold && currentIndex > 0) {
                                                            accumulatedY = 0f
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            val mutable = state.availableUnits.toMutableList()
                                                            val item = mutable.removeAt(currentIndex)
                                                            mutable.add(currentIndex - 1, item)
                                                            onReorderUnits(mutable)
                                                        }
                                                    }
                                                )
                                            }
                                    )

                                    Text(
                                        text = "${index + 1}.",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textSecondary
                                    )
                                    Text(
                                        text = unit,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary
                                    )
                                    if (isDefault) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = accentGreenLight
                                        ) {
                                            Text(
                                                text = "Default",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = accentGreen,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    // Move Up Button
                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onMoveUnitUp(index)
                                        },
                                        enabled = index > 0,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowUp,
                                            contentDescription = "Move Up",
                                            tint = if (index > 0) accentGreen else textSecondary.copy(alpha = 0.25f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // Move Down Button
                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onMoveUnitDown(index)
                                        },
                                        enabled = index < state.availableUnits.size - 1,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Move Down",
                                            tint = if (index < state.availableUnits.size - 1) accentGreen else textSecondary.copy(alpha = 0.25f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // Delete button (allowed for custom units or with confirmation)
                                    IconButton(
                                        onClick = { unitToDelete = unit },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Delete Unit",
                                            tint = if (isDefault) textSecondary.copy(alpha = 0.4f) else DebitRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Delete Unit Confirmation Dialog
        if (unitToDelete != null) {
            AlertDialog(
                onDismissRequest = { unitToDelete = null },
                containerColor = cardBackground,
                title = { Text("Delete '$unitToDelete'?", fontWeight = FontWeight.Bold, color = textPrimary) },
                text = {
                    Text(
                        "Are you sure you want to remove '$unitToDelete'? Items using this unit will retain it, but it won't appear in the unit picker.",
                        fontSize = 13.sp,
                        color = textSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDeleteUnit(unitToDelete!!)
                            unitToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DebitRed, contentColor = Color.White)
                    ) {
                        Text("Delete", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { unitToDelete = null }) { Text("Cancel", color = textSecondary) }
                }
            )
        }
    }

    // ============================================================
    // DIALOG: MANAGE CATEGORIES & TABLE HEADERS
    // ============================================================
    if (showManageCategoriesDialog) {
        var newCatName by remember { mutableStateOf("") }
        var categoryToRename by remember { mutableStateOf<String?>(null) }
        var renameText by remember { mutableStateOf("") }
        var categoryForIconPicker by remember { mutableStateOf<String?>(null) }

        val activeCategories = state.rateCategories.filter { it != "ALL" }

        FrostedIosModal(
            onDismissRequest = { showManageCategoriesDialog = false },
            title = "Categories & Headers",
            icon = Icons.Default.ViewColumn,
            isDark = isDark
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Tap icon to change vector, drag (≡) to reorder, or edit names.",
                    fontSize = 13.sp,
                    color = textSecondary
                )

                // Add Category Inline Form
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newCatName,
                        onValueChange = { newCatName = it },
                        placeholder = { Text("e.g. Spices, Dairy", fontSize = 13.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentGreen,
                            unfocusedBorderColor = cardBorder,
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary
                        )
                    )

                    Button(
                        onClick = {
                            if (newCatName.isNotBlank()) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onAddCategory(newCatName.trim())
                                newCatName = ""
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accentGreen, contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        enabled = newCatName.isNotBlank()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Category", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                HorizontalDivider(color = dividerColor.copy(alpha = 0.6f), thickness = 0.5.dp)

                // Categories List with Drag Handle & Interactive Icon Preview Button
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(
                        items = activeCategories,
                        key = { "cat_item_$it" }
                    ) { category ->
                        val isHidden = state.hiddenCategories.contains(category)
                        val iconKey = state.categoryIconMap[category] ?: VectorIconRegistry.inferIconForCategory(category).key
                        val catIndex = activeCategories.indexOf(category)

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isHidden) surfaceVariantBg.copy(alpha = 0.5f) else surfaceVariantBg,
                            border = BorderStroke(1.dp, cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // 1. Dedicated Drag Handle Icon on far left edge
                                    Icon(
                                        imageVector = Icons.Default.DragIndicator,
                                        contentDescription = "Drag to reorder",
                                        tint = textSecondary.copy(alpha = 0.7f),
                                        modifier = Modifier
                                            .size(24.dp)
                                            .pointerInput(category, activeCategories) {
                                                var accumulatedY = 0f
                                                detectDragGestures(
                                                    onDragStart = { accumulatedY = 0f },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        accumulatedY += dragAmount.y
                                                        val threshold = 36.dp.toPx()
                                                        val currentIndex = activeCategories.indexOf(category)
                                                        if (accumulatedY > threshold && currentIndex < activeCategories.size - 1) {
                                                            accumulatedY = 0f
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            val mutable = activeCategories.toMutableList()
                                                            val item = mutable.removeAt(currentIndex)
                                                            mutable.add(currentIndex + 1, item)
                                                            onReorderCategories(mutable)
                                                        } else if (accumulatedY < -threshold && currentIndex > 0) {
                                                            accumulatedY = 0f
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            val mutable = activeCategories.toMutableList()
                                                            val item = mutable.removeAt(currentIndex)
                                                            mutable.add(currentIndex - 1, item)
                                                            onReorderCategories(mutable)
                                                        }
                                                    }
                                                )
                                            }
                                    )

                                    // 2. Interactive Per-Category Icon Preview Button
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = accentGreenLight,
                                        border = BorderStroke(1.dp, accentGreen.copy(alpha = 0.35f)),
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                categoryForIconPicker = category
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            VectorIconView(
                                                iconKey = iconKey,
                                                tint = accentGreen,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    // 3. Category Name
                                    Text(
                                        text = category,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isHidden) textSecondary else textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    // Move Up
                                    IconButton(
                                        onClick = {
                                            if (catIndex > 0) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                val mutable = activeCategories.toMutableList()
                                                val item = mutable.removeAt(catIndex)
                                                mutable.add(catIndex - 1, item)
                                                onReorderCategories(mutable)
                                            }
                                        },
                                        enabled = catIndex > 0,
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowUp,
                                            contentDescription = "Move Up",
                                            tint = if (catIndex > 0) accentGreen else textSecondary.copy(alpha = 0.25f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Move Down
                                    IconButton(
                                        onClick = {
                                            if (catIndex < activeCategories.size - 1) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                val mutable = activeCategories.toMutableList()
                                                val item = mutable.removeAt(catIndex)
                                                mutable.add(catIndex + 1, item)
                                                onReorderCategories(mutable)
                                            }
                                        },
                                        enabled = catIndex < activeCategories.size - 1,
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Move Down",
                                            tint = if (catIndex < activeCategories.size - 1) accentGreen else textSecondary.copy(alpha = 0.25f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Visibility Toggle
                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onToggleCategoryVisibility(category)
                                        },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle Visibility",
                                            tint = if (isHidden) textSecondary else accentGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Rename Button
                                    IconButton(
                                        onClick = {
                                            categoryToRename = category
                                            renameText = category
                                        },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Rename Category",
                                            tint = textSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Delete Category
                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onDeleteCategory(category)
                                        },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Delete Category",
                                            tint = DebitRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Category Icon Picker Dialog
        if (categoryForIconPicker != null) {
            val cat = categoryForIconPicker!!
            val currentKey = state.categoryIconMap[cat] ?: VectorIconRegistry.inferIconForCategory(cat).key
            CategoryIconPickerDialog(
                initialCategoryName = cat,
                initialIconKey = currentKey,
                title = "Select Icon for $cat",
                onDismiss = { categoryForIconPicker = null },
                onConfirm = { _, iconKey, _ ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSetCategoryIcon(cat, iconKey)
                    categoryForIconPicker = null
                }
            )
        }

        // Rename Category Prompt Dialog
        if (categoryToRename != null) {
            AlertDialog(
                onDismissRequest = { categoryToRename = null },
                containerColor = cardBackground,
                title = { Text("Rename Category", fontWeight = FontWeight.Bold, color = textPrimary) },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text("Category Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentGreen,
                            unfocusedBorderColor = cardBorder,
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary
                        )
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (renameText.isNotBlank()) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onRenameCategory(categoryToRename!!, renameText.trim())
                                categoryToRename = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentGreen, contentColor = Color.White),
                        enabled = renameText.isNotBlank()
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { categoryToRename = null }) { Text("Cancel", color = textSecondary) }
                }
            )
        }
    }
}

// ============================================================
// HELPER COMPONENTS FOR SETTINGS SCREEN
// ============================================================

@Composable
private fun SettingsSectionHeader(title: String, color: Color) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 0.8.sp
        ),
        color = color
    )
}

@Composable
private fun SummaryRowItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    iconTint: Color,
    label: String,
    value: String,
    textColor: Color,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = iconBg,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
        }
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String, labelColor: Color, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = labelColor)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
fun ListSettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    iconBg: Color,
    iconTint: Color,
    textColor: Color,
    subtitleColor: Color,
    chevronColor: Color = subtitleColor,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = iconBg,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                ),
                color = textColor
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp
                    ),
                    color = subtitleColor
                )
            }
        }

        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        } else {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = chevronColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SoftSettingCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    borderColor: Color,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = Color(0x06000000),
                spotColor = Color(0x0A000000)
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                        onClick = onClick
                    )
                } else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun CircularSettingsActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    customIcon: (@Composable () -> Unit)? = null,
    isDark: Boolean = false,
    badgeContent: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
        ),
        label = "circular_btn_scale"
    )

    val circleBg = if (isDark) Color(0xFF1B2E21) else Color(0xFFEAF6ED)
    val circleBorder = if (isDark) Color(0xFF2E4D37) else Color(0xFFD6EDE0)
    val iconTint = if (isDark) Color(0xFF4CAF6E) else Color(0xFF379358)
    val textColor = if (isDark) Color(0xFFF4F4F5) else Color(0xFF1C1C1E)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .widthIn(min = 74.dp, max = 108.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier
                    .size(64.dp)
                    .shadow(
                        elevation = if (isPressed) 1.dp else 3.dp,
                        shape = CircleShape,
                        spotColor = Color(0x33379358)
                    ),
                shape = CircleShape,
                color = circleBg,
                border = BorderStroke(1.dp, circleBorder)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (customIcon != null) {
                        customIcon()
                    } else if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = iconTint,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            if (badgeContent != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 2.dp, top = 2.dp)
                ) {
                    badgeContent()
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp
        )
    }
}

@Composable
fun TrashWithClockIcon(isDark: Boolean = false) {
    val tint = if (isDark) Color(0xFF4CAF6E) else Color(0xFF379358)
    val bg = if (isDark) Color(0xFF1B2E21) else Color(0xFFEAF6ED)
    Box(
        modifier = Modifier.size(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Delete,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(26.dp)
        )
        Surface(
            shape = CircleShape,
            color = bg,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(13.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.padding(1.dp)
            )
        }
    }
}

@Composable
fun FrostedIosModal(
    onDismissRequest: () -> Unit,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    isDark: Boolean,
    actionButton: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var animateIn by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            animateIn = true
        }

        val scale by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (animateIn) 1.0f else 0.90f,
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                stiffness = 360f
            ),
            label = "frostedModalScale"
        )
        val alpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (animateIn) 1.0f else 0.0f,
            animationSpec = androidx.compose.animation.core.tween(180),
            label = "frostedModalAlpha"
        )
        val scrimAlpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (animateIn) (if (isDark) 0.65f else 0.55f) else 0.0f,
            animationSpec = androidx.compose.animation.core.tween(200),
            label = "frostedScrimAlpha"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                ),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 520.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // prevent closing when clicking inside modal
                    )
                    .shadow(elevation = 16.dp, shape = RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                color = if (isDark) Color(0xFF18181B) else Color(0xFFFFFFFF),
                border = BorderStroke(1.dp, if (isDark) Color(0xFF27272A) else Color(0xFFE3EFE6))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (icon != null) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isDark) Color(0xFF132A1C) else Color(0xFFEAF6ED),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = if (isDark) Color(0xFF4CAF6E) else Color(0xFF379358),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color(0xFFF4F4F5) else Color(0xFF1C1C1E),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (actionButton != null) {
                                actionButton()
                            }

                            IconButton(
                                onClick = onDismissRequest,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (isDark) Color(0xFF27272A) else Color(0xFFF2F4F7))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = if (isDark) Color(0xFFA1A1AA) else Color(0xFF5E6573),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    content()
                }
            }
        }
    }
}

