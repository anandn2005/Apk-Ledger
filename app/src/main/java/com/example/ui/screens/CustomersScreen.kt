package com.example.ui.screens

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CustomerEntity
import com.example.data.CustomerWithBalance
import com.example.data.TransactionEntity
import com.example.data.TransactionType
import com.example.ui.Formatters
import com.example.ui.LedgerUiState
import com.example.ui.components.AnimatedPillSearchBar
import com.example.ui.components.CloudSyncStatusBadge
import com.example.ui.components.IosScrollbarPill
import com.example.ui.components.PremiumEmptyState
import com.example.ui.components.ScrollEdgeShadow
import com.example.ui.components.pressScale
import com.example.ui.components.rememberCanScrollBack
import com.example.ui.i18n.AppStrings
import com.example.ui.i18n.LocalAppLanguage
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Brand Tokens
private val AccentGreen = Color(0xFF379358)
private val AccentGreenLight = Color(0xFFEAF6ED)
private val BorderGreenGray = Color(0xFFE3EFE6)
private val TextPrimary = Color(0xFF1B2E22)
private val TextSecondary = Color(0xFF8E8E93)

private fun isSameCalendarDay(ts1: Long, ts2: Long): Boolean = Formatters.isSameCalendarDay(ts1, ts2)

private fun formatDateHeaderLabel(timestamp: Long): String {
    val now = System.currentTimeMillis()
    if (Formatters.isSameCalendarDay(timestamp, now)) return "Today"

    val yesterday = Formatters.shiftCalendarDay(now, -1)
    if (Formatters.isSameCalendarDay(timestamp, yesterday)) return "Yesterday"

    return Formatters.formatDateHeader(timestamp)
}

private fun formatTimeLabel(timestamp: Long): String = Formatters.formatTime(timestamp)

private fun shiftCalendarDay(timestamp: Long, days: Int): Long = Formatters.shiftCalendarDay(timestamp, days)

