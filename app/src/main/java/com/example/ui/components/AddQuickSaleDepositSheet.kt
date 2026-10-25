package com.example.ui.components

import android.app.DatePickerDialog
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CallMade
import androidx.compose.material.icons.outlined.CallReceived
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CustomerWithBalance
import com.example.data.TransactionType
import com.example.ui.AppHaptics
import com.example.ui.Formatters
import com.example.ui.theme.CreditGreen
import com.example.ui.theme.DebitRed
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val AccentGreen = Color(0xFF379358)
private val AccentGreenLight = Color(0xFFEAF6ED)
private val BorderGreenGray = Color(0xFFE3EFE6)
private val TextPrimary = Color(0xFF1C1C1E)
private val TextSecondary = Color(0xFF8E8E93)

private fun launchQuickEntryDatePicker(
    context: Context,
    currentTs: Long,
    onDateChosen: (Long) -> Unit
) {
    val cal = Calendar.getInstance().apply { timeInMillis = currentTs }
    val dpd = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val selectedCal = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, dayOfMonth)
                set(Calendar.HOUR_OF_DAY, 12)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            onDateChosen(selectedCal.timeInMillis)
        },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH)
    )
    dpd.show()
}

private fun evaluateMathExpression(expr: String): Double? {
    val clean = expr.replace("×", "*").replace("÷", "/").replace(" ", "").trim()
    if (clean.isEmpty()) return null
    clean.toDoubleOrNull()?.let { return it }
    return try {
        val addParts = clean.split("+")
        var total = 0.0
        for (part in addParts) {
            if (part.isEmpty()) continue
            val subParts = part.split("-")
            var subVal = 0.0
            for ((idx, sPart) in subParts.withIndex()) {
                if (sPart.isEmpty()) continue
                val mulParts = sPart.split("*")
                var mulVal = 1.0
                for (mPart in mulParts) {
                    val divParts = mPart.split("/")
                    var dVal = divParts.firstOrNull()?.toDoubleOrNull() ?: return null
                    for (dIdx in 1 until divParts.size) {
                        val divisor = divParts[dIdx].toDoubleOrNull() ?: return null
                        if (divisor != 0.0) dVal /= divisor
                    }
                    mulVal *= dVal
                }
                if (idx == 0) subVal = mulVal else subVal -= mulVal
            }
            total += subVal
        }
        if (total.isNaN() || total.isInfinite()) null else total
    } catch (_: Exception) {
        null
    }
}

