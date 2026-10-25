package com.example.ui.components
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.platform.testTag

import androidx.compose.animation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.example.data.CustomerEntity
import com.example.data.InvoiceItem
import com.example.data.TransactionType
import com.example.ui.Formatters
import com.example.ui.AppHaptics
import com.example.ui.theme.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import com.example.data.ReceiptDraftManager
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun rememberIsolatedNestedScrollConnection(): NestedScrollConnection {
    return remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Intercept downward drag when at the top so it never bubbles to parent bottom sheet drag
                return if (available.y > 0f) Offset(0f, available.y) else Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Completely isolate internal modal scrolling: consume remaining vertical delta so parent sheet never closes
                return Offset(0f, available.y)
            }
            override suspend fun onPreFling(available: Velocity): Velocity = Velocity.Zero
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // Consume vertical velocity so flinging never auto-closes the sheet
                return Velocity(0f, available.y)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)


@Composable
fun LedgerOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
        unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        cursorColor = MaterialTheme.colorScheme.primary
    )
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        prefix = prefix,
        suffix = suffix,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        interactionSource = interactionSource,
        shape = shape,
        colors = colors
    )
}


@Composable
fun LedgerLedgerOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
        unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        cursorColor = MaterialTheme.colorScheme.primary
    )
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        prefix = prefix,
        suffix = suffix,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        interactionSource = interactionSource,
        shape = shape,
        colors = colors
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomerDialog(
    initialPartyType: String = CustomerEntity.PARTY_TYPE_CUSTOMER,
    currencySymbol: String = "₹",
    onDismiss: () -> Unit,
    onConfirmWithArea: ((name: String, phone: String, businessName: String, partyType: String, notes: String, openingBalance: Double, area: String) -> Unit)? = null,
    onConfirm: (name: String, phone: String, businessName: String, partyType: String, notes: String, openingBalance: Double) -> Unit
) {
    BackHandler { onDismiss() }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val view = LocalView.current

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { true }
    )

    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var area by remember { mutableStateOf("") }
    var businessName by remember { mutableStateOf("") }
    var partyType by remember { mutableStateOf(initialPartyType) }
    var openingBalanceText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var isMoreDetailsExpanded by remember { mutableStateOf(false) }
    var isSubmitted by remember { mutableStateOf(false) }

    val nameFocusRequester = remember { FocusRequester() }
    val phoneFocusRequester = remember { FocusRequester() }

    // Voice recognition launcher for customer name
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                name = spokenText
            }
        }
    }

    fun startVoiceInput() {
        try {
            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Speak Customer Name")
            }
            voiceLauncher.launch(intent)
        } catch (_: Exception) {
            android.widget.Toast.makeText(context, "Voice search is not available on this device", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        try {
            nameFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    fun handleSave() {
        if (name.isBlank() && businessName.isBlank()) return
        AppHaptics.performConfirm(view)
        focusManager.clearFocus()
        keyboardController?.hide()
        val parsedBal = openingBalanceText.replace("[^\\d.]".toRegex(), "").toDoubleOrNull() ?: 0.0
        if (onConfirmWithArea != null) {
            onConfirmWithArea(name.trim(), phone.trim(), businessName.trim(), partyType, notes.trim(), parsedBal, area.trim())
        } else {
            onConfirm(name.trim(), phone.trim(), businessName.trim(), partyType, notes.trim(), parsedBal)
        }
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        scrimColor = Color.Black.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Title & Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Add Customer",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp).testTag("close_add_customer")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // PRIMARY ROW: Name (with mic voice input)
            LedgerOutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Customer Name *") },
                placeholder = { Text("e.g. Ramesh Kumar, Sunita Verma") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                trailingIcon = {
                    IconButton(onClick = { startVoiceInput() }) {
                        Icon(Icons.Default.Mic, contentDescription = "Voice Input", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(nameFocusRequester),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { phoneFocusRequester.requestFocus() })
            )

            // COMPACT INLINE PHONE
            LedgerOutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone Number (Optional)") },
                placeholder = { Text("10-digit mobile number") },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(phoneFocusRequester),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { handleSave() })
            )

            // PROGRESSIVE DISCLOSURE: Add more details toggle
            Surface(
                onClick = { isMoreDetailsExpanded = !isMoreDetailsExpanded },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Add more details (Opening balance, Shop, Notes)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Icon(
                        imageVector = if (isMoreDetailsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = isMoreDetailsExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Opening Balance
                    LedgerOutlinedTextField(
                        value = openingBalanceText,
                        onValueChange = { openingBalanceText = it },
                        label = { Text("Opening Balance ($currencySymbol)") },
                        leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                    )

                    // Area / Village (#26)
                    LedgerOutlinedTextField(
                        value = area,
                        onValueChange = { area = it },
                        label = { Text("Area / Village (e.g. Hisampur, Kekri)") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )

                    // Shop Name
                    LedgerOutlinedTextField(
                        value = businessName,
                        onValueChange = { businessName = it },
                        label = { Text("Shop / Business Name") },
                        leadingIcon = { Icon(Icons.Default.Store, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )

                    // Notes / Address
                    LedgerOutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes / Address") },
                        leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 2,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { handleSave() })
                    )
                }
            }

            // Inline validation explanation when Save is disabled (#37)
            if (name.isBlank() && businessName.isBlank()) {
                Text(
                    text = "Enter a customer name to enable Save Customer",
                    style = MaterialTheme.typography.labelSmall,
                    color = DebitRed,
                    fontWeight = FontWeight.Medium
                )
            }

            // PRIMARY ACTION BUTTON
            Button(
                onClick = { handleSave() },
                enabled = name.isNotBlank() || businessName.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CreditGreen,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_customer_button")
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Save Customer",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionDialog(
    initialCustomerId: String? = null,
    initialType: String = TransactionType.GAVE_CREDIT.name,
    initialDateTimestamp: Long? = null,
    customers: List<com.example.data.CustomerWithBalance>,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onAddCustomerInline: ((String) -> String)? = null,
    onConfirm: (customerId: String, amount: Double, type: String, description: String, paymentMethod: String, invoiceRef: String, dateTimestamp: Long) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isVisible by remember { mutableStateOf(false) }

    val dismissWithAnimation: () -> Unit = {
        isVisible = false
        onDismiss()
    }

    BackHandler { dismissWithAnimation() }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current

    val initialCustomer = customers.find { it.customer.id == initialCustomerId }
    var customerSearchQuery by remember { mutableStateOf(initialCustomer?.customer?.name ?: "") }
    var selectedCustomerId by remember { mutableStateOf(initialCustomerId ?: customers.firstOrNull()?.customer?.id ?: "") }
    var isCustomerDropdownExpanded by remember { mutableStateOf(false) }

    val filteredCustomers = remember(customerSearchQuery, customers) {
        if (customerSearchQuery.isBlank()) customers
        else customers.filter {
            it.customer.name.contains(customerSearchQuery, ignoreCase = true) ||
            it.customer.phone.contains(customerSearchQuery) ||
            it.customer.businessName.contains(customerSearchQuery, ignoreCase = true)
        }
    }

    var selectedType by remember { mutableStateOf(initialType) }
    var amountText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var saleStatusOption by remember { mutableStateOf("DUE") } // Option 1: DUE (default), Option 2: PAID
    var selectedMethod by remember { mutableStateOf("UPI") } // Option 1: UPI, Option 2: CASH
    var invoiceRef by remember { mutableStateOf("") }
    var dateText by remember(initialDateTimestamp) {
        mutableStateOf(Formatters.formatDate(initialDateTimestamp ?: System.currentTimeMillis()))
    }
    
    var showSuccessFlash by remember { mutableStateOf(false) }
    var successFlashMessage by remember { mutableStateOf("") }

    val amountFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            amountFocusRequester.requestFocus()
            keyboardController?.show()
        } catch (_: Exception) {}
    }

    val isSale = selectedType == TransactionType.GAVE_CREDIT.name
    val saleLabel = "SALE"
    val depositLabel = "DEPOSIT"

    val handleSaveTransaction: () -> Unit = {
        val amt = amountText.replace("[^\\d.]".toRegex(), "").toDoubleOrNull() ?: 0.0
        val effectiveCustId = if (selectedCustomerId.isNotBlank()) selectedCustomerId else customers.firstOrNull()?.customer?.id ?: ""
        if (effectiveCustId.isNotBlank() && amt > 0) {
            AppHaptics.performConfirm(view)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)

            val parsedTs = Formatters.parseDate(dateText) ?: System.currentTimeMillis()
            val custName = customers.find { it.customer.id == effectiveCustId }?.customer?.name
                ?: customerSearchQuery.ifBlank { "Customer" }
            val effectiveMethod = if (isSale) {
                if (saleStatusOption == "PAID") selectedMethod else "DUE"
            } else {
                selectedMethod
            }
            onConfirm(effectiveCustId, amt, selectedType, description, effectiveMethod, invoiceRef, parsedTs)

            successFlashMessage = "Saved $currencySymbol$amountText for $custName"
            showSuccessFlash = true

            // Continuous Entry Loop: Clear 'Amount' and 'Customer Name', retain 'Date' and 'Payment Mode'
            amountText = ""
            customerSearchQuery = ""
            selectedCustomerId = ""
            description = ""
            invoiceRef = ""

            // Keep numeric keyboard open for back-to-back entries
            coroutineScope.launch {
                try {
                    amountFocusRequester.requestFocus()
                    keyboardController?.show()
                } catch (_: Exception) {}
                delay(2200)
                showSuccessFlash = false
            }
        }
    }

    val isDark = isAppInDarkTheme()

    androidx.compose.ui.window.Dialog(
        onDismissRequest = dismissWithAnimation,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        LedgerMateTheme(darkTheme = isDark) {
            val specularBorderBrush = getGlassSpecularBorderBrush(isDark)
            val glassBg = MaterialTheme.colorScheme.surface

            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f)
                ) + fadeIn(animationSpec = tween(300)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)
                ) + fadeOut(animationSpec = tween(250))
            ) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        Surface(
                            color = glassBg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .border(BorderStroke(1.dp, if (isDark) specularBorderBrush else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(if (isSale) (if (isDark) StatusRedDarkBg else StatusRedLight) else (if (isDark) StatusEmeraldDarkBg else StatusEmeraldLight)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isSale) Icons.Default.RemoveCircle else Icons.Default.AddCircle,
                                            contentDescription = null,
                                            tint = if (isSale) DebitRed else CreditGreen,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Text(
                                        text = if (isSale) "Record Sale" else "Record Deposit",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                IconButton(
                                    onClick = dismissWithAnimation,
                                    modifier = Modifier.testTag("close_add_transaction")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        Surface(
                            color = glassBg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .imePadding()
                                .navigationBarsPadding()
                                .border(BorderStroke(1.dp, if (isDark) specularBorderBrush else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))))
                        ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    dismissWithAnimation()
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                            ) {
                                Text("Done / Close")
                            }

                            Button(
                                onClick = {
                                    handleSaveTransaction()
                                },
                                enabled = (customerSearchQuery.isNotBlank() || selectedCustomerId.isNotBlank()) && (amountText.replace("[^\\d.]".toRegex(), "").toDoubleOrNull() ?: 0.0) > 0,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSale) DebitRed else CreditGreen,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(52.dp)
                                    .testTag("save_transaction_button")
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isSale) "Save Sale" else "Save Deposit",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Continuous Entry Flash Banner
                    AnimatedVisibility(
                        visible = showSuccessFlash,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Surface(
                            color = if (isDark) StatusEmeraldDarkBg else StatusEmeraldLight,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, CreditGreen),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CreditGreen, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "✓ $successFlashMessage — Ready for next entry",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) StatusEmerald else CreditGreenDark
                                )
                            }
                        }
                    }

                    // Sale / Deposit Toggle Pills
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedType = TransactionType.GAVE_CREDIT.name },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSale) DebitRed else Color.Transparent,
                            contentColor = if (isSale) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 12.dp)) {
                                Text(saleLabel, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedType = TransactionType.GOT_PAYMENT.name },
                            shape = RoundedCornerShape(12.dp),
                            color = if (!isSale) CreditGreen else Color.Transparent,
                            contentColor = if (!isSale) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 12.dp)) {
                                Text(depositLabel, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    // Party Selector
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Customer *",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        ExposedDropdownMenuBox(
                            expanded = isCustomerDropdownExpanded && filteredCustomers.isNotEmpty(),
                            onExpandedChange = { isCustomerDropdownExpanded = it }
                        ) {
                            LedgerOutlinedTextField(
                                value = customerSearchQuery,
                                onValueChange = { newVal ->
                                    customerSearchQuery = newVal
                                    isCustomerDropdownExpanded = true
                                    val matched = customers.find { it.customer.name.equals(newVal.trim(), ignoreCase = true) }
                                    if (matched != null) {
                                        selectedCustomerId = matched.customer.id
                                    }
                                },
                                placeholder = { Text("Search customer by name or phone...") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                trailingIcon = {
                                    if (selectedCustomerId.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = CreditGreen.copy(alpha = 0.15f),
                                            modifier = Modifier.padding(end = 4.dp)
                                        ) {
                                            Text(
                                                "Selected",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = CreditGreen,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    } else {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCustomerDropdownExpanded)
                                    }
                                },
                                modifier = Modifier
                                    .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true)
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(
                                    onNext = { amountFocusRequester.requestFocus() }
                                )
                            )

                            val hasExactMatch = remember(customerSearchQuery, customers) {
                                val q = customerSearchQuery.trim()
                                q.isNotBlank() && customers.any { it.customer.name.equals(q, ignoreCase = true) }
                            }

                            ExposedDropdownMenu(
                                expanded = isCustomerDropdownExpanded && (filteredCustomers.isNotEmpty() || (customerSearchQuery.isNotBlank() && !hasExactMatch)),
                                onDismissRequest = { isCustomerDropdownExpanded = false }
                            ) {
                                filteredCustomers.take(8).forEach { item ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(item.customer.name, fontWeight = FontWeight.Bold)
                                                val details = listOfNotNull(
                                                    item.customer.businessName.takeIf { it.isNotBlank() },
                                                    item.customer.phone.takeIf { it.isNotBlank() },
                                                    "Bal: " + Formatters.formatCurrency(item.netBalance, currencySymbol)
                                                ).joinToString(" • ")
                                                Text(
                                                    details,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectedCustomerId = item.customer.id
                                            customerSearchQuery = item.customer.name
                                            isCustomerDropdownExpanded = false
                                            amountFocusRequester.requestFocus()
                                        }
                                    )
                                }

                                if (customerSearchQuery.isNotBlank() && !hasExactMatch) {
                                    if (filteredCustomers.isNotEmpty()) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    }
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.AddCircleOutline,
                                                    contentDescription = null,
                                                    tint = Color(0xFF379358),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = "+ Add \"${customerSearchQuery.trim()}\" as new customer",
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF379358)
                                                )
                                            }
                                        },
                                        onClick = {
                                            val typedName = customerSearchQuery.trim()
                                            if (typedName.isNotBlank()) {
                                                val createdId = onAddCustomerInline?.invoke(typedName) ?: ""
                                                if (createdId.isNotBlank()) {
                                                    selectedCustomerId = createdId
                                                }
                                                isCustomerDropdownExpanded = false
                                                amountFocusRequester.requestFocus()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Amount and Date Inputs
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LedgerOutlinedTextField(
                            value = amountText,
                            onValueChange = { amountText = it },
                            textStyle = LocalTextStyle.current.tabularNumbers(),
                            label = { Text("Amount ($currencySymbol) *") },
                            leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                            modifier = Modifier
                                .weight(1.05f)
                                .focusRequester(amountFocusRequester),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Right) }
                            )
                        )

                        FluentDateInputField(
                            value = dateText,
                            onValueChange = { dateText = it },
                            label = "Date (DD/MM/YYYY)",
                            modifier = Modifier.weight(1.15f),
                            imeAction = ImeAction.Next,
                            onImeAction = { focusManager.moveFocus(FocusDirection.Down) }
                        )
                    }

                    // Description / Item Notes
                    LedgerOutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description / Item Notes (Optional)") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                handleSaveTransaction()
                            }
                        )
                    )

                    // Payment Status & Method Selection (2-Pill System for Sale)
                    if (isSale) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Sale Payment Status:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // 2-Pill System: Due (1st Option) | Paid (2nd Option)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val options = listOf("DUE" to "Due (Credit)", "PAID" to "Paid (Immediate)")
                                options.forEach { (key, label) ->
                                    val isSelected = saleStatusOption == key
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { saleStatusOption = key },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) (if (key == "DUE") DebitRed else CreditGreen) else Color.Transparent,
                                        contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                                            Text(
                                                text = label,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }

                            // Payment Method reveals from behind 2-pill system when PAID
                            AnimatedVisibility(
                                visible = saleStatusOption == "PAID",
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Payment Method:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                            .padding(3.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        val methods = listOf("UPI", "CASH")
                                        methods.forEach { method ->
                                            val isSelected = selectedMethod == method
                                            Surface(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable { selectedMethod = method },
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isSelected) CreditGreen else Color.Transparent,
                                                contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                            ) {
                                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                                    Text(
                                                        text = method,
                                                        fontSize = 12.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Payment Method Chips for Deposit
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Payment Mode:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                listOf("UPI", "CASH", "BANK_TRANSFER", "CHEQUE").forEach { method ->
                                    FilterChip(
                                        selected = selectedMethod == method,
                                        onClick = { selectedMethod = method },
                                        label = { Text(method, fontWeight = if (selectedMethod == method) FontWeight.Bold else FontWeight.Normal) },
                                        shape = RoundedCornerShape(12.dp)
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

// CreateInvoiceDialog has been migrated to full-screen ReceiptGeneratorScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionDialog(
    transaction: com.example.data.TransactionEntity,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onConfirm: (updatedTransaction: com.example.data.TransactionEntity) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isVisible by remember { mutableStateOf(false) }

    val dismissWithAnimation: () -> Unit = {
        isVisible = false
        onDismiss()
    }

    BackHandler { dismissWithAnimation() }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var selectedType by remember { mutableStateOf(transaction.type) }
    var amountText by remember { mutableStateOf(transaction.amount.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }) }
    var description by remember { mutableStateOf(transaction.description) }
    var selectedMethod by remember { mutableStateOf(transaction.paymentMethod.ifBlank { "CASH" }) }
    var dateText by remember { mutableStateOf(Formatters.formatDate(transaction.date)) }
    var isSubmitted by remember { mutableStateOf(false) }

    if (isSubmitted) {
        LaunchedEffect(Unit) {
            val amt = amountText.replace("[^\\d.]".toRegex(), "").toDoubleOrNull() ?: 0.0
            val parsedTs = Formatters.parseDate(dateText) ?: transaction.date
            onConfirm(
                transaction.copy(
                    amount = amt,
                    type = selectedType,
                    description = description,
                    paymentMethod = selectedMethod,
                    date = parsedTs
                )
            )
            onDismiss()
        }
    }

    val isDark = isAppInDarkTheme()

    androidx.compose.ui.window.Dialog(
        onDismissRequest = dismissWithAnimation,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        LedgerMateTheme(darkTheme = isDark) {
            val specularBorderBrush = getGlassSpecularBorderBrush(isDark)
            val glassBg = MaterialTheme.colorScheme.surface

            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f)
                ) + fadeIn(animationSpec = tween(300)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)
                ) + fadeOut(animationSpec = tween(250))
            ) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        Surface(
                            color = glassBg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .border(BorderStroke(1.dp, if (isDark) specularBorderBrush else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Text(
                                        text = "Edit Transaction",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                IconButton(
                                    onClick = dismissWithAnimation,
                                    modifier = Modifier.testTag("close_edit_transaction")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        Surface(
                            color = glassBg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .imePadding()
                                .navigationBarsPadding()
                                .border(BorderStroke(1.dp, if (isDark) specularBorderBrush else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))))
                        ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    dismissWithAnimation()
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                            ) {
                                Text("Cancel")
                            }

                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    if ((amountText.replace("[^\\d.]".toRegex(), "").toDoubleOrNull() ?: 0.0) > 0) {
                                        isSubmitted = true
                                    }
                                },
                                enabled = (amountText.replace("[^\\d.]".toRegex(), "").toDoubleOrNull() ?: 0.0) > 0,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(52.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save Changes", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (isSubmitted) {
                        SuccessCheckmarkOverlay(title = "Transaction Updated!")
                    } else {
                        // Type Selector (SALE / DEPOSIT)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val isSale = selectedType == TransactionType.GAVE_CREDIT.name
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedType = TransactionType.GAVE_CREDIT.name },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSale) DebitRed else Color.Transparent,
                                contentColor = if (isSale) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 12.dp)) {
                                    Text("SALE", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            val isDeposit = selectedType == TransactionType.GOT_PAYMENT.name
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedType = TransactionType.GOT_PAYMENT.name },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isDeposit) CreditGreen else Color.Transparent,
                                contentColor = if (isDeposit) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 12.dp)) {
                                    Text("DEPOSIT", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        // Amount and Date Inputs
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            LedgerOutlinedTextField(
                                value = amountText,
                                onValueChange = { amountText = it },
                                label = { Text("Amount ($currencySymbol) *") },
                                leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(
                                    onNext = { focusManager.moveFocus(FocusDirection.Right) }
                                )
                            )

                            FluentDateInputField(
                                value = dateText,
                                onValueChange = { dateText = it },
                                label = "Date (DD/MM/YYYY)",
                                modifier = Modifier.weight(1.15f),
                                imeAction = ImeAction.Next,
                                onImeAction = { focusManager.moveFocus(FocusDirection.Down) }
                            )
                        }

                        // Description Field
                        LedgerOutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Description / Item Notes") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    val amt = amountText.replace("[^\\d.]".toRegex(), "").toDoubleOrNull() ?: 0.0
                                    if (amt > 0) {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        isSubmitted = true
                                    }
                                }
                            )
                        )

                        // Payment Mode
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Payment Mode:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                listOf("CASH", "UPI", "DUE", "BANK_TRANSFER", "CHEQUE").forEach { method ->
                                    FilterChip(
                                        selected = selectedMethod == method,
                                        onClick = { selectedMethod = method },
                                        label = { Text(method, fontWeight = if (selectedMethod == method) FontWeight.Bold else FontWeight.Normal) },
                                        shape = RoundedCornerShape(12.dp)
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

@Composable
fun EditInvoiceDialog(
    invoice: com.example.data.InvoiceEntity,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onConfirm: (updatedInvoice: com.example.data.InvoiceEntity) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isVisible by remember { mutableStateOf(false) }

    val dismissWithAnimation: () -> Unit = {
        isVisible = false
        onDismiss()
    }

    BackHandler { dismissWithAnimation() }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isDark = isAppInDarkTheme()

    var taxText by remember { mutableStateOf(invoice.taxAmount.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }) }
    var discountText by remember { mutableStateOf(invoice.discountAmount.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }) }
    var notes by remember { mutableStateOf(invoice.notes) }

    val initialItems = remember(invoice.itemsJson) {
        invoice.getItems()
    }

    val items = remember { mutableStateListOf<InvoiceItem>().apply { addAll(initialItems) } }
    var itemDesc by remember { mutableStateOf("") }
    var itemPriceText by remember { mutableStateOf("") }
    val subtotal = items.sumOf { it.total }
    var isSubmitted by remember { mutableStateOf(false) }

    if (isSubmitted) {
        LaunchedEffect(Unit) {
            val taxVal = taxText.replace("[^\\d.]".toRegex(), "").toDoubleOrNull() ?: 0.0
            val discVal = discountText.replace("[^\\d.]".toRegex(), "").toDoubleOrNull() ?: 0.0
            val finalTotal = Math.max(0.0, subtotal + taxVal - discVal)

            val jsonArr = JSONArray()
            items.forEach { item ->
                val obj = JSONObject()
                obj.put("description", item.description)
                obj.put("quantity", item.quantity)
                obj.put("unitPrice", item.unitPrice)
                jsonArr.put(obj)
            }

            val updatedStatus = if (invoice.paidAmount >= finalTotal && finalTotal > 0) {
                com.example.data.InvoiceEntity.STATUS_PAID
            } else if (invoice.paidAmount > 0) {
                com.example.data.InvoiceEntity.STATUS_PARTIAL
            } else {
                com.example.data.InvoiceEntity.STATUS_UNPAID
            }

            onConfirm(
                invoice.copy(
                    itemsJson = jsonArr.toString(),
                    subtotal = subtotal,
                    taxAmount = taxVal,
                    discountAmount = discVal,
                    totalAmount = finalTotal,
                    status = updatedStatus,
                    notes = notes
                )
            )
            onDismiss()
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = dismissWithAnimation,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        LedgerMateTheme(darkTheme = isDark) {
            val specularBorderBrush = getGlassSpecularBorderBrush(isDark)
            val glassBg = MaterialTheme.colorScheme.surface

            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f)
                ) + fadeIn(animationSpec = tween(300)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)
                ) + fadeOut(animationSpec = tween(250))
            ) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        Surface(
                            color = glassBg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        if (isDark) specularBorderBrush else androidx.compose.ui.graphics.SolidColor(
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        )
                                    )
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "Edit Receipt #${invoice.invoiceNumber}",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Party: ${invoice.customerName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = dismissWithAnimation,
                                    modifier = Modifier.testTag("close_edit_invoice")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        Surface(
                            color = glassBg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .imePadding()
                                .navigationBarsPadding()
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        if (isDark) specularBorderBrush else androidx.compose.ui.graphics.SolidColor(
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        )
                                    )
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        dismissWithAnimation()
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                ) {
                                    Text("Cancel")
                                }

                                Button(
                                    onClick = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        if (items.isNotEmpty()) {
                                            isSubmitted = true
                                        }
                                    },
                                    enabled = items.isNotEmpty(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .height(52.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Save Changes", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (isSubmitted) {
                            SuccessCheckmarkOverlay(title = "Receipt Changes Saved!")
                        } else {
                            // Quick Add Line Item Card
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Add Item to Receipt",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    LedgerOutlinedTextField(
                                        value = itemDesc,
                                        onValueChange = { itemDesc = it },
                                        label = { Text("Item Name / Description") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                                    )

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        LedgerOutlinedTextField(
                                            value = itemPriceText,
                                            onValueChange = { itemPriceText = it },
                                            label = { Text("Price ($currencySymbol)") },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Decimal,
                                                imeAction = ImeAction.Done
                                            ),
                                            keyboardActions = KeyboardActions(
                                                onDone = {
                                                    val price = itemPriceText.replace("[^\\d.]".toRegex(), "").toDoubleOrNull() ?: 0.0
                                                    if (itemDesc.isNotBlank() && price > 0) {
                                                        items.add(InvoiceItem(itemDesc.trim(), 1.0, price))
                                                        itemDesc = ""
                                                        itemPriceText = ""
                                                    }
                                                    focusManager.clearFocus()
                                                    keyboardController?.hide()
                                                }
                                            )
                                        )

                                        Button(
                                            onClick = {
                                                focusManager.clearFocus()
                                                keyboardController?.hide()
                                                val price = itemPriceText.replace("[^\\d.]".toRegex(), "").toDoubleOrNull() ?: 0.0
                                                if (itemDesc.isNotBlank() && price > 0) {
                                                    items.add(InvoiceItem(itemDesc.trim(), 1.0, price))
                                                    itemDesc = ""
                                                    itemPriceText = ""
                                                }
                                            },
                                            enabled = itemDesc.isNotBlank() && (itemPriceText.replace("[^\\d.]".toRegex(), "").toDoubleOrNull() ?: 0.0) > 0,
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            modifier = Modifier.height(52.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Add Item")
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Add", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            // Items List Header
                            Text(
                                text = "Itemized Breakdown (${items.size} items)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // Items List
                            items.forEachIndexed { index, item ->
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = item.description,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.titleSmall
                                                )
                                                Text(
                                                    text = "Item Total: ${Formatters.formatCurrency(item.total, currencySymbol)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            IconButton(
                                                onClick = { items.removeAt(index) },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Remove",
                                                    tint = DebitRed,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            var priceInput by remember(item.unitPrice) {
                                                mutableStateOf(if (item.unitPrice % 1.0 == 0.0) item.unitPrice.toLong().toString() else item.unitPrice.toString())
                                            }

                                            LedgerOutlinedTextField(
                                                value = priceInput,
                                                onValueChange = { newVal ->
                                                    priceInput = newVal
                                                    val parsedPrice = newVal.toDoubleOrNull() ?: 0.0
                                                    items[index] = item.copy(unitPrice = parsedPrice)
                                                },
                                                label = { Text("Rate ($currencySymbol)") },
                                                modifier = Modifier.weight(1.2f),
                                                shape = RoundedCornerShape(12.dp),
                                                singleLine = true,
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                                )
                                            )

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        if (item.quantity > 1.0) {
                                                            items[index] = item.copy(quantity = item.quantity - 1.0)
                                                        } else {
                                                            items.removeAt(index)
                                                        }
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(18.dp))
                                                }

                                                Text(
                                                    text = "${item.quantity}",
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 10.dp)
                                                )

                                                IconButton(
                                                    onClick = {
                                                        items[index] = item.copy(quantity = item.quantity + 1.0)
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Tax & Discount Row
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                LedgerOutlinedTextField(
                                    value = taxText,
                                    onValueChange = { taxText = it },
                                    label = { Text("Tax ($currencySymbol)") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                                )
                                LedgerOutlinedTextField(
                                    value = discountText,
                                    onValueChange = { discountText = it },
                                    label = { Text("Discount ($currencySymbol)") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                                )
                            }

                            // Notes
                            LedgerOutlinedTextField(
                                value = notes,
                                onValueChange = { notes = it },
                                label = { Text("Notes / Payment Terms") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                maxLines = 3,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                    }
                                )
                            )

                            // Total Summary Card
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(18.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Subtotal: ${Formatters.formatCurrency(subtotal, currencySymbol)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                        Text("Updated Total:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                    val taxVal = taxText.replace("[^\\d.]".toRegex(), "").toDoubleOrNull() ?: 0.0
                                    val discVal = discountText.replace("[^\\d.]".toRegex(), "").toDoubleOrNull() ?: 0.0
                                    val finalTotal = Math.max(0.0, subtotal + taxVal - discVal)
                                    Text(
                                        Formatters.formatCurrency(finalTotal, currencySymbol),
                                        fontWeight = FontWeight.ExtraBold,
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordPaymentDialog(
    invoice: com.example.data.InvoiceEntity,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onConfirm: (paidAmount: Double, paymentMethod: String) -> Unit
) {
    BackHandler { onDismiss() }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { false }
    )
    val remainingBalance = Math.max(0.0, invoice.totalAmount - invoice.paidAmount)
    var payAmountText by remember { mutableStateOf(remainingBalance.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }) }
    var selectedMethod by remember { mutableStateOf("UPI") }
    var isSubmitted by remember { mutableStateOf(false) }

    if (isSubmitted) {
        LaunchedEffect(Unit) {
            val amt = payAmountText.toDoubleOrNull() ?: 0.0
            if (amt > 0) {
                onConfirm(amt, selectedMethod)
            }
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        scrimColor = Color.Black.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isSubmitted) {
                SuccessCheckmarkOverlay(title = "Payment Recorded!")
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(CreditGreenLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Payments,
                            contentDescription = null,
                            tint = CreditGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Record Payment",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Invoice #${invoice.invoiceNumber} • ${invoice.customerName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Outstanding Balance:", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            Formatters.formatCurrency(remainingBalance, currencySymbol),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = DebitRed
                        )
                    }
                }

                LedgerOutlinedTextField(
                    value = payAmountText,
                    onValueChange = { payAmountText = it },
                    label = { Text("Received Amount ($currencySymbol) *") },
                    leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }
                    )
                )

                Column {
                    Text("Payment Mode:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("UPI", "CASH", "BANK_TRANSFER", "CHEQUE").forEach { method ->
                            FilterChip(
                                selected = selectedMethod == method,
                                onClick = { selectedMethod = method },
                                label = { Text(method) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            onDismiss()
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            val amt = payAmountText.toDoubleOrNull() ?: 0.0
                            if (amt > 0) {
                                isSubmitted = true
                            }
                        },
                        enabled = (payAmountText.toDoubleOrNull() ?: 0.0) > 0,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CreditGreen,
                            contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(50.dp)
                    ) {
                        Text("Confirm", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceViewDialog(
    invoice: com.example.data.InvoiceEntity,
    currencySymbol: String,
    businessName: String,
    businessPhone: String,
    businessAddress: String,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { false }
    )

    val itemsList = remember(invoice.itemsJson) {
        invoice.getItems()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        scrimColor = Color.Black.copy(alpha = 0.55f)
    ) {
        val isolatedScroll = rememberIsolatedNestedScrollConnection()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .nestedScroll(isolatedScroll)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Invoice #${invoice.invoiceNumber}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Issued on ${Formatters.formatDate(invoice.issueDate)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val (statusBg, statusFg) = when (invoice.status) {
                    com.example.data.InvoiceEntity.STATUS_PAID -> CreditGreenLight to CreditGreen
                    com.example.data.InvoiceEntity.STATUS_PARTIAL -> AmberWarningLight to AmberWarning
                    else -> DebitRedLight to DebitRed
                }

                Surface(
                    color = statusBg,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = invoice.status,
                        color = statusFg,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            HorizontalDivider()

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Billed To:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(invoice.customerName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    if (invoice.dueDate > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Due Date: ${Formatters.formatDate(invoice.dueDate)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(itemsList) { _, item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(item.description, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${item.quantity} x ${Formatters.formatCurrency(item.unitPrice, currencySymbol)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            Formatters.formatCurrency(item.total, currencySymbol),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Subtotal:")
                        Text(Formatters.formatCurrency(invoice.subtotal, currencySymbol), fontWeight = FontWeight.SemiBold)
                    }
                    if (invoice.taxAmount > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Tax:")
                            Text("+ ${Formatters.formatCurrency(invoice.taxAmount, currencySymbol)}")
                        }
                    }
                    if (invoice.discountAmount > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Discount:")
                            Text("- ${Formatters.formatCurrency(invoice.discountAmount, currencySymbol)}")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total Amount:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            Formatters.formatCurrency(invoice.totalAmount, currencySymbol),
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Close")
            }
        }
    }
}

@Composable
fun EditCustomerDialog(
    customer: CustomerEntity,
    onDismiss: () -> Unit,
    onConfirm: (updated: CustomerEntity) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isVisible by remember { mutableStateOf(false) }

    val dismissWithAnimation: () -> Unit = {
        isVisible = false
        onDismiss()
    }

    BackHandler { dismissWithAnimation() }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isDark = isAppInDarkTheme()

    var name by remember { mutableStateOf(customer.name) }
    var phone by remember { mutableStateOf(customer.phone) }
    var area by remember { mutableStateOf(customer.area) }
    var businessName by remember { mutableStateOf(customer.businessName) }
    var notes by remember { mutableStateOf(customer.notes) }
    var isSubmitted by remember { mutableStateOf(false) }

    if (isSubmitted) {
        LaunchedEffect(Unit) {
            val updated = customer.copy(
                name = name.ifBlank { businessName },
                phone = phone,
                area = area.trim(),
                businessName = businessName,
                notes = notes
            )
            onConfirm(updated)
            onDismiss()
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = dismissWithAnimation,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        LedgerMateTheme(darkTheme = isDark) {
            val specularBorderBrush = getGlassSpecularBorderBrush(isDark)
            val glassBg = MaterialTheme.colorScheme.surface

            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f)
                ) + fadeIn(animationSpec = tween(300)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)
                ) + fadeOut(animationSpec = tween(250))
            ) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        Surface(
                            color = glassBg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .border(BorderStroke(1.dp, if (isDark) specularBorderBrush else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Text(
                                        text = if (customer.partyType == CustomerEntity.PARTY_TYPE_SUPPLIER) "Edit Supplier" else "Edit Customer",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                IconButton(
                                    onClick = dismissWithAnimation,
                                    modifier = Modifier.testTag("close_edit_customer")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        Surface(
                            color = glassBg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .imePadding()
                                .navigationBarsPadding()
                                .border(BorderStroke(1.dp, if (isDark) specularBorderBrush else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))))
                        ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    dismissWithAnimation()
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                            ) {
                                Text("Cancel")
                            }

                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    if (name.isNotBlank() || businessName.isNotBlank()) {
                                        isSubmitted = true
                                    }
                                },
                                enabled = name.isNotBlank() || businessName.isNotBlank(),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(52.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save Changes", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (isSubmitted) {
                        SuccessCheckmarkOverlay(title = "Profile Updated!")
                    } else {
                        LedgerOutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Party / Contact Name *") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )

                        LedgerOutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Phone Number") },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next)
                        )

                        LedgerOutlinedTextField(
                            value = area,
                            onValueChange = { area = it },
                            label = { Text("Area / Village (e.g. Hisampur, Kekri)") },
                            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )

                        LedgerOutlinedTextField(
                            value = businessName,
                            onValueChange = { businessName = it },
                            label = { Text("Shop / Business Name") },
                            leadingIcon = { Icon(Icons.Default.Store, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )

                        LedgerOutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Notes / Address") },
                            leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    if (name.isNotBlank() || businessName.isNotBlank()) {
                                        isSubmitted = true
                                    }
                                }
                            )
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
fun SuccessCheckmarkOverlay(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(CreditGreen),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Success",
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