private fun launchNativeDatePicker(
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CustomersScreen(
    state: LedgerUiState,
    onSearchQueryChange: (String) -> Unit,
    onFilterChange: (String) -> Unit,
    onSelectCustomer: (String) -> Unit,
    onAddCustomerClick: () -> Unit,
    onAddEntryForDateClick: ((Long) -> Unit)? = null,
    onActiveDateChange: ((Long?) -> Unit)? = null,
    onDeleteSelectedCustomers: (Set<String>) -> Unit = {},
    onTogglePinCustomer: (String) -> Unit = {},
    onTriggerCloudSync: () -> Unit = {},
    onDailyClosingClick: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenNotes: () -> Unit = {},
    onQuickEntryForCustomer: ((customerId: String, txnType: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode = selectedIds.isNotEmpty()
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val currentLang = state.selectedLanguage
    // Persistent View Mode State: "ALL" vs "DATE_WISE"
    var viewMode by rememberSaveable { mutableStateOf("ALL") }
    var selectedDateTimestamp by rememberSaveable { mutableStateOf(Formatters.shiftCalendarDay(System.currentTimeMillis(), 0)) }
    var selectedSortOptionName by rememberSaveable { mutableStateOf(com.example.feature.customers.CustomerSortOption.RECENT.name) }
    val selectedSortOption = remember(selectedSortOptionName) {
        runCatching { com.example.feature.customers.CustomerSortOption.valueOf(selectedSortOptionName) }
            .getOrDefault(com.example.feature.customers.CustomerSortOption.RECENT)
    }
    var selectedAreaFilter by rememberSaveable { mutableStateOf("ALL") }
    val availableAreas = remember(state.customers) {
        state.customers.mapNotNull { it.customer.area.trim().takeIf { a -> a.isNotBlank() } }.distinct().sorted()
    }
    val displayedCustomers = remember(state.customers, selectedSortOption, selectedAreaFilter, state.pinnedCustomerIds) {
        val areaFiltered = if (selectedAreaFilter == "ALL") {
            state.customers
        } else {
            state.customers.filter { it.customer.area.equals(selectedAreaFilter, ignoreCase = true) }
        }
        val sorted = when (selectedSortOption) {
            com.example.feature.customers.CustomerSortOption.RECENT ->
                areaFiltered.sortedByDescending { it.lastTransactionDate ?: it.customer.createdAt }
            com.example.feature.customers.CustomerSortOption.HIGHEST_DUE ->
                areaFiltered.sortedByDescending { it.netBalance }
            com.example.feature.customers.CustomerSortOption.OLDEST_DUE ->
                areaFiltered.sortedWith(
                    compareByDescending<CustomerWithBalance> { it.netBalance > 0 }
                        .thenBy { it.oldestDueDate ?: it.lastTransactionDate ?: it.customer.createdAt }
                )
            com.example.feature.customers.CustomerSortOption.NAME ->
                areaFiltered.sortedBy { it.customer.name.lowercase() }
            com.example.feature.customers.CustomerSortOption.AREA ->
                areaFiltered.sortedWith(
                    compareBy<CustomerWithBalance> { it.customer.area.ifBlank { "zzz" }.lowercase() }
                        .thenBy { it.customer.name.lowercase() }
                )
        }
        val (pinned, unpinned) = sorted.partition { state.pinnedCustomerIds.contains(it.customer.id) }
        pinned + unpinned
    }
    val customerMap = remember(state.customers) { state.customers.associate { it.customer.id to it.customer.name } }

    LaunchedEffect(viewMode, selectedDateTimestamp) {
        if (viewMode == "DATE_WISE") {
            onActiveDateChange?.invoke(selectedDateTimestamp)
        } else {
            onActiveDateChange?.invoke(null)
        }
    }

    val listState = rememberLazyListState()
    val canScrollBack = rememberCanScrollBack(listState)
    val dateListState = rememberLazyListState()
    val canScrollBackDate = rememberCanScrollBack(dateListState)

    var localCustomerQuery by remember { mutableStateOf(state.customerSearchQuery) }
    LaunchedEffect(state.customerSearchQuery) {
        if (state.customerSearchQuery != localCustomerQuery) {
            localCustomerQuery = state.customerSearchQuery
        }
    }

    // Keep header height stable during scroll to prevent mid-fling Scaffold/LazyColumn re-measurement
    val isHeaderVisible = true
    val pullToRevealConnection = remember {
        object : NestedScrollConnection {}
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
                            if (selectedIds.size == state.customers.size) {
                                selectedIds = emptySet()
                            } else {
                                selectedIds = state.customers.map { it.customer.id }.toSet()
                            }
                        }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }

                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showDeleteConfirmDialog = true
                        }) {
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
                    // Collapsing Title Row & Segmented Control
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
                                    text = if (viewMode == "DATE_WISE") "Date-wise Entries" else "Customers",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = (-0.5).sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Daily Closing Labeled Pill Button (EoD Settlement) (#6)
                                    Surface(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onDailyClosingClick()
                                        },
                                        shape = RoundedCornerShape(50),
                                        color = if (isAppInDarkTheme()) Color(0xFF2C2C2E) else Color(0xFFF2F2F7),
                                        border = BorderStroke(1.dp, if (isAppInDarkTheme()) Color(0xFF3A3A3C) else Color(0xFFE5E5EA)),
                                        modifier = Modifier
                                            .height(34.dp)
                                            .testTag("daily_closing_button")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Assessment,
                                                contentDescription = "Daily Closing Summary",
                                                tint = AccentGreen,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Closing",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isAppInDarkTheme()) Color.White else TextPrimary
                                            )
                                        }
                                    }

                                    NotesHeaderIconButton(
                                        hasDueReminder = state.hasDueOrOverdueNoteReminder,
                                        onClick = onOpenNotes
                                    )

                                    CloudSyncStatusBadge(
                                        syncState = state.syncState,
                                        authUser = state.authUser,
                                        onTriggerSync = onTriggerCloudSync,
                                        onOpenSettings = onOpenSettings
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // ============================================================
                            // 1) LIQUID SPRING SEGMENTED CONTROL: All Customers vs Date-wise
                            // ============================================================
                            val isDark = isAppInDarkTheme()
                            val trackBg = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
                            val activePillBg = if (isDark) Color(0xFF2C2C2E) else Color.White
                            val trackBorder = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
                            val isAllMode = viewMode == "ALL"

                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(trackBg)
                                    .border(BorderStroke(1.dp, trackBorder), RoundedCornerShape(12.dp))
                                    .padding(3.dp)
                            ) {
                                val segmentWidth = (maxWidth - 4.dp) / 2f
                                val indicatorOffset by animateDpAsState(
                                    targetValue = if (isAllMode) 0.dp else (segmentWidth + 4.dp),
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    ),
                                    label = "SegmentedPillSlide"
                                )

                                // Liquid Sliding Pill Background
                                Surface(
                                    modifier = Modifier
                                        .offset(x = indicatorOffset)
                                        .width(segmentWidth)
                                        .fillMaxHeight(),
                                    shape = RoundedCornerShape(9.dp),
                                    color = activePillBg,
                                    shadowElevation = 2.dp,
                                    border = if (!isDark) BorderStroke(0.5.dp, Color(0x14000000)) else null
                                ) {}

                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val allTextColor by animateColorAsState(
                                        targetValue = if (isAllMode) AccentGreen else TextSecondary,
                                        animationSpec = tween(180),
                                        label = "AllTabTextColor"
                                    )
                                    val dateTextColor by animateColorAsState(
                                        targetValue = if (!isAllMode) AccentGreen else TextSecondary,
                                        animationSpec = tween(180),
                                        label = "DateTabTextColor"
                                    )

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(9.dp))
                                            .pressScale(targetScale = 0.96f)
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                viewMode = "ALL"
                                            }
                                            .testTag("toggle_all_customers_tab"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = AppStrings.allCustomers(currentLang),
                                            fontWeight = if (isAllMode) FontWeight.SemiBold else FontWeight.Medium,
                                            fontSize = 13.sp,
                                            color = allTextColor
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(9.dp))
                                            .pressScale(targetScale = 0.96f)
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                viewMode = "DATE_WISE"
                                            }
                                            .testTag("toggle_date_wise_tab"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = AppStrings.dateWise(currentLang),
                                            fontWeight = if (!isAllMode) FontWeight.SemiBold else FontWeight.Medium,
                                            fontSize = 13.sp,
                                            color = dateTextColor
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }

                    // 2) Search Bar: Permanently Visible at Top in All Customers & Date-wise Modes
                    AnimatedPillSearchBar(
                        query = localCustomerQuery,
                        onQueryChange = { newQuery ->
                            localCustomerQuery = newQuery
                            onSearchQueryChange(newQuery)
                        },
                        placeholderSequence = if (viewMode == "DATE_WISE") {
                            listOf(
                                "Search entries for this date...",
                                "Search customer name...",
                                "Search notes..."
                            )
                        } else {
                            listOf(
                                AppStrings.search(currentLang),
                                "Search by customer name...",
                                "Search by phone number..."
                            )
                        },
                        testTag = "customer_search_input"
                    )

                    if (viewMode == "ALL") {
                        Spacer(modifier = Modifier.height(8.dp))
                        val isDarkFilter = isAppInDarkTheme()
                        val activeFilter = if (state.customerFilter == "CUSTOMER") "ALL" else state.customerFilter
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val filters = listOf(
                                Triple("ALL", "${AppStrings.all(currentLang)} (${state.customers.size})", state.customers.size),
                                Triple("OWED", "${AppStrings.youWillGet(currentLang)} • ${Formatters.formatCurrency(state.summary.totalReceivable, state.currencySymbol)}", -1),
                                Triple("ADVANCE", "${AppStrings.youWillGive(currentLang)} • ${Formatters.formatCurrency(state.summary.totalPayable, state.currencySymbol)}", -1)
                            )
                            filters.forEach { (key, label, _) ->
                                val selected = activeFilter == key
                                // Consistent money color rule (#2): Green = Receivable coming to you, Red = Payable you owe
                                val pillAccent = when (key) {
                                    "OWED" -> CreditGreen
                                    "ADVANCE" -> DebitRed
                                    else -> AccentGreen
                                }
                                Surface(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onFilterChange(if (selected && key != "ALL") "CUSTOMER" else if (key == "ALL") "CUSTOMER" else key)
                                    },
                                    shape = RoundedCornerShape(50),
                                    color = if (selected) {
                                        pillAccent.copy(alpha = if (isDarkFilter) 0.22f else 0.12f)
                                    } else {
                                        if (isDarkFilter) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (selected) pillAccent.copy(alpha = 0.55f)
                                        else (if (isDarkFilter) Color(0xFF2C2C2E) else Color(0xFFE5E5EA))
                                    ),
                                    modifier = Modifier
                                        .pressScale(targetScale = 0.94f)
                                        .testTag("customer_filter_$key")
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) pillAccent else TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        // Sort & Area/Village collection chips (#5, #25, #26)
                        Spacer(modifier = Modifier.height(6.dp))
                        androidx.compose.foundation.lazy.LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val sortOptions = listOf(
                                com.example.feature.customers.CustomerSortOption.RECENT to "Recent",
                                com.example.feature.customers.CustomerSortOption.HIGHEST_DUE to "Highest Due",
                                com.example.feature.customers.CustomerSortOption.OLDEST_DUE to "Oldest Due",
                                com.example.feature.customers.CustomerSortOption.AREA to "By Area"
                            )
                            items(
                                items = sortOptions,
                                key = { (opt, _) -> "sort_${opt.name}" },
                                contentType = { "sort_chip" }
                            ) { (opt, label) ->
                                val isSortSelected = selectedSortOption == opt
                                Surface(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedSortOptionName = opt.name
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSortSelected) AccentGreen.copy(alpha = 0.14f)
                                    else (if (isDarkFilter) Color(0xFF1C1C1E) else Color(0xFFF5F5F7)),
                                    border = BorderStroke(
                                        0.8.dp,
                                        if (isSortSelected) AccentGreen.copy(alpha = 0.5f)
                                        else (if (isDarkFilter) Color(0xFF2C2C2E) else Color(0xFFE5E5EA))
                                    ),
                                    modifier = Modifier.testTag("customer_sort_${opt.name}")
                                ) {
                                    Text(
                                        text = "Sort: $label",
                                        fontSize = 10.5.sp,
                                        fontWeight = if (isSortSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSortSelected) AccentGreen else TextSecondary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            if (availableAreas.isNotEmpty()) {
                                items(
                                    items = availableAreas,
                                    key = { areaName -> "area_$areaName" },
                                    contentType = { "area_chip" }
                                ) { areaName ->
                                    val isAreaSelected = selectedAreaFilter.equals(areaName, ignoreCase = true)
                                    Surface(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            selectedAreaFilter = if (isAreaSelected) "ALL" else areaName
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isAreaSelected) AccentGreen.copy(alpha = 0.18f)
                                        else (if (isDarkFilter) Color(0xFF1C1C1E) else Color(0xFFF5F5F7)),
                                        border = BorderStroke(
                                            0.8.dp,
                                            if (isAreaSelected) AccentGreen else (if (isDarkFilter) Color(0xFF2C2C2E) else Color(0xFFE5E5EA))
                                        )
                                    ) {
                                        Text(
                                            text = "📍 $areaName",
                                            fontSize = 10.5.sp,
                                            fontWeight = if (isAreaSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isAreaSelected) AccentGreen else TextSecondary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
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
                            onDeleteSelectedCustomers(selectedIds)
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

        if (viewMode == "DATE_WISE") {
            // ============================================================
            // 2) DATE-WISE VIEW MODE CONTENT
            // ============================================================
            val dateLabel = formatDateHeaderLabel(selectedDateTimestamp)
            val isFuture = selectedDateTimestamp > (System.currentTimeMillis() + 86400000L)

            val dateTransactions = remember(selectedDateTimestamp, state.allTransactions, localCustomerQuery, customerMap) {
                state.allTransactions
                    .filter { isSameCalendarDay(it.date, selectedDateTimestamp) }
                    .filter { txn ->
                        if (localCustomerQuery.isBlank()) true
                        else {
                            val cName = customerMap[txn.customerId] ?: ""
                            cName.contains(localCustomerQuery, ignoreCase = true) ||
                            txn.description.contains(localCustomerQuery, ignoreCase = true) ||
                            txn.invoiceRef.contains(localCustomerQuery, ignoreCase = true)
                        }
                    }
                    .sortedByDescending { it.date }
            }

            val dailyTotalSum = remember(dateTransactions) {
                dateTransactions.sumOf { txn ->
                    if (txn.type == TransactionType.GAVE_CREDIT.name || txn.type == TransactionType.INVOICE_BILL.name) {
                        txn.amount
                    } else {
                        -txn.amount
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Header Date Selector Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, BorderGreenGray),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous Day Chevron
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedDateTimestamp = shiftCalendarDay(selectedDateTimestamp, -1)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = "Previous Day",
                                tint = TextSecondary
                            )
                        }

                        // Center Date Label + Calendar Icon (Tapping launches Android Native DatePickerDialog)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    launchNativeDatePicker(context, selectedDateTimestamp) { chosenTs ->
                                        selectedDateTimestamp = chosenTs
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = "Pick Date",
                                tint = AccentGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = dateLabel,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        // Next Day Chevron
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedDateTimestamp = shiftCalendarDay(selectedDateTimestamp, +1)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Next Day",
                                tint = TextSecondary
                            )
                        }
                    }
                }

                // Daily Summary Chip Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${dateTransactions.size} entries",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isFuture) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "Future date",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        // Daily Running Total Chip
                        Surface(
                            shape = CircleShape,
                            color = AccentGreenLight,
                            border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "Total: ${Formatters.formatCurrency(Math.abs(dailyTotalSum), state.currencySymbol)} ${if (dailyTotalSum >= 0) "(Net Due)" else "(Recd)"}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentGreen,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Date-wise Entry List or Empty State
                if (dateTransactions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = AccentGreenLight,
                                modifier = Modifier.size(68.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.EventNote,
                                        contentDescription = null,
                                        tint = AccentGreen,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }

                            Text(
                                text = "No entries for $dateLabel",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )

                            Text(
                                text = "Tap '+ Add Entry' below to log a sale or payment against $dateLabel.",
                                fontSize = 14.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onAddEntryForDateClick?.invoke(selectedDateTimestamp) ?: onAddCustomerClick()
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentGreen,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Entry for $dateLabel", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            state = dateListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(pullToRevealConnection)
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 150.dp)
                        ) {
                        items(
                            items = dateTransactions,
                            key = { it.id },
                            contentType = { txn ->
                                if (txn.type == TransactionType.GAVE_CREDIT.name || txn.type == TransactionType.INVOICE_BILL.name) {
                                    "date_txn_credit"
                                } else {
                                    "date_txn_payment"
                                }
                            }
                        ) { txn ->
                            val custName = customerMap[txn.customerId] ?: "Customer"
                            val isCredit = txn.type == TransactionType.GAVE_CREDIT.name || txn.type == TransactionType.INVOICE_BILL.name
                            val isDark = isAppInDarkTheme()
                            val formattedTime = remember(txn.date) { formatTimeLabel(txn.date) }
                            val formattedAmount = remember(txn.amount, state.currencySymbol) {
                                Formatters.formatCurrency(txn.amount, state.currencySymbol)
                            }
                            val subtitleMeta = remember(txn.description, txn.invoiceRef) {
                                listOfNotNull(
                                    txn.description.takeIf { it.isNotBlank() },
                                    txn.invoiceRef.takeIf { it.isNotBlank() }?.let { "#$it" }
                                ).joinToString(" • ")
                            }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isDark) Color(0xFF242426) else Color.White,
                                border = BorderStroke(1.dp, if (isDark) Color(0xFF333336) else BorderGreenGray),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 74.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onSelectCustomer(txn.customerId) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(if (isCredit) DebitRed.copy(alpha = 0.12f) else CreditGreen.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isCredit) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                                contentDescription = null,
                                                tint = if (isCredit) DebitRed else CreditGreen,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = custName,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isDark) Color.White else TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = formattedTime,
                                                    fontSize = 12.sp,
                                                    color = TextSecondary
                                                )

                                                if (txn.paymentMethod.isNotBlank()) {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                    ) {
                                                        Text(
                                                            text = txn.paymentMethod,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = TextSecondary,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            if (subtitleMeta.isNotBlank()) {
                                                Text(
                                                    text = subtitleMeta,
                                                    fontSize = 11.sp,
                                                    color = TextSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                        modifier = Modifier.wrapContentWidth(Alignment.End)
                                    ) {
                                        Text(
                                            text = formattedAmount,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.End,
                                            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                                            color = if (isCredit) DebitRed else CreditGreen
                                        )

                                        Text(
                                            text = if (isCredit) AppStrings.saleOnCredit(currentLang) else AppStrings.paymentReceived(currentLang),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            textAlign = TextAlign.End,
                                            color = if (isCredit) DebitRed else CreditGreen
                                        )
                                    }
                                }
                            }
                        }
                    }

                    ScrollEdgeShadow(
                        isScrolled = canScrollBackDate,
                        isTop = true,
                        shadowHeight = 16.dp,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )

                    ScrollEdgeShadow(
                        isScrolled = true,
                        isTop = false,
                        shadowHeight = 24.dp,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )

                    IosScrollbarPill(
                        listState = dateListState,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }
        }
        } else {
            // ============================================================
            // STANDARD ALL CUSTOMERS LIST VIEW
            // ============================================================
            if (displayedCustomers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    PremiumEmptyState(
                        title = "No collections logged for this period.",
                        subtitle = if (state.customerSearchQuery.isNotBlank() || selectedAreaFilter != "ALL")
                            "No matching customers found."
                        else
                            "Tap '+ Customer' below to add your first customer and start logging collections."
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
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(top = 6.dp, bottom = 150.dp)
                    ) {
                        items(
                            items = displayedCustomers,
                            key = { it.customer.id },
                            contentType = { item ->
                                when {
                                    item.netBalance > 0.0 -> "customer_owed"
                                    item.netBalance < 0.0 -> "customer_advance"
                                    else -> "customer_settled"
                                }
                            }
                        ) { item ->
                            val isSelected = selectedIds.contains(item.customer.id)
                            val isPinned = state.pinnedCustomerIds.contains(item.customer.id)
                            
                            SwipeRevealRow(
                                onDelete = {
                                    onDeleteSelectedCustomers(setOf(item.customer.id))
                                },
                                onEdit = {
                                    onSelectCustomer(item.customer.id)
                                },
                                onRemind = if (item.netBalance > 0.0) {
                                    {
                                        val amountFormatted = Formatters.formatCurrency(Math.abs(item.netBalance), state.currencySymbol)
                                        val bizName = state.businessName.ifBlank { "Our Store" }
                                        val upiPart = if (state.upiId.isNotBlank()) "\nUPI ID: ${state.upiId}" else ""
                                        val msg = "Hello ${item.customer.name}, a friendly reminder from *$bizName* that your pending balance is *$amountFormatted*.$upiPart\nThank you!"
                                        val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(android.content.Intent.EXTRA_TEXT, msg)
                                        }
                                        context.startActivity(android.content.Intent.createChooser(sendIntent, "Send Payment Reminder"))
                                    }
                                } else null,
                                onQuickSale = if (onQuickEntryForCustomer != null) {
                                    { onQuickEntryForCustomer(item.customer.id, TransactionType.GAVE_CREDIT.name) }
                                } else null,
                                onQuickDeposit = if (onQuickEntryForCustomer != null) {
                                    { onQuickEntryForCustomer(item.customer.id, TransactionType.GOT_PAYMENT.name) }
                                } else null
                            ) {
                                CustomerCardItem(
                                    item = item,
                                    currencySymbol = state.currencySymbol,
                                    isSelected = isSelected,
                                    isPinned = isPinned,
                                    onClick = {
                                        if (isSelectionMode) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            selectedIds = if (isSelected) selectedIds - item.customer.id else selectedIds + item.customer.id
                                        } else {
                                            onSelectCustomer(item.customer.id)
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        selectedIds = if (isSelected) selectedIds - item.customer.id else selectedIds + item.customer.id
                                    },
                                    onTogglePin = { onTogglePinCustomer(item.customer.id) }
                                )
                            }
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

                    IosScrollbarPill(
                        listState = listState,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CustomerCardItem(
    item: CustomerWithBalance,
    currencySymbol: String,
    isSelected: Boolean,
    isPinned: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTogglePin: () -> Unit
) {
    val cust = item.customer
    val net = item.netBalance
    val isOwed = net > 0
    val isDark = isAppInDarkTheme()
    val currentLang = LocalAppLanguage.current

    val (avatarBg, avatarFg) = remember(cust.name, isDark) {
        com.example.feature.customers.CustomersViewModel.avatarColorsForName(cust.name, isDark)
    }
    val avatarInitial = remember(cust.name) {
        cust.name.trim().take(1).uppercase().ifBlank { "C" }
    }
    val ageDays = remember(item.oldestDueDate, item.lastTransactionDate, item.netBalance) {
        com.example.feature.customers.CustomersViewModel.computeAgeingDays(item)
    }
    val isOverdue = isOwed && ageDays >= 30
    val metaParts = remember(cust.businessName, cust.phone, item.lastTransactionDate, isOverdue) {
        val lastActivityText = Formatters.formatRelativeDateOrAge(item.lastTransactionDate ?: 0L)
        buildList {
            cust.businessName.takeIf { it.isNotBlank() }?.let { add(it) }
            cust.phone.takeIf { it.isNotBlank() }?.let { add(it) }
            if (!isOverdue && lastActivityText.isNotBlank()) add(lastActivityText)
        }.joinToString(" • ")
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) {
            if (isDark) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else AccentGreenLight
        } else {
            if (isDark) Color(0xFF242426) else Color.White
        },
        border = BorderStroke(
            1.dp,
            if (isSelected) AccentGreen else (if (isDark) Color(0xFF333336) else BorderGreenGray)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 74.dp)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left block: Avatar (44dp) + Name (truncated) + Subtitle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                // Deterministic multi-color 44dp Avatar Circle (#5)
                Surface(
                    shape = CircleShape,
                    color = avatarBg,
                    border = BorderStroke(1.dp, avatarFg.copy(alpha = 0.25f)),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = avatarInitial,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = avatarFg
                        )
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = cust.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDark) Color.White else TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (cust.area.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF0FDF4),
                                border = BorderStroke(0.5.dp, AccentGreen.copy(alpha = 0.35f))
                            ) {
                                Text(
                                    text = cust.area,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AccentGreen,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }

                        if (isPinned) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = "Pinned",
                                tint = AccentGreen,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isOverdue) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = DebitRed.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "Overdue ${ageDays}d",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DebitRed,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        if (metaParts.isNotBlank()) {
                            Text(
                                text = metaParts,
                                fontSize = 12.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Right block: Right-aligned Amount & Status with tabular numbers
            // Consistent Money Color (#2): Positive Receivable = Green (CreditGreen), Negative Payable = Red (DebitRed)
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.wrapContentWidth(Alignment.End)
            ) {
                AnimatedBalanceText(
                    amount = Math.abs(net),
                    currencySymbol = currencySymbol,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = if (net == 0.0) TextSecondary else (if (isOwed) CreditGreen else DebitRed)
                )

                Text(
                    text = if (net == 0.0) "Settled" else (if (isOwed) AppStrings.youWillGet(currentLang) else AppStrings.youWillGive(currentLang)),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End,
                    color = if (net == 0.0) TextSecondary else (if (isOwed) CreditGreen.copy(alpha = 0.9f) else DebitRed.copy(alpha = 0.9f))
                )
            }
        }
    }
}
