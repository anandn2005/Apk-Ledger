package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CustomerEntity
import com.example.data.InvoiceItem
import com.example.data.RateItemEntity
import com.example.data.ReceiptDraftManager
import com.example.ui.Formatters
import com.example.ui.LedgerUiState
import com.example.ui.components.AddCustomerDialog
import com.example.ui.components.LedgerOutlinedTextField
import com.example.ui.theme.CreditGreen
import com.example.ui.theme.DebitRed
import com.example.ui.theme.PrimaryBlue
import com.example.ui.utils.ReceiptData
import com.example.ui.utils.ReceiptGenerator
import com.example.ui.utils.ReceiptLineItem
import org.json.JSONArray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptGeneratorScreen(
    state: LedgerUiState,
    preselectedRateItems: List<RateItemEntity> = emptyList(),
    preselectedInvoiceItems: List<InvoiceItem> = emptyList(),
    preselectedCustomerId: String? = null,
    onBackClick: () -> Unit,
    onSaveInvoice: (customerId: String, customerName: String, items: List<InvoiceItem>, dueDays: Int, tax: Double, discount: Double, notes: String, paymentMode: String) -> Unit,
    onAddCustomer: ((name: String, phone: String, businessName: String, partyType: String, notes: String, openingBalance: Double) -> Unit)? = null
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val initialMatchedCustomer = state.customers.find { it.customer.id == preselectedCustomerId }
    val savedDraft = remember { ReceiptDraftManager.loadDraft(context) }

    var selectedCustomerId by remember {
        mutableStateOf<String?>(savedDraft?.customerId ?: initialMatchedCustomer?.customer?.id)
    }
    var customerNameInput by remember {
        mutableStateOf(
            savedDraft?.customerName?.ifBlank { initialMatchedCustomer?.customer?.name ?: "" }
                ?: (initialMatchedCustomer?.customer?.name ?: "")
        )
    }
    var customerMobileInput by remember {
        mutableStateOf(savedDraft?.customerMobile ?: (initialMatchedCustomer?.customer?.phone ?: ""))
    }
    var isCustomerDropdownExpanded by remember { mutableStateOf(false) }
    var showFullCustomerSheet by remember { mutableStateOf(false) }

    var dueDaysText by remember { mutableStateOf(savedDraft?.dueDays ?: "30") }
    var taxText by remember { mutableStateOf(savedDraft?.tax ?: "0") }
    var discountText by remember { mutableStateOf(savedDraft?.discount ?: "0") }
    var notes by remember { mutableStateOf(savedDraft?.notes ?: "") }
    var selectedPaymentMode by remember { mutableStateOf(savedDraft?.paymentMode ?: "UPI") }
    var isMoreOptionsExpanded by remember { mutableStateOf(false) }

    val items = remember {
        val list = mutableStateListOf<InvoiceItem>()
        if (savedDraft != null && savedDraft.items.isNotEmpty() && preselectedInvoiceItems.isEmpty() && preselectedRateItems.isEmpty()) {
            list.addAll(savedDraft.items)
        }
        if (preselectedInvoiceItems.isNotEmpty()) {
            list.addAll(preselectedInvoiceItems)
        }
        if (preselectedRateItems.isNotEmpty()) {
            val existingNames = list.map { it.description.lowercase().trim() }.toSet()
            preselectedRateItems.forEach { r ->
                if (!existingNames.contains(r.name.lowercase().trim())) {
                    val priceNum = if (r.sellPrice > 0) r.sellPrice
                    else r.getDisplaySellPrice().toDoubleOrNull()
                        ?: if (r.minSellPrice > 0) r.minSellPrice
                        else 0.0
                    list.add(InvoiceItem(description = r.name, quantity = 1.0, unitPrice = priceNum))
                }
            }
        }
        list
    }

    // Quick add single-line state
    var itemDesc by remember { mutableStateOf("") }
    var itemPriceText by remember { mutableStateOf("") }
    var itemQuantityText by remember { mutableStateOf("1") }
    var isItemDropdownExpanded by remember { mutableStateOf(false) }
    var itemForVariantPicker by remember { mutableStateOf<RateItemEntity?>(null) }

    // Item edit state (Quantity and Rate adjustment)
    var editingItemIndex by remember { mutableStateOf<Int?>(null) }
    var editItemDesc by remember { mutableStateOf("") }
    var editItemQtyText by remember { mutableStateOf("") }
    var editItemRateText by remember { mutableStateOf("") }

    val itemDescFocusRequester = remember { FocusRequester() }
    val itemPriceFocusRequester = remember { FocusRequester() }

    // Voice recognition handling
    var activeVoiceTarget by remember { mutableStateOf<String?>(null) }
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                when (activeVoiceTarget) {
                    "customer" -> {
                        customerNameInput = spokenText
                        isCustomerDropdownExpanded = true
                    }
                    "item" -> {
                        itemDesc = spokenText
                        isItemDropdownExpanded = true
                    }
                }
            }
        }
    }

    fun startVoiceInput(target: String) {
        activeVoiceTarget = target
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, if (target == "customer") "Speak Customer Name" else "Speak Item Name")
            }
            voiceLauncher.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Voice search is not available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    val cachedItemSuggestions = remember(state.rateItems, state.invoices) {
        val map = linkedMapOf<String, Pair<Double, String>>()
        state.rateItems.forEach { r ->
            if (r.name.isNotBlank()) {
                val price = if (r.sellPrice > 0) r.sellPrice
                else r.getDisplaySellPrice().toDoubleOrNull()
                    ?: if (r.minSellPrice > 0) r.minSellPrice
                    else 0.0
                val info = listOfNotNull(
                    r.category.takeIf { it.isNotBlank() && it != "ALL" },
                    r.unit.takeIf { it.isNotBlank() }
                ).joinToString(" • ")
                map[r.name.trim()] = Pair(price, info)
            }
        }
        state.invoices.forEach { inv ->
            inv.getItems().forEach { invItem ->
                val desc = invItem.description.trim()
                if (desc.isNotBlank() && !map.containsKey(desc)) {
                    map[desc] = Pair(invItem.unitPrice, "Previous Item")
                }
            }
        }
        map.map { (name, pair) -> Triple(name, pair.first, pair.second) }
    }

    val filteredItemSuggestions = remember(itemDesc, cachedItemSuggestions) {
        if (itemDesc.isBlank()) emptyList()
        else cachedItemSuggestions.filter { it.first.contains(itemDesc, ignoreCase = true) }
    }

    var isSubmitted by remember { mutableStateOf(false) }

    // Auto-save receipt draft
    LaunchedEffect(
        selectedCustomerId, customerNameInput, customerMobileInput,
        items.size, items.map { "${it.description}_${it.quantity}_${it.unitPrice}" },
        dueDaysText, taxText, discountText, notes, selectedPaymentMode
    ) {
        if (!isSubmitted) {
            ReceiptDraftManager.saveDraft(
                context = context,
                customerId = selectedCustomerId,
                customerName = customerNameInput,
                customerMobile = customerMobileInput,
                items = items.toList(),
                dueDays = dueDaysText,
                tax = taxText,
                discount = discountText,
                notes = notes,
                paymentMode = selectedPaymentMode
            )
        }
    }

    BackHandler { onBackClick() }

    val subtotal = items.sumOf { it.total }
    val taxPercent = taxText.replace("[^\\d.]".toRegex(), "").toDoubleOrNull() ?: 0.0
    val taxAmount = subtotal * (taxPercent / 100.0)
    val discountAmount = discountText.replace("[^\\d.]".toRegex(), "").toDoubleOrNull() ?: 0.0
    val grandTotal = Math.max(0.0, subtotal + taxAmount - discountAmount)

    fun handleClearDraft() {
        focusManager.clearFocus()
        keyboardController?.hide()
        ReceiptDraftManager.clearDraft(context)
        items.clear()
        customerNameInput = ""
        customerMobileInput = ""
        selectedCustomerId = null
        dueDaysText = "30"
        taxText = "0"
        discountText = "0"
        notes = ""
        selectedPaymentMode = "UPI"
        itemDesc = ""
        itemPriceText = ""
        itemQuantityText = "1"
    }

    fun submitAndProcess(andShare: Boolean) {
        if (customerNameInput.isBlank() || items.isEmpty()) return
        focusManager.clearFocus()
        keyboardController?.hide()
        isSubmitted = true
        ReceiptDraftManager.clearDraft(context)

        val finalCustId = selectedCustomerId
            ?: state.customers.find { it.customer.name.equals(customerNameInput.trim(), ignoreCase = true) }?.customer?.id
            ?: ""
        val baseName = customerNameInput.ifBlank { "Counter Customer" }
        val finalCustName = if (customerMobileInput.isNotBlank()) "$baseName (${customerMobileInput.trim()})" else baseName
        val days = dueDaysText.toIntOrNull() ?: 30
        val tax = taxPercent
        val discount = discountAmount

        // 1. Save invoice
        onSaveInvoice(finalCustId, finalCustName, items.toList(), days, tax, discount, notes, selectedPaymentMode)

        // 2. If requested, generate and share receipt
        if (andShare) {
            val receiptLines = items.mapIndexed { idx, itm ->
                ReceiptLineItem(
                    index = idx + 1,
                    name = itm.description,
                    quantity = itm.quantity,
                    unitPrice = itm.unitPrice,
                    unit = ""
                )
            }
            val receiptData = ReceiptData(
                receiptNumber = "REC-${System.currentTimeMillis() % 100000}",
                date = System.currentTimeMillis(),
                businessName = state.businessName.ifBlank { "My Business" },
                businessAddress = state.businessAddress.ifBlank { "Main Bazaar" },
                businessPhone = state.businessPhone,
                upiId = state.upiId.ifBlank { "merchant@upi" },
                customerName = finalCustName,
                customerPhone = customerMobileInput.ifBlank { null },
                items = receiptLines,
                subtotal = subtotal,
                taxAmount = taxAmount,
                discountAmount = discountAmount,
                partialPayment = if (selectedPaymentMode != "DUE") grandTotal else 0.0,
                grandTotal = grandTotal,
                paymentStatus = if (selectedPaymentMode != "DUE") "PAID" else "DUE",
                currencySymbol = state.currencySymbol
            )
            ReceiptGenerator.generateAndShareReceipt(context, receiptData)
        }

        onBackClick()
    }

    fun addCurrentQuickItem() {
        val price = itemPriceText.replace("[^\\d.]".toRegex(), "").toDoubleOrNull() ?: 0.0
        val qty = itemQuantityText.replace("[^\\d.]".toRegex(), "").toDoubleOrNull() ?: 1.0
        if (itemDesc.isNotBlank() && price > 0 && qty > 0) {
            items.add(InvoiceItem(itemDesc.trim(), qty, price))
            itemDesc = ""
            itemPriceText = ""
            itemQuantityText = "1"
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Generate Receipt",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { handleClearDraft() },
                        colors = ButtonDefaults.textButtonColors(contentColor = DebitRed)
                    ) {
                        Text(
                            text = "CLEAR",
                            fontWeight = FontWeight.Bold,
                            color = DebitRed,
                            letterSpacing = 0.5.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            // Pinned action bar with crystal-clear visual hierarchy
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 10.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Summary line
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${items.size} ${if (items.size == 1) "item" else "items"} • Subtotal: ${Formatters.formatCurrency(subtotal, state.currencySymbol)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (taxAmount > 0 || discountAmount > 0) {
                                val adjustments = buildList {
                                    if (taxAmount > 0) add("Tax: +${Formatters.formatCurrency(taxAmount, state.currencySymbol)}")
                                    if (discountAmount > 0) add("Disc: -${Formatters.formatCurrency(discountAmount, state.currencySymbol)}")
                                }.joinToString(" • ")
                                Text(
                                    text = adjustments,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Total:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                            Text(
                                text = Formatters.formatCurrency(grandTotal, state.currencySymbol),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (selectedPaymentMode == "DUE") DebitRed else MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Primary Share CTA vs Secondary Save Only
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Secondary Save Only button (compact outlined/tonal)
                        OutlinedButton(
                            onClick = { submitAndProcess(andShare = false) },
                            enabled = customerNameInput.isNotBlank() && items.isNotEmpty(),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("save_only_receipt_button")
                        ) {
                            Icon(
                                Icons.Default.Save,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Only", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }

                        // Sole prominent Primary: Share Receipt
                        Button(
                            onClick = { submitAndProcess(andShare = true) },
                            enabled = customerNameInput.isNotBlank() && items.isNotEmpty(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CreditGreen,
                                contentColor = Color.White,
                                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 4.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("share_receipt_primary_button")
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Share Receipt", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. CUSTOMER TYPEAHEAD & INLINE ADD
            val filteredCustomers = remember(customerNameInput, state.customers) {
                if (customerNameInput.isBlank()) state.customers
                else state.customers.filter {
                    it.customer.name.contains(customerNameInput, ignoreCase = true) ||
                            it.customer.phone.contains(customerNameInput) ||
                            it.customer.businessName.contains(customerNameInput, ignoreCase = true)
                }
            }
            val hasExactCustomerMatch = remember(customerNameInput, state.customers) {
                state.customers.any { it.customer.name.equals(customerNameInput.trim(), ignoreCase = true) }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Customer *",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!selectedCustomerId.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = CreditGreen.copy(alpha = 0.15f)
                        ) {
                            Text(
                                "Linked Ledger",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = CreditGreen,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else if (customerNameInput.isNotBlank()) {
                        Text(
                            text = "+ More Details",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { showFullCustomerSheet = true }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Typeahead Input with inline add & voice input
                ExposedDropdownMenuBox(
                    expanded = isCustomerDropdownExpanded && (filteredCustomers.isNotEmpty() || (customerNameInput.isNotBlank() && !hasExactCustomerMatch)),
                    onExpandedChange = { isCustomerDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = customerNameInput,
                        onValueChange = { newVal ->
                            customerNameInput = newVal
                            isCustomerDropdownExpanded = true
                            val matched = state.customers.find { it.customer.name.equals(newVal.trim(), ignoreCase = true) }
                            selectedCustomerId = matched?.customer?.id
                            if (matched != null && matched.customer.phone.isNotBlank()) {
                                customerMobileInput = matched.customer.phone
                            }
                        },
                        placeholder = { Text("Search or type customer name...", style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = {
                            Icon(Icons.Default.PersonSearch, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { startVoiceInput("customer") }) {
                                    Icon(Icons.Default.Mic, contentDescription = "Voice Input", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                }
                                if (customerNameInput.isNotBlank()) {
                                    IconButton(onClick = {
                                        customerNameInput = ""
                                        selectedCustomerId = null
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                    )

                    ExposedDropdownMenu(
                        expanded = isCustomerDropdownExpanded && (filteredCustomers.isNotEmpty() || (customerNameInput.isNotBlank() && !hasExactCustomerMatch)),
                        onDismissRequest = { isCustomerDropdownExpanded = false }
                    ) {
                        // Existing Matches
                        filteredCustomers.take(5).forEach { item ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(item.customer.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        val details = listOfNotNull(
                                            item.customer.businessName.takeIf { it.isNotBlank() },
                                            item.customer.phone.takeIf { it.isNotBlank() }
                                        ).joinToString(" • ")
                                        if (details.isNotBlank()) {
                                            Text(
                                                details,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    selectedCustomerId = item.customer.id
                                    customerNameInput = item.customer.name
                                    if (item.customer.phone.isNotBlank()) {
                                        customerMobileInput = item.customer.phone
                                    }
                                    isCustomerDropdownExpanded = false
                                }
                            )
                        }

                        // Inline Quick-Add Customer Option
                        if (customerNameInput.isNotBlank() && !hasExactCustomerMatch) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                        Column {
                                            Text(
                                                text = "+ Add \"${customerNameInput.trim()}\" as customer",
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = "Save to ledger instantly",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    val trimmedName = customerNameInput.trim()
                                    if (trimmedName.isNotBlank() && onAddCustomer != null) {
                                        onAddCustomer(trimmedName, customerMobileInput.trim(), "", CustomerEntity.PARTY_TYPE_CUSTOMER, "", 0.0)
                                        Toast.makeText(context, "Added $trimmedName to customers", Toast.LENGTH_SHORT).show()
                                    }
                                    isCustomerDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Compact inline phone input
                OutlinedTextField(
                    value = customerMobileInput,
                    onValueChange = { customerMobileInput = it },
                    placeholder = { Text("Customer mobile (optional)", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = {
                        Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { itemDescFocusRequester.requestFocus() })
                )
            }

            // 2. PAYMENT STATUS: COMPACT SEGMENTED PILL ROW (No card wrapper, no bloated helper text)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("UPI", "CASH", "DUE", "CHEQUE").forEach { mode ->
                    val isSelected = selectedPaymentMode == mode
                    val activeBg = if (mode == "DUE") DebitRed else MaterialTheme.colorScheme.primary
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clickable { selectedPaymentMode = mode },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) activeBg else Color.Transparent,
                        contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = mode,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // 3. ITEMIZED ENTRY & COMPACT LIST
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Items on Receipt",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${items.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                // Existing items list (compact rows above the quick-add line)
                if (items.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items.forEachIndexed { index, item ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "${index + 1}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.description,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${if (item.quantity % 1.0 == 0.0) item.quantity.toLong() else item.quantity} × ${Formatters.formatCurrency(item.unitPrice, state.currencySymbol)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = Formatters.formatCurrency(item.total, state.currencySymbol),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        IconButton(
                                            onClick = {
                                                editingItemIndex = index
                                                editItemDesc = item.description
                                                editItemQtyText = if (item.quantity % 1.0 == 0.0) item.quantity.toLong().toString() else item.quantity.toString()
                                                editItemRateText = if (item.unitPrice % 1.0 == 0.0) item.unitPrice.toLong().toString() else item.unitPrice.toString()
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Edit Item Quantity & Rate",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { items.removeAt(index) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = "Remove",
                                                tint = DebitRed,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "No items yet. Type item name below to quick-add.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                // SINGLE-LINE QUICK-ADD ROW
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Item Name with autocomplete dropdown & mic
                    ExposedDropdownMenuBox(
                        expanded = isItemDropdownExpanded && filteredItemSuggestions.isNotEmpty(),
                        onExpandedChange = { isItemDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = itemDesc,
                            onValueChange = { newVal ->
                                itemDesc = newVal
                                isItemDropdownExpanded = true
                            },
                            placeholder = { Text("Item Name / Description...", style = MaterialTheme.typography.bodyMedium) },
                            leadingIcon = {
                                Icon(Icons.Default.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { startVoiceInput("item") }) {
                                        Icon(Icons.Default.Mic, contentDescription = "Voice Input", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                                .fillMaxWidth()
                                .focusRequester(itemDescFocusRequester),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { itemPriceFocusRequester.requestFocus() })
                        )

                        ExposedDropdownMenu(
                            expanded = isItemDropdownExpanded && filteredItemSuggestions.isNotEmpty(),
                            onDismissRequest = { isItemDropdownExpanded = false }
                        ) {
                            filteredItemSuggestions.take(6).forEach { suggestion ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                                Text(suggestion.first, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                if (suggestion.third.isNotBlank()) {
                                                    Text(suggestion.third, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                            if (suggestion.second > 0) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = Formatters.formatCurrency(suggestion.second, state.currencySymbol),
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        val matchedRateItem = state.rateItems.find { it.name.trim().equals(suggestion.first.trim(), ignoreCase = true) }
                                        if (matchedRateItem != null && matchedRateItem.hasVariants()) {
                                            isItemDropdownExpanded = false
                                            itemForVariantPicker = matchedRateItem
                                        } else {
                                            itemDesc = suggestion.first
                                            if (suggestion.second > 0) {
                                                itemPriceText = if (suggestion.second % 1.0 == 0.0) suggestion.second.toLong().toString() else suggestion.second.toString()
                                            }
                                            isItemDropdownExpanded = false
                                            itemPriceFocusRequester.requestFocus()
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Stepper + Rate + Add button in single horizontal row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Compact Qty Stepper
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .height(44.dp)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 2.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    val curr = itemQuantityText.replace("[^\\d.]".toRegex(), "").toDoubleOrNull() ?: 1.0
                                    if (curr > 1.0) itemQuantityText = (curr - 1).let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(14.dp))
                            }

                            BasicTextField(
                                value = itemQuantityText,
                                onValueChange = { newVal ->
                                    if (newVal.all { it.isDigit() || it == '.' }) itemQuantityText = newVal
                                },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                singleLine = true,
                                modifier = Modifier.width(32.dp)
                            )

                            IconButton(
                                onClick = {
                                    val curr = itemQuantityText.replace("[^\\d.]".toRegex(), "").toDoubleOrNull() ?: 1.0
                                    itemQuantityText = (curr + 1).let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        // Rate field
                        OutlinedTextField(
                            value = itemPriceText,
                            onValueChange = { itemPriceText = it },
                            placeholder = { Text("Rate (${state.currencySymbol})", style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .focusRequester(itemPriceFocusRequester),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { addCurrentQuickItem() })
                        )

                        // Add button
                        val canAdd = itemDesc.isNotBlank() && (itemPriceText.replace("[^\\d.]".toRegex(), "").toDoubleOrNull() ?: 0.0) > 0
                        Button(
                            onClick = { addCurrentQuickItem() },
                            enabled = canAdd,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Item", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // 4. TAX, DISCOUNT & DUE DAYS (Progressive Disclosure)
            Surface(
                onClick = { isMoreOptionsExpanded = !isMoreOptionsExpanded },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Tax, Discount & Due Days",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Icon(
                        if (isMoreOptionsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = isMoreOptionsExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = dueDaysText,
                            onValueChange = { dueDaysText = it },
                            label = { Text("Due Days", style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                        )
                        OutlinedTextField(
                            value = taxText,
                            onValueChange = { taxText = it },
                            label = { Text("Tax %", style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                        )
                        OutlinedTextField(
                            value = discountText,
                            onValueChange = { discountText = it },
                            label = { Text("Disc (${state.currencySymbol})", style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                        )
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        placeholder = { Text("Notes / payment terms (optional)...", style = MaterialTheme.typography.bodySmall) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        })
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }

    // Modal Bottom Sheet for adding customer if requested via "+ More Details"
    if (showFullCustomerSheet) {
        AddCustomerDialog(
            initialPartyType = CustomerEntity.PARTY_TYPE_CUSTOMER,
            currencySymbol = state.currencySymbol,
            onDismiss = { showFullCustomerSheet = false },
            onConfirm = { name, phone, businessName, partyType, notes, openingBalance ->
                if (onAddCustomer != null) {
                    onAddCustomer(name, phone, businessName, partyType, notes, openingBalance)
                }
                customerNameInput = name
                customerMobileInput = phone
                showFullCustomerSheet = false
            }
        )
    }

    // Dialog to edit quantity and rate for an added receipt item
    if (editingItemIndex != null) {
        AlertDialog(
            onDismissRequest = { editingItemIndex = null },
            title = {
                Text(
                    text = "Edit Item Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = editItemDesc,
                        onValueChange = { editItemDesc = it },
                        label = { Text("Product / Item Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = editItemQtyText,
                            onValueChange = { editItemQtyText = it },
                            label = { Text("Quantity") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = editItemRateText,
                            onValueChange = { editItemRateText = it },
                            label = { Text("Rate (${state.currencySymbol})") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    val currentQty = editItemQtyText.replace("[^\\d.]".toRegex(), "").toDoubleOrNull() ?: 0.0
                    val currentRate = editItemRateText.replace("[^\\d.]".toRegex(), "").toDoubleOrNull() ?: 0.0
                    val calculatedTotal = currentQty * currentRate

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Calculated Total:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            Formatters.formatCurrency(calculatedTotal, state.currencySymbol),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val idx = editingItemIndex ?: return@Button
                        val q = editItemQtyText.replace("[^\\d.]".toRegex(), "").toDoubleOrNull() ?: 1.0
                        val r = editItemRateText.replace("[^\\d.]".toRegex(), "").toDoubleOrNull() ?: 0.0
                        if (editItemDesc.isNotBlank() && q > 0 && r >= 0) {
                            items[idx] = InvoiceItem(
                                description = editItemDesc.trim(),
                                quantity = q,
                                unitPrice = r
                            )
                            editingItemIndex = null
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Update Item")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingItemIndex = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (itemForVariantPicker != null) {
        val targetItem = itemForVariantPicker!!
        AlertDialog(
            onDismissRequest = { itemForVariantPicker = null },
            title = {
                Column {
                    Text(
                        text = "Select Size / Variant",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = targetItem.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    targetItem.getVariants().forEach { v ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val formattedDesc = "${targetItem.name} (${v.label})"
                                    val price = v.sellPrice
                                    val priceStr = if (price % 1.0 == 0.0) price.toLong().toString() else String.format(java.util.Locale.US, "%.2f", price)
                                    itemDesc = formattedDesc
                                    itemPriceText = priceStr
                                    itemForVariantPicker = null
                                    try { itemPriceFocusRequester.requestFocus() } catch (_: Exception) {}
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SubdirectoryArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = v.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = Formatters.formatCurrency(v.sellPrice, state.currencySymbol),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { itemForVariantPicker = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
