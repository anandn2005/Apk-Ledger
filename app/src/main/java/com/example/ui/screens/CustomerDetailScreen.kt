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
import com.example.ui.theme.*
import com.example.ui.utils.ReceiptData
import com.example.ui.utils.ReceiptGenerator
import com.example.ui.utils.StatementImageGenerator
import androidx.compose.ui.platform.testTag
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
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
    onDeleteCustomer: (String) -> Unit
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

    val isSelectionMode = selectedTxnIds.isNotEmpty()

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
                modifier = Modifier.padding(bottom = 70.dp)
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    actionColor = CreditGreen,
                    shape = RoundedCornerShape(14.dp)
                )
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
                        IconButton(onClick = {
                            selectedTxnIds = transactions.map { it.id }.toSet()
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
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
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
                            Icon(Icons.Default.RemoveCircle, contentDescription = "Sale", tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("SALE", fontWeight = FontWeight.Bold, color = Color.White)
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
                            Icon(Icons.Default.AddCircle, contentDescription = "Deposit", tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("DEPOSIT", fontWeight = FontWeight.Bold, color = Color.White)
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
            item {
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

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(width = 1.dp, color = cardBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
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
                            Text(
                                text = customer.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color(0xFFF4F4F5) else Color(0xFF1C1C1E),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .padding(end = 8.dp)
                            )

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
                            }
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Transactions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (transactions.isEmpty()) {
                item {
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
                    contentType = { "customer_transaction_item" }
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

            item { Spacer(modifier = Modifier.height(24.dp)) }
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

                    // Row 2: Date & Time
                    Text(
                        text = "${Formatters.formatDate(txn.date)} • ${Formatters.formatTime(txn.date)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Row 3: Payment Mode Badge + Status Badge
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val modeText = if (txn.paymentMethod.isNotBlank()) txn.paymentMethod else "CASH"
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
                    Text(
                        text = displayPrefix + Formatters.formatCurrency(txn.amount, currencySymbol),
                        style = MaterialTheme.typography.titleMedium.tabularNumbers(),
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isSettledPaid) CreditGreen else if (isGaveCredit) DebitRed else CreditGreen
                    )

                    Text(
                        text = if (isSettledPaid) "Settled" else if (isGaveCredit) "Sale (Due)" else "Deposit",
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

