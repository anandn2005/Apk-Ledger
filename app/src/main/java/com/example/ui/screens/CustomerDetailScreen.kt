package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CustomerEntity
import com.example.data.CustomerWithBalance
import com.example.data.InvoiceEntity
import com.example.data.TransactionEntity
import com.example.data.TransactionType
import kotlinx.coroutines.launch
import com.example.ui.AppHaptics
import com.example.ui.Formatters
import com.example.ui.components.EditCustomerDialog
import com.example.ui.components.EditTransactionDialog
import com.example.ui.components.PremiumEmptyState
import com.example.ui.components.ReceiptPreviewDialog
import com.example.ui.components.ScrollEdgeShadow
import com.example.ui.components.rememberCanScrollBack
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.ui.theme.*
import com.example.ui.utils.ReceiptData
import com.example.ui.utils.ReceiptGenerator
import com.example.ui.utils.StatementImageGenerator
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun CustomerDetailScreen(
    customerWithBalance: CustomerWithBalance,
    transactions: List<TransactionEntity>,
    currencySymbol: String,
    businessName: String = "",
    businessPhone: String = "",
    businessAddress: String = "",
    upiId: String = "",
    invoices: List<InvoiceEntity> = emptyList(),
    onBackClick: () -> Unit,
    onAddTransactionClick: (String) -> Unit,
    onUpdateTransaction: (TransactionEntity) -> Unit,
    onDeleteTransaction: (TransactionEntity) -> Unit,
    onUpdateCustomer: (CustomerEntity) -> Unit = {},
    onDeleteSelectedTransactions: (List<TransactionEntity>) -> Unit,
    onDeleteCustomer: (String) -> Unit,
    onMergeTransactions: (
        customerId: String,
        transactionsToMerge: List<TransactionEntity>,
        combineIntoSingleNetEntry: Boolean,
        customDescription: String,
        mergeDateTimestamp: Long
    ) -> Unit = { _, _, _, _, _ -> },
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val customer = customerWithBalance.customer
    val netBalance = customerWithBalance.netBalance
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var showDeleteCustomerConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteTxnsConfirmDialog by remember { mutableStateOf(false) }
    var showEditCustomerDialog by remember { mutableStateOf(false) }
    var editingTxn by remember { mutableStateOf<TransactionEntity?>(null) }
    var selectedTxnIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var previewReceiptData by remember { mutableStateOf<ReceiptData?>(null) }
    var mergeCandidates by remember { mutableStateOf<List<TransactionEntity>?>(null) }
    var mergeDefaultOldOnly by remember { mutableStateOf(false) }

    val isSelectionMode = selectedTxnIds.isNotEmpty()
    val thirtyDaysAgoMs = remember(transactions.size) {
        System.currentTimeMillis() - (30L * 24L * 3600L * 1000L)
    }
    val oldTransactions = remember(transactions, thirtyDaysAgoMs) {
        transactions.filter { it.date <= thirtyDaysAgoMs }
    }

    val listState = rememberLazyListState()
    val invoicesMap = remember(invoices) {
        val map = mutableMapOf<String, InvoiceEntity>()
        invoices.forEach { 
            map[it.id] = it
            if (it.invoiceNumber.isNotBlank()) map[it.invoiceNumber] = it
        }
        map
    }
    val canScrollBack = rememberCanScrollBack(listState)
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = 78.dp, start = 16.dp, end = 16.dp)
            ) { data ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    UnifiedToastBanner(
                        message = data.visuals.message,
                        actionLabel = data.visuals.actionLabel,
                        onActionClick = if (data.visuals.actionLabel != null) {
                            { data.performAction() }
                        } else null
                    )
                }
            }
        },
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = {
                        Text("${selectedTxnIds.size} Selected", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    },
                    navigationIcon = {
                        IconButton(onClick = { selectedTxnIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        if (selectedTxnIds.size >= 2) {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val selectedList = transactions.filter { selectedTxnIds.contains(it.id) }
                                    if (selectedList.size >= 2) {
                                        mergeDefaultOldOnly = false
                                        mergeCandidates = selectedList
                                    }
                                },
                                modifier = Modifier.testTag("merge_selected_txns_button")
                            ) {
                                Icon(
                                    Icons.Default.CallMerge,
                                    contentDescription = "Merge Selected Transactions",
                                    tint = CreditGreen
                                )
                            }
                        }

                        IconButton(onClick = {
                            selectedTxnIds = if (selectedTxnIds.size == transactions.size) {
                                emptySet()
                            } else {
                                transactions.map { it.id }.toSet()
                            }
                        }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                        }

                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showDeleteTxnsConfirmDialog = true
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = DebitRed)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Perfectly round back button container with subtle frosted effect and diffused drop shadow
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .shadow(
                                    elevation = 8.dp,
                                    shape = CircleShape,
                                    ambientColor = Color(0x0A000000),
                                    spotColor = Color(0x12000000)
                                )
                                .clip(CircleShape)
                                .background(androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
                                .border(0.5.dp, Color(0xFFF1F5F9), CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = true),
                                    onClick = onBackClick
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Spacer(modifier = Modifier.weight(1f))

                        Spacer(modifier = Modifier.width(8.dp))

                        // 3. Actions on the right with matching soft circular frosted containers
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (customer.phone.isNotBlank()) {
                                SoftCircularActionButton(
                                    icon = Icons.Default.Phone,
                                    contentDescription = "Call Customer",
                                    tint = MaterialTheme.colorScheme.primary,
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${customer.phone}"))
                                        context.startActivity(intent)
                                    }
                                )
                            }

                            // Single Combined Receipt Action Button
                            SoftCircularActionButton(
                                icon = Icons.Default.ReceiptLong,
                                contentDescription = "Receipt Viewer",
                                tint = MaterialTheme.colorScheme.primary,
                                onClick = {
                                    previewReceiptData = ReceiptGenerator.createCombinedReceiptData(
                                        customer = customer,
                                        transactions = transactions,
                                        invoices = invoices,
                                        businessName = businessName,
                                        businessAddress = businessAddress,
                                        businessPhone = businessPhone,
                                        upiId = upiId,
                                        currencySymbol = currencySymbol,
                                        netBalance = netBalance
                                    )
                                }
                            )

                            // One-tap Customer Statement PDF (#27)
                            SoftCircularActionButton(
                                icon = Icons.Default.PictureAsPdf,
                                contentDescription = "Share Statement PDF",
                                tint = CreditGreen,
                                onClick = {
                                    com.example.core.common.CustomerStatementPdfGenerator.generateAndShareStatement(
                                        context = context,
                                        customer = customer,
                                        transactions = transactions,
                                        businessName = businessName,
                                        businessPhone = businessPhone,
                                        businessAddress = businessAddress,
                                        upiId = upiId,
                                        currencySymbol = currencySymbol
                                    )
                                }
                            )

                            SoftCircularActionButton(
                                icon = Icons.Default.Edit,
                                contentDescription = "Edit Customer",
                                tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                onClick = { showEditCustomerDialog = true }
                            )

                            SoftCircularActionButton(
                                icon = Icons.Default.Delete,
                                contentDescription = "Delete Customer",
                                tint = DebitRed,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showDeleteCustomerConfirmDialog = true
                                }
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (!isSelectionMode) {
                Surface(
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = {
                                AppHaptics.performTap(null)
                                onAddTransactionClick(TransactionType.GAVE_CREDIT.name)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DebitRed,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .springPressable {
                                    onAddTransactionClick(TransactionType.GAVE_CREDIT.name)
                                }
                        ) {
                            Icon(Icons.Default.RemoveCircle, contentDescription = "Sale on credit", tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sale on credit", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Color.White, maxLines = 1)
                        }

                        Button(
                            onClick = {
                                AppHaptics.performTap(null)
                                onAddTransactionClick(TransactionType.GOT_PAYMENT.name)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CreditGreen,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .springPressable {
                                    onAddTransactionClick(TransactionType.GOT_PAYMENT.name)
                                }
                        ) {
                            Icon(Icons.Default.AddCircle, contentDescription = "Payment received", tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Payment received", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Color.White, maxLines = 1)
                        }
                    }
                }
            }
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
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            // Balance Card
            item(key = "customer_balance_header", contentType = "balance_header") {
                val isDark = isAppInDarkTheme()
                val cardBg = when {
                    netBalance > 0 -> if (isDark) StatusEmeraldDarkBg else StatusEmeraldLight
                    netBalance < 0 -> if (isDark) StatusRedDarkBg else StatusRedLight
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val cardBorder = when {
                    netBalance > 0 -> StatusEmerald.copy(alpha = if (isDark) 0.4f else 0.25f)
                    netBalance < 0 -> StatusRed.copy(alpha = if (isDark) 0.4f else 0.25f)
                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                }
                val badgeBg = when {
                    netBalance > 0 -> StatusEmerald.copy(alpha = if (isDark) 0.25f else 0.12f)
                    netBalance < 0 -> StatusRed.copy(alpha = if (isDark) 0.25f else 0.12f)
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                }
                val badgeTextColor = when {
                    netBalance > 0 -> if (isDark) StatusEmerald else CreditGreenDark
                    netBalance < 0 -> if (isDark) StatusRed else DebitRedDark
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                val (avatarBg, avatarFg) = remember(customer.name, isDark) {
                    com.example.feature.customers.CustomersViewModel.avatarColorsForName(customer.name, isDark)
                }
                val avatarInitial = remember(customer.name) {
                    customer.name.trim().take(1).uppercase().ifBlank { "C" }
                }

                val cardSharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        Modifier.sharedBounds(
                            sharedContentState = rememberSharedContentState(key = "customer_card_${customer.id}"),
                            animatedVisibilityScope = animatedVisibilityScope,
                            boundsTransform = { _, _ -> spring(dampingRatio = 0.82f, stiffness = 380f) }
                        )
                    }
                } else Modifier

                val avatarSharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        Modifier.sharedElement(
                            state = rememberSharedContentState(key = "customer_avatar_${customer.id}"),
                            animatedVisibilityScope = animatedVisibilityScope,
                            boundsTransform = { _, _ -> spring(dampingRatio = 0.82f, stiffness = 380f) }
                        )
                    }
                } else Modifier

                val nameSharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        Modifier.sharedBounds(
                            sharedContentState = rememberSharedContentState(key = "customer_name_${customer.id}"),
                            animatedVisibilityScope = animatedVisibilityScope,
                            boundsTransform = { _, _ -> spring(dampingRatio = 0.82f, stiffness = 380f) }
                        )
                    }
                } else Modifier

                val balanceSharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        Modifier.sharedBounds(
                            sharedContentState = rememberSharedContentState(key = "customer_balance_${customer.id}"),
                            animatedVisibilityScope = animatedVisibilityScope,
                            boundsTransform = { _, _ -> spring(dampingRatio = 0.82f, stiffness = 380f) }
                        )
                    }
                } else Modifier

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(width = 1.dp, color = cardBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = cardSharedModifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .padding(end = 8.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = avatarBg,
                                    border = BorderStroke(1.dp, avatarFg.copy(alpha = 0.25f)),
                                    modifier = avatarSharedModifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = avatarInitial,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = avatarFg
                                        )
                                    }
                                }

                                Text(
                                    text = customer.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color(0xFFF4F4F5) else Color(0xFF1C1C1E),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = nameSharedModifier
                                )
                            }

                            Surface(
                                shape = CircleShape,
                                color = badgeBg,
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = when {
                                        netBalance > 0 -> StatusEmerald.copy(alpha = 0.3f)
                                        netBalance < 0 -> StatusRed.copy(alpha = 0.3f)
                                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                    }
                                )
                            ) {
                                Text(
                                    text = when {
                                        netBalance > 0 -> "Receivable"
                                        netBalance < 0 -> "Payable"
                                        else -> "Settled"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeTextColor,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        AnimatedBalanceText(
                            amount = Math.abs(netBalance),
                            currencySymbol = currencySymbol,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = when {
                                netBalance > 0 -> if (isDark) StatusEmerald else CreditGreenDark
                                netBalance < 0 -> if (isDark) StatusRed else DebitRedDark
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            modifier = balanceSharedModifier
                        )

                        if (customer.area.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Area / Village: ${customer.area}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick Action Strip: Statement PDF (#27) & WhatsApp UPI Reminder (#25)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = {
                                    com.example.core.common.CustomerStatementPdfGenerator.generateAndShareStatement(
                                        context = context,
                                        customer = customer,
                                        transactions = transactions,
                                        businessName = businessName,
                                        businessPhone = businessPhone,
                                        businessAddress = businessAddress,
                                        upiId = upiId,
                                        currencySymbol = currencySymbol
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                modifier = Modifier.testTag("share_statement_pdf_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = CreditGreen, modifier = Modifier.size(14.dp))
                                    Text("PDF Statement", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            if (netBalance > 0) {
                                Surface(
                                    onClick = {
                                        val amountFormatted = Formatters.formatCurrency(Math.abs(netBalance), currencySymbol)
                                        val biz = businessName.ifBlank { "Our Store" }
                                        val upiLink = if (upiId.isNotBlank()) {
                                            "\nPay via UPI ($upiId): upi://pay?pa=${Uri.encode(upiId)}&pn=${Uri.encode(biz)}&am=${String.format(java.util.Locale.US, "%.2f", Math.abs(netBalance))}&cu=INR"
                                        } else ""
                                        val msg = "Namaste ${customer.name}, your pending balance with *$biz* is *$amountFormatted*.$upiLink\nThank you!"
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, msg)
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Send Payment Reminder"))
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = StatusEmerald.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, StatusEmerald.copy(alpha = 0.35f)),
                                    modifier = Modifier.testTag("whatsapp_upi_reminder_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(Icons.Default.Send, contentDescription = null, tint = CreditGreenDark, modifier = Modifier.size(14.dp))
                                        Text("Remind + UPI", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = badgeTextColor)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "transactions_section_header", contentType = "section_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Transactions (${transactions.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (transactions.size >= 2) {
                        val hasOld = oldTransactions.size >= 2
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                mergeDefaultOldOnly = hasOld
                                mergeCandidates = if (hasOld) oldTransactions else transactions
                            },
                            shape = RoundedCornerShape(50),
                            color = if (hasOld) StatusEmerald.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            border = BorderStroke(
                                1.dp,
                                if (hasOld) StatusEmerald.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier.testTag("compact_merge_txns_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CallMerge,
                                    contentDescription = "Merge older transactions",
                                    tint = if (hasOld) StatusEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (hasOld) "Compact 30d+ (${oldTransactions.size})" else "Merge",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (hasOld) CreditGreenDark else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (transactions.isEmpty()) {
                item(key = "transactions_empty_state", contentType = "empty_state") {
                    PremiumEmptyState(
                        title = "No collections logged for this period.",
                        subtitle = "Swipe from the bottom or tap below to record a sale or payment.",
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            } else {
                items(
                    items = transactions,
                    key = { it.id },
                    contentType = { txn ->
                        if (txn.type == TransactionType.GAVE_CREDIT.name || txn.type == TransactionType.INVOICE_BILL.name) {
                            "customer_txn_credit"
                        } else {
                            "customer_txn_payment"
                        }
                    }
                ) { txn ->
                    val isSelected = selectedTxnIds.contains(txn.id)
                    val assocInvoice = invoicesMap[txn.invoiceRef]
                    SwipeableTransactionItemCard(
                        txn = txn,
                        currencySymbol = currencySymbol,
                        isSelected = isSelected,
                        onClick = {
                            if (isSelectionMode) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedTxnIds = if (isSelected) selectedTxnIds - txn.id else selectedTxnIds + txn.id
                            } else {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                previewReceiptData = ReceiptGenerator.createReceiptData(
                                    txn = txn,
                                    customer = customer,
                                    businessName = businessName,
                                    businessAddress = businessAddress,
                                    businessPhone = businessPhone,
                                    upiId = upiId,
                                    currencySymbol = currencySymbol,
                                    associatedInvoice = assocInvoice
                                )
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectedTxnIds = if (isSelected) selectedTxnIds - txn.id else selectedTxnIds + txn.id
                        },
                        onEdit = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            editingTxn = txn
                        },
                        onDelete = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDeleteTransaction(txn)
                        }
                    )
                }
            }

            item(key = "transactions_bottom_spacer", contentType = "bottom_spacer") {
                Spacer(modifier = Modifier.height(24.dp))
            }
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

    if (showEditCustomerDialog) {
        EditCustomerDialog(
            customer = customer,
            onDismiss = { showEditCustomerDialog = false },
            onConfirm = { updated ->
                onUpdateCustomer(updated)
                showEditCustomerDialog = false
            }
        )
    }

    if (showDeleteCustomerConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteCustomerConfirmDialog = false },
            title = { Text("Delete Customer?") },
            text = { Text("Delete ${customer.name} and all associated records?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteCustomerConfirmDialog = false
                        onDeleteCustomer(customer.id)
                        onBackClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DebitRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteCustomerConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteTxnsConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteTxnsConfirmDialog = false },
            title = { Text("Delete ${selectedTxnIds.size} Selected?") },
            text = { Text("Selected items will be moved to trash.") },
            confirmButton = {
                Button(
                    onClick = {
                        val selectedList = transactions.filter { selectedTxnIds.contains(it.id) }
                        onDeleteSelectedTransactions(selectedList)
                        selectedTxnIds = emptySet()
                        showDeleteTxnsConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DebitRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteTxnsConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (editingTxn != null) {
        EditTransactionDialog(
            transaction = editingTxn!!,
            currencySymbol = currencySymbol,
            onDismiss = { editingTxn = null },
            onConfirm = { updated ->
                onUpdateTransaction(updated)
                editingTxn = null
            }
        )
    }

    if (previewReceiptData != null) {
        ReceiptPreviewDialog(
            receipt = previewReceiptData!!,
            onDismiss = { previewReceiptData = null }
        )
    }

    val activeMergeList = mergeCandidates
    if (activeMergeList != null && activeMergeList.size >= 2) {
        MergeTransactionsDialog(
            allCustomerTransactions = transactions,
            initialCandidates = activeMergeList,
            oldTransactionsCount = oldTransactions.size,
            initialOldOnly = mergeDefaultOldOnly,
            currencySymbol = currencySymbol,
            onDismiss = { mergeCandidates = null },
            onConfirmMerge = { txnsToMerge, combineIntoSingleNet, description, mergeDateTs ->
                onMergeTransactions(
                    customer.id,
                    txnsToMerge,
                    combineIntoSingleNet,
                    description,
                    mergeDateTs
                )
                selectedTxnIds = emptySet()
                mergeCandidates = null
            }
        )
    }
}

/**
 * Swipeable Entry Card displaying:
 * - 3 Rows: Row 1 = Description (with text blur / overflow handling), Row 2 = Date/Time, Row 3 = Payment Mode Badge.
 * - Right to left gesture reveals Share Receipt, Edit & Delete action buttons.
 * - Clicking anywhere else automatically closes the swipe drag offset.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeableTransactionItemCard(
    txn: TransactionEntity,
    currencySymbol: String,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val isGaveCredit = txn.type == TransactionType.GAVE_CREDIT.name || txn.type == TransactionType.INVOICE_BILL.name
    val isDue = txn.status == "DUE" || (isGaveCredit && txn.status != "PAID")
    val isSettledPaid = txn.status == "PAID"
    val isDark = isAppInDarkTheme()

    // Clean, direct interaction card without swipe gesture
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) BorderStroke(2.dp, PrimaryIndigo)
                 else if (isDark) BorderStroke(1.dp, DarkBorder)
                 else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .shadow(
                elevation = if (isSelected) 8.dp else 4.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = Color(0x06000000),
                spotColor = Color(0x0C000000)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
                // Selection or Status Icon
                if (isSelected) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (isSettledPaid) CreditGreenLight else if (isGaveCredit) DebitRedLight else CreditGreenLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSettledPaid) Icons.Default.CheckCircle else if (isGaveCredit) Icons.Default.ArrowOutward else Icons.Default.SouthWest,
                            contentDescription = null,
                            tint = if (isSettledPaid) CreditGreen else if (isGaveCredit) DebitRed else CreditGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // 3-Row Content Layout: Description, Date/Time, Payment Mode & Status Badges
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    // Row 1: Description with White/Surface Blur text overflow handling
                    Box(modifier = Modifier.fillMaxWidth()) {
                        val descText = if (txn.description.isNotBlank()) txn.description else if (isGaveCredit) "Sale / Credit Issued" else "Deposit Received"
                        Text(
                            text = descText,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (descText.length > 25) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .width(32.dp)
                                    .fillMaxHeight()
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                            )
                                        )
                                    )
                            )
                        }
                    }

                    // Row 2: Date & Time (memoized to prevent per-frame DateFormat allocations)
                    val formattedDateTime = remember(txn.date) {
                        "${Formatters.formatDate(txn.date)} • ${Formatters.formatTime(txn.date)}"
                    }
                    Text(
                        text = formattedDateTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Row 3: Payment Mode Badge + Status Badge (Never show duplicate "DUE DUE" chips #35)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val modeText = if (txn.paymentMethod.isNotBlank()) txn.paymentMethod else "CASH"
                        val showModeBadge = !(modeText.equals("DUE", ignoreCase = true) && isDue)
                        if (showModeBadge) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = when (modeText.uppercase()) {
                                            "UPI" -> Icons.Default.QrCode
                                            "BANK", "TRANSFER" -> Icons.Default.AccountBalance
                                            "CHEQUE" -> Icons.Default.Receipt
                                            else -> Icons.Default.Payments
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = modeText.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Status Badge (PAID or DUE)
                        if (isSettledPaid) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = CreditGreenLight,
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(11.dp),
                                        tint = CreditGreen
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "PAID",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = CreditGreen
                                    )
                                }
                            }
                        } else if (isDue) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFFEF3C7),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        modifier = Modifier.size(11.dp),
                                        tint = Color(0xFFD97706)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "DUE",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = Color(0xFFB45309)
                                    )
                                }
                            }
                        }
                    }
                }

                // Amount Column (Right side) - Strict Tabular Financial Typography
                Column(horizontalAlignment = Alignment.End) {
                    val displayPrefix = if (isSettledPaid) "" else if (isGaveCredit) "- " else "+ "
                    val formattedAmount = remember(txn.amount, currencySymbol, displayPrefix) {
                        displayPrefix + Formatters.formatCurrency(txn.amount, currencySymbol)
                    }
                    Text(
                        text = formattedAmount,
                        style = MaterialTheme.typography.titleMedium.tabularNumbers(),
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isSettledPaid) CreditGreen else if (isGaveCredit) DebitRed else CreditGreen
                    )

                    Text(
                        text = if (isSettledPaid) "Settled" else if (isGaveCredit) "Sale on credit" else "Payment received",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isSettledPaid) CreditGreen else if (isGaveCredit) DebitRed else CreditGreen
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Transaction",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier.size(15.dp)
                            )
                        }

                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Transaction",
                                tint = DebitRed.copy(alpha = 0.75f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }
    }

/**
 * Soft frosted circular action button replicating the subtle rounded icon containers from the reference design.
 */
@Composable
fun SoftCircularActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .shadow(
                elevation = 6.dp,
                shape = CircleShape,
                ambientColor = Color(0x08000000),
                spotColor = Color(0x0E000000)
            )
            .clip(CircleShape)
            .background(androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .border(0.5.dp, Color(0xFFF1F5F9), CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}

private enum class MergeScopeFilter {
    DEBITS_ONLY,
    ALL_TYPES,
    CREDITS_ONLY
}

@Composable
private fun MergeTransactionsDialog(
    allCustomerTransactions: List<TransactionEntity>,
    initialCandidates: List<TransactionEntity>,
    oldTransactionsCount: Int,
    initialOldOnly: Boolean,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onConfirmMerge: (
        txnsToMerge: List<TransactionEntity>,
        combineIntoSingleNet: Boolean,
        description: String,
        mergeDateTs: Long
    ) -> Unit
) {
    val isDark = isAppInDarkTheme()
    val haptic = LocalHapticFeedback.current
    val thirtyDaysAgoMs = remember { System.currentTimeMillis() - (30L * 24L * 3600L * 1000L) }

    // If opened from selection, keep user's selection as base pool; if opened from header pill, allow toggling 30d+ vs all
    val openedFromAllOrOld = initialCandidates.size == allCustomerTransactions.size ||
        (oldTransactionsCount >= 2 && initialOldOnly)
    var filterOldOnly by remember { mutableStateOf(initialOldOnly) }

    val basePool = remember(filterOldOnly, initialCandidates, allCustomerTransactions, openedFromAllOrOld) {
        if (!openedFromAllOrOld) {
            initialCandidates
        } else if (filterOldOnly) {
            val old = allCustomerTransactions.filter { it.date <= thirtyDaysAgoMs }
            if (old.size >= 2) old else initialCandidates
        } else {
            allCustomerTransactions
        }
    }

    val poolDebits = remember(basePool) {
        basePool.filter { it.type == TransactionType.GAVE_CREDIT.name || it.type == TransactionType.INVOICE_BILL.name }
    }
    val poolCredits = remember(basePool) {
        basePool.filter { it.type == TransactionType.GOT_PAYMENT.name }
    }

    var scopeFilter by remember(basePool) {
        mutableStateOf(
            when {
                poolDebits.size >= 2 && poolCredits.size == 1 -> MergeScopeFilter.DEBITS_ONLY
                poolDebits.size >= 2 && poolCredits.isEmpty() -> MergeScopeFilter.DEBITS_ONLY
                poolCredits.size >= 2 && poolDebits.isEmpty() -> MergeScopeFilter.CREDITS_ONLY
                else -> MergeScopeFilter.ALL_TYPES
            }
        )
    }

    val activeList = remember(basePool, poolDebits, poolCredits, scopeFilter) {
        when (scopeFilter) {
            MergeScopeFilter.DEBITS_ONLY -> poolDebits
            MergeScopeFilter.CREDITS_ONLY -> poolCredits
            MergeScopeFilter.ALL_TYPES -> basePool
        }
    }

    val activeDebits = remember(activeList) {
        activeList.filter { it.type == TransactionType.GAVE_CREDIT.name || it.type == TransactionType.INVOICE_BILL.name }
    }
    val activeCredits = remember(activeList) {
        activeList.filter { it.type == TransactionType.GOT_PAYMENT.name }
    }
    val hasBothTypes = activeDebits.isNotEmpty() && activeCredits.isNotEmpty()

    var combineSingleNet by remember(hasBothTypes, activeList.size) {
        mutableStateOf(hasBothTypes && activeList.size == 2)
    }
    // Separate mode requires at least 3 rows when both types exist, otherwise 2 rows -> 2 rows saves 0 space
    val canKeepSeparate = !hasBothTypes || activeList.size >= 3
    val effectiveSingleNet = combineSingleNet || (hasBothTypes && !canKeepSeparate)

    val debitSum = remember(activeDebits) { activeDebits.sumOf { it.amount } }
    val creditSum = remember(activeCredits) { activeCredits.sumOf { it.amount } }
    val netAmount = debitSum - creditSum

    val resultingRowCount = remember(activeDebits.size, activeCredits.size, effectiveSingleNet) {
        if (effectiveSingleNet) 1
        else (if (activeDebits.isNotEmpty()) 1 else 0) + (if (activeCredits.isNotEmpty()) 1 else 0)
    }

    val dateSpanLabel = remember(activeList) {
        if (activeList.isEmpty()) "" else {
            val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
            val minD = activeList.minOf { it.date }
            val maxD = activeList.maxOf { it.date }
            val s1 = sdf.format(Date(minD))
            val s2 = sdf.format(Date(maxD))
            if (s1 == s2) s1 else "$s1 – $s2"
        }
    }

    val autoDescription = remember(activeDebits.size, activeCredits.size, dateSpanLabel) {
        val parts = buildList {
            if (activeDebits.isNotEmpty()) add("${activeDebits.size} debit${if (activeDebits.size > 1) "s" else ""}")
            if (activeCredits.isNotEmpty()) add("${activeCredits.size} credit${if (activeCredits.size > 1) "s" else ""}")
        }.joinToString(" & ")
        if (dateSpanLabel.isNotBlank()) "Merged $parts ($dateSpanLabel)" else "Merged $parts"
    }

    var customDescription by remember(autoDescription) { mutableStateOf(autoDescription) }
    val mergeTimestamp = remember(activeList) {
        activeList.maxOfOrNull { it.date } ?: System.currentTimeMillis()
    }
    val isValidMerge = activeList.size >= 2 && resultingRowCount in 1 until activeList.size

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = if (isDark) Color(0xFF1C1C1E) else Color.White,
            border = BorderStroke(1.dp, if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E7EB)),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .widthIn(max = 500.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
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
                            color = StatusEmerald.copy(alpha = 0.14f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CallMerge,
                                    contentDescription = null,
                                    tint = StatusEmerald,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Merge Transactions",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Compact older rows with exact balance",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Optional 30+ Days Filter Toggle when opened from section header
                if (openedFromAllOrOld && oldTransactionsCount >= 2 && allCustomerTransactions.size > oldTransactionsCount) {
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            filterOldOnly = !filterOldOnly
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (filterOldOnly) StatusEmerald.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(
                            1.dp,
                            if (filterOldOnly) StatusEmerald.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = if (filterOldOnly) StatusEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "30+ days old only ($oldTransactionsCount)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Switch(
                                checked = filterOldOnly,
                                onCheckedChange = { filterOldOnly = it }
                            )
                        }
                    }
                }

                // Scope Chips (Debits Only / All / Credits Only)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "ENTRIES TO INCLUDE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.7.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (poolDebits.size >= 2) {
                            val selected = scopeFilter == MergeScopeFilter.DEBITS_ONLY
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    scopeFilter = MergeScopeFilter.DEBITS_ONLY
                                },
                                label = { Text("Debits (${poolDebits.size})", fontSize = 12.sp) },
                                modifier = Modifier.testTag("merge_scope_debits")
                            )
                        }
                        if (poolDebits.isNotEmpty() && poolCredits.isNotEmpty()) {
                            val selected = scopeFilter == MergeScopeFilter.ALL_TYPES
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    scopeFilter = MergeScopeFilter.ALL_TYPES
                                },
                                label = { Text("Debits + Credits (${basePool.size})", fontSize = 12.sp) },
                                modifier = Modifier.testTag("merge_scope_all")
                            )
                        }
                        if (poolCredits.size >= 2) {
                            val selected = scopeFilter == MergeScopeFilter.CREDITS_ONLY
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    scopeFilter = MergeScopeFilter.CREDITS_ONLY
                                },
                                label = { Text("Credits (${poolCredits.size})", fontSize = 12.sp) },
                                modifier = Modifier.testTag("merge_scope_credits")
                            )
                        }
                    }
                }

                // Output Mode (when both debits and credits are included)
                if (hasBothTypes) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "MERGE FORMAT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.7.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (canKeepSeparate) {
                                Surface(
                                    onClick = { combineSingleNet = false },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (!effectiveSingleNet) StatusEmerald.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (!effectiveSingleNet) StatusEmerald else Color.Transparent
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = "Separate (2 rows)",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (!effectiveSingleNet) CreditGreenDark else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "1 Debit + 1 Credit",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Surface(
                                onClick = { combineSingleNet = true },
                                shape = RoundedCornerShape(12.dp),
                                color = if (effectiveSingleNet) StatusEmerald.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(
                                    1.dp,
                                    if (effectiveSingleNet) StatusEmerald else Color.Transparent
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("merge_mode_single_net")
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "Single Net (1 row)",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (effectiveSingleNet) CreditGreenDark else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Debits minus Credits",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Live Before -> After Balance Preview Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isDark) Color(0xFF141B17) else Color(0xFFF0FDF4),
                    border = BorderStroke(1.dp, StatusEmerald.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${activeList.size} entries → $resultingRowCount entry",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) StatusEmerald else CreditGreenDark
                            )
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = StatusEmerald.copy(alpha = 0.16f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = null,
                                        tint = StatusEmerald,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "Exact Balance",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) StatusEmerald else CreditGreenDark
                                    )
                                }
                            }
                        }

                        if (activeDebits.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Debits (${activeDebits.size})",
                                    fontSize = 12.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = Formatters.formatCurrency(debitSum, currencySymbol),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DebitRed
                                )
                            }
                        }

                        if (activeCredits.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Credits (${activeCredits.size})",
                                    fontSize = 12.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = Formatters.formatCurrency(creditSum, currencySymbol),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CreditGreen
                                )
                            }
                        }

                        HorizontalDivider(color = StatusEmerald.copy(alpha = 0.2f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Net Impact",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${if (netAmount >= 0) "+" else "-"}${Formatters.formatCurrency(Math.abs(netAmount), currencySymbol)} ${if (netAmount > 0.005) "(Due)" else if (netAmount < -0.005) "(Credit)" else "(Settled)"}",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (netAmount > 0.005) DebitRed else CreditGreen
                            )
                        }
                    }
                }

                // Editable Summary Note
                OutlinedTextField(
                    value = customDescription,
                    onValueChange = { customDescription = it },
                    label = { Text("Summary Note") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("merge_summary_note_input")
                )

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onConfirmMerge(
                                activeList,
                                effectiveSingleNet,
                                customDescription,
                                mergeTimestamp
                            )
                        },
                        enabled = isValidMerge,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CreditGreen),
                        modifier = Modifier
                            .weight(1.4f)
                            .testTag("confirm_merge_transactions_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallMerge,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Merge (${activeList.size} → $resultingRowCount)",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}


