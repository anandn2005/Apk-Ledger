package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.asImageBitmap
import com.example.data.InvoiceEntity
import com.example.data.InvoiceItem
import com.example.ui.Formatters
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.example.ui.LedgerUiState
import com.example.ui.components.AnimatedPillSearchBar
import com.example.ui.components.IosScrollbarPill
import com.example.ui.components.PremiumEmptyState
import com.example.ui.components.ReceiptPreviewDialog
import com.example.ui.components.ScrollEdgeShadow
import com.example.ui.components.rememberCanScrollBack
import androidx.compose.foundation.lazy.rememberLazyListState
import com.example.ui.theme.*
import com.example.ui.utils.ReceiptGenerator
import kotlinx.coroutines.flow.distinctUntilChanged
import org.json.JSONArray

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun InvoicesScreen(
    state: LedgerUiState,
    onFilterChange: (String) -> Unit,
    onCreateInvoiceClick: () -> Unit,
    onRecordPayment: (InvoiceEntity, Double, String) -> Unit,
    onDeleteInvoice: (InvoiceEntity) -> Unit,
    onUpdateInvoice: (InvoiceEntity) -> Unit = {},
    onDeleteSelectedInvoices: (List<InvoiceEntity>) -> Unit = {}
) {
    var selectedInvoiceForView by remember { mutableStateOf<InvoiceEntity?>(null) }
    var selectedInvoiceForPayment by remember { mutableStateOf<InvoiceEntity?>(null) }
    var editingInvoice by remember { mutableStateOf<InvoiceEntity?>(null) }

    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode = selectedIds.isNotEmpty()
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val canScrollBack = rememberCanScrollBack(listState)

    var invoiceSearchQuery by remember { mutableStateOf("") }
    val filteredInvoices = remember(state.invoices, invoiceSearchQuery) {
        if (invoiceSearchQuery.isBlank()) {
            state.invoices
        } else {
            val q = invoiceSearchQuery.trim().lowercase()
            state.invoices.filter { inv ->
                inv.customerName.lowercase().contains(q) ||
                inv.invoiceNumber.lowercase().contains(q) ||
                inv.notes.lowercase().contains(q) ||
                inv.totalAmount.toString().contains(q)
            }
        }
    }
    val customerPhoneMap = remember(state.customers) {
        state.customers.associate { it.customer.name.lowercase() to it.customer.phone }
    }

    var isHeaderVisible by remember { mutableStateOf(true) }

    val pullToRevealConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val deltaY = available.y
                if (deltaY < -10f) {
                    if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 25) {
                        if (isHeaderVisible) {
                            isHeaderVisible = false
                        }
                    }
                } else if (deltaY > 10f) {
                    if (!isHeaderVisible) {
                        isHeaderVisible = true
                    }
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= 10
        }.distinctUntilChanged().collect { atTop ->
            if (atTop) {
                isHeaderVisible = true
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            if (isSelectionMode) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Exit selection", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Text(
                            text = "${selectedIds.size} Selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(onClick = {
                            if (selectedIds.size == state.invoices.size) {
                                selectedIds = emptySet()
                            } else {
                                selectedIds = state.invoices.map { it.id }.toSet()
                            }
                        }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }

                        IconButton(onClick = { showDeleteConfirmDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    // Collapsing Title & Filter Chips
                    AnimatedVisibility(
                        visible = isHeaderVisible,
                        enter = expandVertically(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        ) + fadeIn(animationSpec = tween(180)),
                        exit = shrinkVertically(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        ) + fadeOut(animationSpec = tween(180))
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Receipts & Invoices",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = (-0.5).sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Invoice Status Filter Tabs
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val filters = listOf(
                                    "ALL" to "All",
                                    "UNPAID" to "Unpaid",
                                    "OVERDUE" to "Overdue",
                                    "PAID" to "Paid"
                                )

                                items(filters) { (key, label) ->
                                    val isSelected = state.invoiceFilter == key
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { onFilterChange(key) },
                                        label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                                        leadingIcon = if (isSelected) {
                                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                        } else null,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                                            selectedLeadingIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            labelColor = MaterialTheme.colorScheme.onSurface
                                        ),
                                        shape = CircleShape
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }

                    // Search Bar: Permanently Visible at Top!
                    AnimatedPillSearchBar(
                        query = invoiceSearchQuery,
                        onQueryChange = { invoiceSearchQuery = it },
                        placeholderSequence = listOf(
                            "Search receipts & invoices...",
                            "Search by customer name...",
                            "Search by invoice #..."
                        ),
                        testTag = "invoice_search_input"
                    )
                }
            }
        }
    ) { padding ->
        if (showDeleteConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = { Text("Delete ${selectedIds.size} Selected?") },
                text = { Text("Selected items will be moved to trash.") },
                confirmButton = {
                    Button(
                        onClick = {
                            val selectedList = state.invoices.filter { selectedIds.contains(it.id) }
                            onDeleteSelectedInvoices(selectedList)
                            selectedIds = emptySet()
                            showDeleteConfirmDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DebitRed)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (filteredInvoices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                PremiumEmptyState(
                    title = if (invoiceSearchQuery.isNotBlank()) "No matching receipts found" else "No collections logged for this period.",
                    subtitle = if (invoiceSearchQuery.isNotBlank()) "Try checking the customer name or invoice number." else "Tap '+' to create a new receipt or invoice."
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(pullToRevealConnection)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(4.dp)) }

                    if (state.summary.overdueInvoiceCount > 0) {
                        item {
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = AmberWarningLight),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(AmberWarning),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "Overdue Alert",
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${state.summary.overdueInvoiceCount} Overdue Invoices Pending",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF78350F)
                                        )
                                        Text(
                                            text = "Review items and send payment reminders to clear receivables.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF92400E)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    items(
                        items = filteredInvoices,
                        key = { it.id },
                        contentType = { "invoice_card_item" }
                    ) { inv ->
                        val isSelected = selectedIds.contains(inv.id)
                        InvoiceCardItem(
                            invoice = inv,
                            currencySymbol = state.currencySymbol,
                            isSelected = isSelected,
                            onViewClick = {
                                if (isSelectionMode) {
                                    selectedIds = if (isSelected) selectedIds - inv.id else selectedIds + inv.id
                                } else {
                                    selectedInvoiceForView = inv
                                }
                            },
                            onLongClick = {
                                selectedIds = if (isSelected) selectedIds - inv.id else selectedIds + inv.id
                            },
                            onEditClick = { editingInvoice = inv },
                            onPaymentClick = { selectedInvoiceForPayment = inv },
                            onDeleteClick = { onDeleteInvoice(inv) }
                        )
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

                IosScrollbarPill(
                    listState = listState,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
    }

    // Edit Invoice Dialog
    editingInvoice?.let { inv ->
        com.example.ui.components.EditInvoiceDialog(
            invoice = inv,
            currencySymbol = state.currencySymbol,
            onDismiss = { editingInvoice = null },
            onConfirm = { updated ->
                onUpdateInvoice(updated)
                editingInvoice = null
            }
        )
    }

    // Invoice View / Receipt Preview Modal (Shows identical receipt as shared, without redundant tabs)
    selectedInvoiceForView?.let { inv ->
        val customerPhone = customerPhoneMap[inv.customerName.lowercase()]
        val receiptData = remember(inv, customerPhone, state.businessName, state.businessAddress, state.businessPhone, state.upiId, state.currencySymbol) {
            ReceiptGenerator.createReceiptDataFromInvoice(
                invoice = inv,
                customerPhone = customerPhone,
                businessName = state.businessName,
                businessAddress = state.businessAddress,
                businessPhone = state.businessPhone,
                upiId = state.upiId,
                currencySymbol = state.currencySymbol
            )
        }

        ReceiptPreviewDialog(
            receipt = receiptData,
            onDismiss = { selectedInvoiceForView = null },
            onRecordPaymentClick = if (inv.status != InvoiceEntity.STATUS_PAID) {
                {
                    val currentInv = inv
                    selectedInvoiceForView = null
                    selectedInvoiceForPayment = currentInv
                }
            } else null
        )
    }

    // Record Payment Sheet
    selectedInvoiceForPayment?.let { inv ->
        RecordPaymentDialog(
            invoice = inv,
            currencySymbol = state.currencySymbol,
            onDismiss = { selectedInvoiceForPayment = null },
            onConfirm = { amount, method ->
                onRecordPayment(inv, amount, method)
                selectedInvoiceForPayment = null
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InvoiceCardItem(
    invoice: InvoiceEntity,
    currencySymbol: String,
    isSelected: Boolean = false,
    onViewClick: () -> Unit,
    onLongClick: () -> Unit,
    onEditClick: () -> Unit = {},
    onPaymentClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val isOverdue = invoice.dueDate < System.currentTimeMillis() && invoice.status != InvoiceEntity.STATUS_PAID
    val statusColor = when {
        invoice.status == InvoiceEntity.STATUS_PAID -> CreditGreen
        isOverdue -> DebitRed
        else -> AmberWarning
    }

    val statusText = when {
        invoice.status == InvoiceEntity.STATUS_PAID -> "PAID"
        isOverdue -> "OVERDUE"
        invoice.status == InvoiceEntity.STATUS_PARTIAL -> "PARTIAL (${Formatters.formatCurrency(invoice.paidAmount, currencySymbol)})"
        else -> "UNPAID"
    }

    val isDark = isAppInDarkTheme()

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else if (isDark) Color(0xFF1E1E20) else Color.White
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                 else androidx.compose.foundation.BorderStroke(1.dp, if (isDark) Color(0xFF333336) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onViewClick,
                onLongClick = onLongClick
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSelected) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                            }
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.Receipt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = invoice.invoiceNumber,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (badgeBg, badgeText) = when {
                        invoice.status == InvoiceEntity.STATUS_PAID -> StatusEmerald to Color.White
                        isOverdue -> StatusRed to Color.White
                        else -> StatusCoral to Color.White
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = badgeBg
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = badgeText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    IconButton(onClick = onEditClick, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Invoice", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }

                    IconButton(onClick = onDeleteClick, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Invoice", tint = StatusRed, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = invoice.customerName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Issued: ${Formatters.formatDate(invoice.issueDate)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Due: ${Formatters.formatDate(invoice.dueDate)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOverdue) StatusRed else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isOverdue) FontWeight.Bold else FontWeight.Normal
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Total Amount",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = Formatters.formatCurrency(invoice.totalAmount, currencySymbol),
                        style = MaterialTheme.typography.titleLarge.tabularNumbers(),
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (invoice.status != InvoiceEntity.STATUS_PAID) {
                        Button(
                            onClick = onPaymentClick,
                            shape = RoundedCornerShape(12.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StatusEmeraldLight,
                                contentColor = CreditGreenDark
                            )
                        ) {
                            Text("Pay", fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = onViewClick,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("View", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun RecordPaymentDialog(
    invoice: InvoiceEntity,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, method: String) -> Unit
) {
    val remainingAmount = invoice.totalAmount - invoice.paidAmount
    var amountText by remember { mutableStateOf(remainingAmount.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }) }
    var selectedMethod by remember { mutableStateOf("UPI") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Payment: ${invoice.invoiceNumber}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Remaining: ${Formatters.formatCurrency(remainingAmount, currencySymbol)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Payment Received Amount ($currencySymbol)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val amt = amountText.toDoubleOrNull() ?: 0.0
                            if (amt > 0) {
                                onConfirm(amt, selectedMethod)
                            }
                        }
                    )
                )

                Text(
                    text = "Payment Method",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("CASH", "UPI", "BANK_TRANSFER", "CHEQUE").forEach { method ->
                        FilterChip(
                            selected = selectedMethod == method,
                            onClick = { selectedMethod = method },
                            label = { Text(method) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull() ?: 0.0
                    if (amt > 0) {
                        onConfirm(amt, selectedMethod)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = CreditGreenLight,
                    contentColor = CreditGreenDark
                )
            ) {
                Text("Confirm", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