/**
 * Bottom-sheet style Quick-Entry Modal for adding Sale or Deposit entries.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddQuickSaleDepositSheet(
    initialType: String = TransactionType.GAVE_CREDIT.name, // GAVE_CREDIT = Sale, GOT_PAYMENT = Deposit
    initialCustomerId: String? = null,
    initialDateTimestamp: Long? = null,
    customers: List<CustomerWithBalance>,
    currencySymbol: String = "₹",
    onDismiss: () -> Unit,
    onAddCustomerInline: (String) -> String,
    onConfirm: (customerId: String, amount: Double, type: String, description: String, paymentMethod: String, dateTimestamp: Long) -> Unit
) {
    BackHandler { onDismiss() }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val isSale = initialType == TransactionType.GAVE_CREDIT.name
    val actionTitle = if (isSale) "Sale on credit" else "Payment received"
    val actionIcon = if (isSale) Icons.Outlined.CallMade else Icons.Outlined.CallReceived

    // Customer Selection State
    var selectedCustomerId by remember { mutableStateOf(initialCustomerId ?: "") }
    var customerSearchQuery by remember { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }

    val selectedCustomer = remember(selectedCustomerId, customers) {
        customers.find { it.customer.id == selectedCustomerId }
    }

    val filteredCustomers = remember(customerSearchQuery, customers) {
        if (customerSearchQuery.isBlank()) customers
        else customers.filter {
            it.customer.name.contains(customerSearchQuery, ignoreCase = true) ||
            it.customer.phone.contains(customerSearchQuery) ||
            it.customer.businessName.contains(customerSearchQuery, ignoreCase = true)
        }
    }

    // Amount, Date & Payment Mode State
    var amountText by remember { mutableStateOf("") }
    var selectedDateTs by remember {
        mutableStateOf(
            initialDateTimestamp?.let { Formatters.shiftCalendarDay(it, 0) }
                ?: Formatters.shiftCalendarDay(System.currentTimeMillis(), 0)
        )
    }
    var dateText by remember(selectedDateTs) {
        mutableStateOf(Formatters.formatDate(selectedDateTs))
    }
    var descriptionText by remember { mutableStateOf("") }
    var saleStatusOption by remember { mutableStateOf("DUE") } // 1st option: DUE (default), 2nd option: PAID
    var selectedPaymentMode by remember { mutableStateOf("UPI") } // 1st option: UPI, 2nd option: CASH

    val amountFocusRequester = remember { FocusRequester() }
    val evaluatedAmount = remember(amountText) { evaluateMathExpression(amountText) ?: 0.0 }
    val hasMathOperators = remember(amountText) {
        amountText.any { it == '+' || it == '*' || it == '×' || it == '-' || it == '/' }
    }

    val isSaveEnabled = selectedCustomerId.isNotBlank() && evaluatedAmount > 0.0

    val formattedDateStr = remember(selectedDateTs) {
        val now = System.currentTimeMillis()
        if (Formatters.isSameCalendarDay(selectedDateTs, now)) {
            "Today (${Formatters.formatDate(selectedDateTs)})"
        } else {
            Formatters.formatDateHeader(selectedDateTs)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { true }
        ),
        containerColor = Color.White,
        scrimColor = Color.Black.copy(alpha = 0.35f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Surface(
                modifier = Modifier.padding(vertical = 10.dp),
                color = TextSecondary.copy(alpha = 0.3f),
                shape = CircleShape
            ) {
                Box(modifier = Modifier.size(width = 38.dp, height = 4.dp))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row: Title + Close Button
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
                        color = if (isSale) DebitRed.copy(alpha = 0.12f) else CreditGreen.copy(alpha = 0.12f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = actionIcon,
                                contentDescription = null,
                                tint = if (isSale) DebitRed else CreditGreen,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = actionTitle,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (isSale) "Record goods or services sold to customer" else "Record payment received from customer",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary
                    )
                }
            }

            HorizontalDivider(color = BorderGreenGray, thickness = 1.dp)

            // ============================================================
            // FIELD 1: CUSTOMER SELECTOR / AUTOCOMPLETE
            // ============================================================
            Text(
                text = "CUSTOMER *",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 0.8.sp
            )

            if (selectedCustomer != null) {
                // Selected Customer Chip Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = AccentGreenLight,
                    border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.4f)),
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
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = AccentGreen,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = selectedCustomer.customer.name.take(1).uppercase(),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            Column {
                                Text(
                                    text = selectedCustomer.customer.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (selectedCustomer.customer.phone.isNotBlank()) {
                                    Text(
                                        text = selectedCustomer.customer.phone,
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = {
                                selectedCustomerId = ""
                                customerSearchQuery = ""
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear Customer",
                                tint = TextSecondary
                            )
                        }
                    }
                }
            } else {
                // Search Input Field
                OutlinedTextField(
                    value = customerSearchQuery,
                    onValueChange = {
                        customerSearchQuery = it
                        isSearchFocused = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_quick_autocomplete_input"),
                    placeholder = { Text("Search name or phone number...", fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentGreen,
                        unfocusedBorderColor = BorderGreenGray,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )

                // Dropdown Result List
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .border(1.dp, BorderGreenGray, RoundedCornerShape(12.dp))
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(filteredCustomers, key = { it.customer.id }) { custItem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedCustomerId = custItem.customer.id
                                        isSearchFocused = false
                                        try {
                                            amountFocusRequester.requestFocus()
                                        } catch (_: Exception) {}
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = AccentGreenLight,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = custItem.customer.name.take(1).uppercase(),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AccentGreen
                                            )
                                        }
                                    }

                                    Column {
                                        Text(
                                            text = custItem.customer.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        if (custItem.customer.phone.isNotBlank()) {
                                            Text(
                                                text = custItem.customer.phone,
                                                fontSize = 11.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = Formatters.formatCurrency(Math.abs(custItem.netBalance), currencySymbol),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (custItem.netBalance > 0) CreditGreen else DebitRed
                                )
                            }

                            HorizontalDivider(color = BorderGreenGray.copy(alpha = 0.6f), thickness = 0.5.dp)
                        }

                        // Inline Add New Customer Option
                        if (customerSearchQuery.trim().isNotBlank()) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val typedName = customerSearchQuery.trim()
                                            val newId = onAddCustomerInline(typedName)
                                            if (newId.isNotBlank()) {
                                                selectedCustomerId = newId
                                            }
                                            isSearchFocused = false
                                        }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddCircleOutline,
                                        contentDescription = null,
                                        tint = AccentGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "+ Add \"${customerSearchQuery.trim()}\" as new customer",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentGreen
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ============================================================
            // FIELD 2: AMOUNT INPUT (Full width — single date chips row below per #33)
            // ============================================================
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "AMOUNT ($currencySymbol) *",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input ->
                        amountText = input.filter { char ->
                            char.isDigit() || char == '.' || char == '+' || char == '*' || char == '×' || char == '-'
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .focusRequester(amountFocusRequester)
                        .testTag("quick_entry_amount_input"),
                    placeholder = { Text("0 or 120+50", fontSize = 15.sp) },
                    prefix = { Text("$currencySymbol ", fontWeight = FontWeight.Bold, color = AccentGreen) },
                    suffix = if (hasMathOperators && evaluatedAmount > 0.0) {
                        {
                            Text(
                                text = "= ${Formatters.formatCurrency(evaluatedAmount, currencySymbol)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentGreen
                            )
                        }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentGreen,
                        unfocusedBorderColor = BorderGreenGray,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )
            }

            // Quick Amount & Inline Calculator Chips (+100, +500, +1000, +, ×)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("+100" to 100, "+500" to 500, "+1000" to 1000).forEach { (label, addVal) ->
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val current = evaluateMathExpression(amountText) ?: 0.0
                                val next = current + addVal
                                amountText = if (next % 1.0 == 0.0) next.toLong().toString() else String.format(Locale.US, "%.2f", next)
                            },
                        shape = RoundedCornerShape(8.dp),
                        color = AccentGreenLight.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.25f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "$currencySymbol$label",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentGreen
                            )
                        }
                    }
                }
                listOf("+" to "+", "×" to "×").forEach { (label, op) ->
                    Surface(
                        modifier = Modifier
                            .width(40.dp)
                            .height(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                if (amountText.isNotEmpty() && amountText.last().isDigit()) {
                                    amountText += op
                                }
                            },
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF4F6F4),
                        border = BorderStroke(1.dp, BorderGreenGray)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            // Quick Date Preset Chips (Today / Yesterday / Pick from Calendar)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isTodaySelected = Formatters.isSameCalendarDay(selectedDateTs, System.currentTimeMillis())
                val isYesterdaySelected = Formatters.isSameCalendarDay(selectedDateTs, System.currentTimeMillis() - 86400000L)

                // "Today" Chip
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            val todayTs = Formatters.shiftCalendarDay(System.currentTimeMillis(), 0)
                            selectedDateTs = todayTs
                            dateText = Formatters.formatDate(todayTs)
                        }
                        .testTag("date_chip_today"),
                    shape = RoundedCornerShape(8.dp),
                    color = if (isTodaySelected) AccentGreenLight else Color(0xFFF4F6F4),
                    border = if (isTodaySelected) BorderStroke(1.dp, AccentGreen) else null
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Today",
                            fontSize = 12.sp,
                            fontWeight = if (isTodaySelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isTodaySelected) AccentGreen else TextSecondary
                        )
                    }
                }

                // "Yesterday" Chip
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            val yesterdayTs = Formatters.shiftCalendarDay(System.currentTimeMillis(), -1)
                            selectedDateTs = yesterdayTs
                            dateText = Formatters.formatDate(yesterdayTs)
                        }
                        .testTag("date_chip_yesterday"),
                    shape = RoundedCornerShape(8.dp),
                    color = if (isYesterdaySelected) AccentGreenLight else Color(0xFFF4F6F4),
                    border = if (isYesterdaySelected) BorderStroke(1.dp, AccentGreen) else null
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Yesterday",
                            fontSize = 12.sp,
                            fontWeight = if (isYesterdaySelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isYesterdaySelected) AccentGreen else TextSecondary
                        )
                    }
                }

                // "Calendar" Chip
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            launchQuickEntryDatePicker(context, selectedDateTs) { ts ->
                                selectedDateTs = ts
                                dateText = Formatters.formatDate(ts)
                            }
                        }
                        .testTag("date_chip_calendar"),
                    shape = RoundedCornerShape(8.dp),
                    color = if (!isTodaySelected && !isYesterdaySelected) AccentGreenLight else Color(0xFFF4F6F4),
                    border = if (!isTodaySelected && !isYesterdaySelected) BorderStroke(1.dp, AccentGreen) else null
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = if (!isTodaySelected && !isYesterdaySelected) AccentGreen else TextSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (!isTodaySelected && !isYesterdaySelected) dateText else "Calendar",
                            fontSize = 12.sp,
                            fontWeight = if (!isTodaySelected && !isYesterdaySelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (!isTodaySelected && !isYesterdaySelected) AccentGreen else TextSecondary
                        )
                    }
                }
            }

            // ============================================================
            // FIELD 4: PAYMENT STATUS & PAYMENT MODE (2-PILL SYSTEM FOR SALE)
            // ============================================================
            if (isSale) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "SALE PAYMENT STATUS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 0.8.sp
                    )

                    // 2-Pill System: Due (1st Option) | Paid (2nd Option)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BorderGreenGray.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val options = listOf("DUE" to "Due (Credit)", "PAID" to "Paid (Immediate)")
                        options.forEach { (key, label) ->
                            val isSelected = saleStatusOption == key
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clickable { saleStatusOption = key },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) (if (key == "DUE") DebitRed else AccentGreen) else Color.Transparent,
                                contentColor = if (isSelected) Color.White else TextSecondary
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // Payment Method appears from behind the 2-pill system when PAID is selected
                    AnimatedVisibility(
                        visible = saleStatusOption == "PAID",
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "PAYMENT METHOD",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                letterSpacing = 0.8.sp
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BorderGreenGray.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val methods = listOf("UPI", "CASH")
                                methods.forEach { method ->
                                    val isSelected = selectedPaymentMode == method
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp)
                                            .clickable { selectedPaymentMode = method },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) AccentGreen else Color.Transparent,
                                        contentColor = if (isSelected) Color.White else TextSecondary
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
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
                // Deposit Payment Mode Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "PAYMENT MODE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 0.8.sp
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BorderGreenGray.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val modes = listOf("UPI", "CASH", "Bank Transfer", "Cheque")
                        modes.forEach { mode ->
                            val isSelected = selectedPaymentMode == mode
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clickable { selectedPaymentMode = mode },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) AccentGreen else Color.Transparent,
                                contentColor = if (isSelected) Color.White else TextSecondary
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
                }
            }

            // ============================================================
            // FIELD 5: NOTE / DESCRIPTION (OPTIONAL)
            // ============================================================
            OutlinedTextField(
                value = descriptionText,
                onValueChange = { descriptionText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Note / Description (Optional)", fontSize = 14.sp) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentGreen,
                    unfocusedBorderColor = BorderGreenGray,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )

            // ============================================================
            // BOTTOM ACTION BUTTONS + INLINE DISABLED REASON (#37)
            // ============================================================
            if (!isSaveEnabled) {
                Text(
                    text = when {
                        selectedCustomerId.isBlank() && evaluatedAmount <= 0.0 -> "Select a customer and enter an amount greater than 0"
                        selectedCustomerId.isBlank() -> "Select or add a customer above"
                        else -> "Amount must be greater than 0"
                    },
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = DebitRed,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(0.8f)
                        .height(48.dp)
                ) {
                    Text(
                        text = "Cancel",
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        fontSize = 15.sp
                    )
                }

                Button(
                    onClick = {
                        val amt = evaluatedAmount
                        if (selectedCustomerId.isNotBlank() && amt > 0.0) {
                            AppHaptics.performConfirm(context as? android.view.View)
                            val effectivePaymentMethod = if (isSale) {
                                if (saleStatusOption == "PAID") selectedPaymentMode else "DUE"
                            } else {
                                selectedPaymentMode
                            }
                            onConfirm(
                                selectedCustomerId,
                                amt,
                                initialType,
                                descriptionText,
                                effectivePaymentMethod,
                                selectedDateTs
                            )
                            onDismiss()
                        }
                    },
                    enabled = isSaveEnabled,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentGreen,
                        contentColor = Color.White,
                        disabledContainerColor = AccentGreen.copy(alpha = 0.4f),
                        disabledContentColor = Color.White.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier
                        .weight(1.2f)
                        .height(48.dp)
                        .testTag("save_quick_entry_button")
                ) {
                    Text(
                        text = "Save $actionTitle",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
