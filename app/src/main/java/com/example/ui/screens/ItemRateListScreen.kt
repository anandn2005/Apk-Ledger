package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import com.example.data.AppPreferencesManager
import com.example.data.RateVariant
import com.example.ui.components.IosScrollbarPill
import com.example.ui.components.FluentPriceHistoryChart
import com.example.ui.components.rememberIsolatedNestedScrollConnection
import com.example.ui.components.CategoryIconPickerDialog
import com.example.ui.components.VectorIconRegistry
import com.example.ui.components.VectorIconView
import com.example.ui.i18n.AppStrings
import com.example.ui.components.ItemRateSkeletonList
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import com.example.ui.components.AnimatedPillSearchBar
import com.example.ui.components.CloudSyncStatusBadge
import com.example.ui.components.ScrollEdgeShadow
import com.example.ui.components.rememberCanScrollBack
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.example.data.RateItemEntity
import com.example.data.Money
import com.example.data.MoneyMath
import com.example.ui.Formatters
import com.example.ui.LedgerUiState
import com.example.ui.LedgerViewModel
import com.example.ui.theme.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import com.example.ui.AppHaptics
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class PricePoint(
    val date: Long,
    val buyRate: Double,
    val sellPrice: Double,
    val sellPriceStr: String = ""
) {
    fun getDisplaySellPrice(): String {
        val cleanStr = if (sellPriceStr.isNotBlank()) sellPriceStr.substringBefore(" (").trim() else String.format("%.2f", sellPrice)
        return cleanStr
    }
}

fun parsePriceHistory(json: String): List<PricePoint> {
    if (json.isBlank()) return emptyList()
    android.os.Trace.beginSection("parsePriceHistory")
    val list = mutableListOf<PricePoint>()
    try {
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                PricePoint(
                    date = obj.optLong("date", System.currentTimeMillis()),
                    buyRate = obj.optDouble("buyRate", 0.0),
                    sellPrice = obj.optDouble("sellPrice", 0.0),
                    sellPriceStr = obj.optString("sellPriceStr", "")
                )
            )
        }
    } catch (e: Exception) {
        // Fallback
    } finally {
        android.os.Trace.endSection()
    }
    // Deduplicate incoming price points by price and timestamp window (3-second bucket)
    return list.distinctBy { "${it.buyRate}_${it.sellPrice}_${it.date / 3000}" }.sortedByDescending { it.date }
}

fun encodePriceHistory(list: List<PricePoint>): String {
    android.os.Trace.beginSection("encodePriceHistory")
    return try {
        val array = JSONArray()
        list.forEach { p ->
            val obj = JSONObject()
            obj.put("date", p.date)
            obj.put("buyRate", p.buyRate)
            obj.put("sellPrice", p.sellPrice)
            obj.put("sellPriceStr", p.sellPriceStr)
            array.put(obj)
        }
        array.toString()
    } finally {
        android.os.Trace.endSection()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ItemRateListScreen(
    state: LedgerUiState,
    viewModel: LedgerViewModel,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    onSelectionModeChange: (Boolean) -> Unit = {},
    onCreateInvoiceFromItems: (List<com.example.data.RateItemEntity>) -> Unit = {},
    onCreateInvoiceWithCartItems: (List<com.example.data.InvoiceItem>) -> Unit = {}
) {
    var showAddEditSheet by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<RateItemEntity?>(null) }
    var historyItem by remember { mutableStateOf<RateItemEntity?>(null) }
    var selectedVariantIdForHistory by remember { mutableStateOf<String?>(null) }
    var itemForRateUpdate by remember { mutableStateOf<RateItemEntity?>(null) }

    // Multi-select / Mass Delete state & Quick-Bill Counter Cart state
    var selectedItemIds by remember { mutableStateOf(setOf<String>()) }
    var cartItemsMap by remember { mutableStateOf<Map<String, com.example.data.InvoiceItem>>(emptyMap()) }
    var isCompactView by rememberSaveable { mutableStateOf(false) }
    var isCounterMode by rememberSaveable { mutableStateOf(false) }
    val isSelectionMode = selectedItemIds.isNotEmpty()
    val hasActiveCart = isSelectionMode || cartItemsMap.isNotEmpty()
    var showConfirmMassDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(hasActiveCart) {
        onSelectionModeChange(hasActiveCart)
    }

    // Inline Category creation and Edit states
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var categoryToEdit by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()
    val canScrollBack = rememberCanScrollBack(listState)
    val coroutineScope = rememberCoroutineScope()
    val categoryRowState = rememberLazyListState()
    var isCategoryPillsVisible by rememberSaveable { mutableStateOf(false) }

    val isHeaderVisible = true

    val pullToRevealConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 15f && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
                    if (!isCategoryPillsVisible) {
                        isCategoryPillsVisible = true
                    }
                }
                return Offset.Zero
            }
        }
    }

    // Auto-open item if requested from Homescreen Widget
    LaunchedEffect(state.targetRateItemIdToOpen) {
        val targetId = state.targetRateItemIdToOpen
        if (!targetId.isNullOrBlank()) {
            val found = state.rateItems.find { it.id == targetId }
            if (found != null) {
                itemToEdit = found
                showAddEditSheet = true
                viewModel.clearTargetRateItemId()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
            // Header Bar - Selection mode or Normal header
            if (isSelectionMode) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { selectedItemIds = emptySet() }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "Clear Selection",
                                        tint = Color(0xFF8E8E93)
                                    )
                                }
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "${selectedItemIds.size} Selected",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val allSelected = selectedItemIds.size == state.rateItems.size && state.rateItems.isNotEmpty()
                                // Minimalist toggle icon button with SF outline style
                                IconButton(
                                    onClick = {
                                        if (allSelected) {
                                            selectedItemIds = emptySet()
                                        } else {
                                            selectedItemIds = state.rateItems.map { it.id }.toSet()
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (allSelected) Icons.Outlined.CheckCircle else Icons.Outlined.SelectAll,
                                        contentDescription = if (allSelected) "Deselect All" else "Select All",
                                        tint = if (allSelected) StatusEmerald else Color(0xFF8E8E93)
                                    )
                                }

                                // Delete action: neutral gray outline icon by default
                                IconButton(
                                    onClick = { showConfirmMassDeleteDialog = true }
                                ) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = "Delete Selected Items",
                                        tint = Color(0xFF8E8E93)
                                    )
                                }
                            }
                        }
                        HorizontalDivider(
                            color = if (isAppInDarkTheme()) Color(0xFF2C2C2E) else Color(0xFFE5E5EA),
                            thickness = 1.dp
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    // Collapsing Title Row (ItemList title + sync status)
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
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp, bottom = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Items",
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
                                    CloudSyncStatusBadge(
                                        syncState = state.syncState,
                                        authUser = state.authUser,
                                        onTriggerSync = { viewModel.triggerCloudSync() },
                                        onOpenSettings = onOpenSettings
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }

                    // Search Bar + Category Filter Toggle: Permanently visible at top
                    var localRateQuery by remember { mutableStateOf(state.rateSearchQuery) }
                    LaunchedEffect(state.rateSearchQuery) {
                        if (state.rateSearchQuery != localRateQuery) {
                            localRateQuery = state.rateSearchQuery
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            AnimatedPillSearchBar(
                                query = localRateQuery,
                                onQueryChange = { newQuery ->
                                    localRateQuery = newQuery
                                    viewModel.setRateSearchQuery(newQuery)
                                },
                                placeholderSequence = listOf(
                                    "Search items...",
                                    "Search Pulses",
                                    "Search Rice & Grains",
                                    "Search Dry Fruit",
                                    "Search Oils & Ghee"
                                ),
                                testTag = "rate_item_search_input"
                            )
                        }

                        // Cart Mode Toggle Button (+ / - Quantity Stepper)
                        val isCartModeActive = isCounterMode || cartItemsMap.isNotEmpty()
                        Surface(
                            onClick = {
                                isCounterMode = !isCounterMode
                                isCompactView = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isCartModeActive)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                if (isAppInDarkTheme()) DarkSurfaceVariant else Color(0xFFF1F5F9),
                            border = BorderStroke(
                                1.dp,
                                if (isCartModeActive)
                                    Color(0xFF10B981)
                                else
                                    if (isAppInDarkTheme()) DarkBorder else Color(0xFFCBD5E1)
                            ),
                            shadowElevation = 1.dp,
                            modifier = Modifier
                                .height(44.dp)
                                .testTag("rate_cart_mode_toggle")
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ShoppingCart,
                                    contentDescription = "Cart",
                                    tint = if (isCartModeActive)
                                        Color(0xFF10B981)
                                    else
                                        Color(0xFF64748B),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Cart",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isCartModeActive) Color(0xFF10B981) else Color(0xFF64748B)
                                )
                            }
                        }

                        // Category Filter Toggle Button with label (#6)
                        Surface(
                            onClick = { isCategoryPillsVisible = !isCategoryPillsVisible },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isCategoryPillsVisible || state.selectedRateCategory != "ALL")
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                if (isAppInDarkTheme()) DarkSurfaceVariant else Color(0xFFF1F5F9),
                            border = BorderStroke(
                                1.dp,
                                if (isCategoryPillsVisible || state.selectedRateCategory != "ALL")
                                    Color(0xFF10B981)
                                else
                                    if (isAppInDarkTheme()) DarkBorder else Color(0xFFCBD5E1)
                            ),
                            shadowElevation = 1.dp,
                            modifier = Modifier.height(44.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (isCategoryPillsVisible) Icons.Default.FilterListOff else Icons.Default.FilterList,
                                    contentDescription = "Filter items by category",
                                    tint = if (isCategoryPillsVisible || state.selectedRateCategory != "ALL")
                                        Color(0xFF10B981)
                                    else
                                        Color(0xFF64748B),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Filter",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isCategoryPillsVisible || state.selectedRateCategory != "ALL") Color(0xFF10B981) else Color(0xFF64748B)
                                )
                            }
                        }
                    }

                    // iOS-Style Continuous Segmented Category Track with Animated Sliding Pill & Edge Fade-out Gradient Mask
                    AnimatedVisibility(
                        visible = isCategoryPillsVisible && isHeaderVisible,
                        enter = expandVertically(animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(animationSpec = tween(200)),
                        exit = shrinkVertically(animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) + fadeOut(animationSpec = tween(180))
                    ) {
                        val haptic = LocalHapticFeedback.current
                        val isDark = isAppInDarkTheme()
                        val edgeFadeColor = if (isDark) DarkSurfaceVariant else Color(0xFFF1F5F9)

                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = if (isDark) DarkSurfaceVariant else Color(0xFFF1F5F9),
                                border = BorderStroke(1.dp, if (isDark) DarkBorder else Color(0xFFCBD5E1)),
                                shadowElevation = 1.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    LazyRow(
                                        state = categoryRowState,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        contentPadding = PaddingValues(start = 6.dp, end = 34.dp, top = 4.dp, bottom = 4.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        itemsIndexed(
                                            items = state.rateCategories,
                                            key = { _, cat -> "cat_segment_$cat" },
                                            contentType = { _, _ -> "category_pill" }
                                        ) { index, cat ->
                                            val isSelected = cat.equals(state.selectedRateCategory, ignoreCase = true)
                                            val pillScale by animateFloatAsState(
                                                targetValue = if (isSelected) 1.02f else 1.0f,
                                                animationSpec = spring(dampingRatio = 0.8f, stiffness = 420f),
                                                label = "catPillScale"
                                            )

                                            Box(
                                                modifier = Modifier
                                                    .graphicsLayer {
                                                        scaleX = pillScale
                                                        scaleY = pillScale
                                                    }
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .then(
                                                        if (isSelected) {
                                                            Modifier
                                                                .shadow(2.dp, RoundedCornerShape(16.dp), spotColor = Color(0xFF10B981).copy(alpha = 0.40f))
                                                                .background(
                                                                    brush = EmeraldGradient,
                                                                    shape = RoundedCornerShape(16.dp)
                                                                )
                                                        } else {
                                                            Modifier.background(Color.Transparent)
                                                        }
                                                    )
                                                    .combinedClickable(
                                                        onClick = {
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            viewModel.setRateCategoryFilter(cat)
                                                            coroutineScope.launch {
                                                                categoryRowState.animateScrollToItem((index - 1).coerceAtLeast(0))
                                                            }
                                                        },
                                                        onLongClick = {
                                                            if (cat != "ALL") {
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                categoryToEdit = cat
                                                            }
                                                        }
                                                    )
                                                    .padding(horizontal = 13.dp, vertical = 6.dp)
                                                    .testTag("category_pill_$cat"),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    val catIcon = if (cat == "ALL") {
                                                        Icons.Default.Apps
                                                    } else {
                                                        val catItem = VectorIconRegistry.findByKey(state.categoryIconMap[cat])
                                                            ?: VectorIconRegistry.inferIconForCategory(cat)
                                                        catItem.fallbackVector
                                                    }

                                                    Icon(
                                                        imageVector = if (isSelected) Icons.Default.Check else catIcon,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(15.dp),
                                                        tint = if (isSelected) Color.White else Color(0xFF64748B)
                                                    )
                                                    Text(
                                                        text = if (cat == "ALL") AppStrings.all(state.selectedLanguage) else cat,
                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                                        )
                                                    )
                                                }
                                            }
                                        }

                                        // Inline + Add Category Action Chip
                                        item(
                                            key = "add_category_chip_btn",
                                            contentType = "add_category_btn"
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(if (isDark) DarkSurface else Color.White)
                                                    .border(1.dp, if (isDark) DarkBorder else Color(0xFFCBD5E1), RoundedCornerShape(16.dp))
                                                    .clickable {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        showAddCategoryDialog = true
                                                    }
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                                    .testTag("add_category_pill_button"),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Add,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp),
                                                        tint = Color(0xFF10B981)
                                                    )
                                                    Text(
                                                        text = "Add",
                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF10B981)
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Right-edge Fade-out Gradient Mask overlay to eliminate harsh text truncation
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .fillMaxHeight()
                                            .width(34.dp)
                                            .background(
                                                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                    colors = listOf(Color.Transparent, edgeFadeColor)
                                                )
                                            )
                                    )

                                    // Left-edge Fade-out Gradient Mask overlay when scrolled backward
                                    if (categoryRowState.canScrollBackward) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.CenterStart)
                                                .fillMaxHeight()
                                                .width(22.dp)
                                                .background(
                                                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                        colors = listOf(edgeFadeColor, Color.Transparent)
                                                    )
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Universal Fixed Table Header (ITEM NAME | BUY | SELL)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSelectionMode) {
                        Spacer(modifier = Modifier.width(34.dp))
                    }
                    Text(
                        text = "ITEM NAME",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontFeatureSettings = "tnum"
                        ),
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF8E8E93),
                        letterSpacing = 1.sp,
                        modifier = Modifier.weight(1.5f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "BUY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontFeatureSettings = "tnum"
                        ),
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.End,
                        color = Color(0xFF8E8E93),
                        letterSpacing = 1.sp,
                        modifier = Modifier.weight(0.85f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "SELL",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontFeatureSettings = "tnum"
                        ),
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.End,
                        color = Color(0xFF8E8E93),
                        letterSpacing = 1.sp,
                        modifier = Modifier.weight(1.0f)
                    )
                }
            }

            HorizontalDivider(
                color = if (isAppInDarkTheme()) Color(0xFF2C2C2E) else Color(0xFFE5E5EA),
                thickness = 1.dp
            )

            // List of Rate Items with smooth fluid animations and swipe-to-reveal
            if (state.rateItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(pullToRevealConnection)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FormatListBulleted,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(56.dp)
                        )
                        Text(
                            text = if (state.rateSearchQuery.isNotBlank()) "No matching items." else "List is empty.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = {
                                itemToEdit = null
                                showAddEditSheet = true
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (state.rateSearchQuery.isNotBlank()) "Add '${state.rateSearchQuery}' as New Item" else "Add New Item")
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    val cartSnapshot = remember(cartItemsMap) {
                        if (cartItemsMap.isEmpty()) VariantCartSnapshot.Empty else VariantCartSnapshot(cartItemsMap)
                    }
                    val onAdjustCartQtyStable = remember {
                        { key: String, desc: String, unitPrice: Double, delta: Double ->
                            val existing = cartItemsMap[key]
                            val nextQty = ((existing?.quantity ?: 0.0) + delta).coerceAtLeast(0.0)
                            cartItemsMap = if (nextQty <= 0.0) {
                                cartItemsMap - key
                            } else {
                                cartItemsMap + (key to com.example.data.InvoiceItem(
                                    description = desc,
                                    quantity = nextQty,
                                    unitPrice = unitPrice
                                ))
                            }
                        }
                    }

                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(top = 0.dp, bottom = 145.dp),
                        verticalArrangement = Arrangement.Top,
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(pullToRevealConnection)
                    ) {
                        itemsIndexed(
                            items = state.rateItems,
                            key = { _, item -> item.id }, // NEVER use list index
                            contentType = { _, item -> if (item.variants.isNotEmpty()) "variant" else "single" }
                        ) { index, item ->
                            val isSelected = selectedItemIds.contains(item.id)
                            val itemCartQty = cartSnapshot.quantities[item.id]?.quantity ?: 0.0
                            RateItemCard(
                                item = item,
                                rowIndex = index,
                                currencySymbol = state.currencySymbol,
                                isSelected = isSelected,
                                isSelectionMode = isSelectionMode,
                                isCompactView = isCompactView,
                                isCounterMode = isCounterMode || cartSnapshot.quantities.isNotEmpty(),
                                cartQuantity = itemCartQty,
                                variantCartSnapshot = if (item.variants.isNotEmpty()) cartSnapshot else VariantCartSnapshot.Empty,
                                onAdjustCartQty = onAdjustCartQtyStable,
                                onToggleSelect = {
                                    selectedItemIds = if (isSelected) {
                                        selectedItemIds - item.id
                                    } else {
                                        selectedItemIds + item.id
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        selectedItemIds = setOf(item.id)
                                    }
                                },
                                onEdit = {
                                    itemToEdit = item
                                    showAddEditSheet = true
                                },
                                onDelete = {
                                    viewModel.deleteRateItem(item)
                                },
                                onViewHistory = { targetVariantId ->
                                    historyItem = item
                                    selectedVariantIdForHistory = targetVariantId
                                },
                                onUpdateRateClick = { itemForRateUpdate = item }
                            )
                        }
                    }

                    // Top and Bottom subtle diffused blur overlays for fluid scroll
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

                    // iOS-Style Interactive Scrollbar Pill Overlay
                    IosScrollbarPill(
                        listState = listState,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )

                    // Cart in Select Mode or Quick-Bill Counter Mode Floating Bottom Bar
                    androidx.compose.animation.AnimatedVisibility(
                        visible = hasActiveCart,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .zIndex(100f)
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        val selectedItems = remember(selectedItemIds, state.rateItems) {
                            state.rateItems.filter { selectedItemIds.contains(it.id) }
                        }
                        val combinedInvoiceItems = remember(selectedItems, cartItemsMap) {
                            val list = mutableListOf<com.example.data.InvoiceItem>()
                            list.addAll(cartItemsMap.values)
                            val existingDescs = list.map { it.description.lowercase() }.toSet()
                            selectedItems.forEach { item ->
                                if (!existingDescs.contains(item.name.lowercase())) {
                                    val unitPrice = if (item.sellPrice > 0.0) item.sellPrice
                                    else MoneyMath.parseMoneyFromUserInput(item.sellPriceStr).toRupeesDouble()
                                    list.add(
                                        com.example.data.InvoiceItem(
                                            description = item.name,
                                            quantity = 1.0,
                                            unitPrice = unitPrice
                                        )
                                    )
                                }
                            }
                            list
                        }
                        val totalUnitsCount = remember(combinedInvoiceItems) {
                            combinedInvoiceItems.sumOf { it.quantity }.toInt().coerceAtLeast(combinedInvoiceItems.size)
                        }
                        val totalSellPaise = remember(combinedInvoiceItems) {
                            combinedInvoiceItems.sumOf { invItem ->
                                MoneyMath.fromRupeesDouble(invItem.total).paise
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
                            shadowElevation = 8.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(
                                                MaterialTheme.colorScheme.primaryContainer,
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ShoppingCart,
                                            contentDescription = "Cart",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "${combinedInvoiceItems.size} items ($totalUnitsCount qty)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = Money(totalSellPaise).format(state.currencySymbol),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            cartItemsMap = emptyMap()
                                            selectedItemIds = emptySet()
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = "Clear Cart",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    FilledTonalButton(
                                        onClick = {
                                            if (cartItemsMap.isNotEmpty()) {
                                                onCreateInvoiceWithCartItems(combinedInvoiceItems)
                                            } else {
                                                onCreateInvoiceFromItems(selectedItems)
                                            }
                                            cartItemsMap = emptyMap()
                                            selectedItemIds = emptySet()
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = Color.White
                                        ),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ReceiptLong,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Make Receipt",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    // Mass Delete Confirm Dialog
    if (showConfirmMassDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmMassDeleteDialog = false },
            title = { Text("Delete Items?") },
            text = { Text("Delete ${selectedItemIds.size} selected items?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteRateItems(selectedItemIds)
                        selectedItemIds = emptySet()
                        showConfirmMassDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DebitRed, contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmMassDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Dynamic Vector Icon Search & Category Creator Dialog
    if (showAddCategoryDialog) {
        CategoryIconPickerDialog(
            onDismiss = { showAddCategoryDialog = false },
            onConfirm = { catName, iconKey, iconUrl ->
                viewModel.addRateCategory(catName, iconKey, iconUrl)
                showAddCategoryDialog = false
            }
        )
    }

    // Contextual Category Edit Popover (Triggered on Category Pill Long-Press)
    if (categoryToEdit != null) {
        val currentCat = categoryToEdit!!
        val currentIconKey = state.categoryIconMap[currentCat] ?: VectorIconRegistry.inferIconForCategory(currentCat).key
        CategoryIconPickerDialog(
            initialCategoryName = currentCat,
            initialIconKey = currentIconKey,
            title = "Edit Category & Icon",
            onDismiss = { categoryToEdit = null },
            onDelete = {
                viewModel.deleteRateCategory(currentCat)
                categoryToEdit = null
            },
            onConfirm = { newName, newIconKey, newIconUrl ->
                viewModel.editRateCategory(currentCat, newName, newIconKey, newIconUrl)
                categoryToEdit = null
            }
        )
    }

    // Add / Edit Rate Item Screen (Full-Page 1-Step Fast Entry)
    if (showAddEditSheet) {
        BackHandler {
            showAddEditSheet = false
            itemToEdit = null
        }
        AddEditRateItemSheet(
            existingItem = itemToEdit,
            currencySymbol = state.currencySymbol,
            categories = state.rateCategories.filter { it != "ALL" },
            existingItems = state.rateItems,
            categoryIconMap = state.categoryIconMap,
            initialCategory = state.selectedRateCategory,
            availableUnits = state.availableUnits,
            onDismiss = {
                showAddEditSheet = false
                itemToEdit = null
            },
            onAddCategoryWithIcon = { catName, iconKey, iconUrl ->
                viewModel.addRateCategory(catName, iconKey, iconUrl)
            },
            onSave = { name, category, unit, qty, buyRate, sellPriceStr, hasMultipleVariants, variants ->
                android.util.Log.d("VARIANT_TRACE", "Hop 1 - Save Handler BEFORE save: name='$name', hasMultipleVariants=$hasMultipleVariants, variantsCount=${variants.size}, variants=${variants.map { "${it.label}(buy=${it.buyRate},sell=${it.sellPriceStr})" }}")
                val cleanSell = sellPriceStr.replace("₹", "").replace("$", "").trim()
                val sellPriceNum = if (cleanSell.contains("-")) {
                    cleanSell.split("-").firstOrNull()?.trim()?.toDoubleOrNull() ?: 0.0
                } else {
                    cleanSell.toDoubleOrNull() ?: 0.0
                }
                val encodedVars = if (hasMultipleVariants && variants.isNotEmpty()) RateItemEntity.encodeVariants(variants) else "[]"
                if (itemToEdit == null) {
                    viewModel.addRateItem(name, category, unit, qty, buyRate, sellPriceNum, sellPriceStr, hasMultipleVariants, variants)
                } else {
                    viewModel.updateRateItem(
                        itemToEdit!!.copy(
                            name = name,
                            category = category,
                            unit = unit,
                            qty = qty,
                            buyRate = buyRate,
                            sellPrice = sellPriceNum,
                            sellPriceStr = sellPriceStr,
                            hasMultipleVariants = hasMultipleVariants && variants.isNotEmpty(),
                            variantsJson = encodedVars,
                            lastUpdated = System.currentTimeMillis()
                        )
                    )
                }
                showAddEditSheet = false
                itemToEdit = null
            }
        )
    }

    // Quick Update Rate Dialog
    if (itemForRateUpdate != null) {
        QuickUpdateRateDialog(
            item = itemForRateUpdate!!,
            currencySymbol = state.currencySymbol,
            onDismiss = { itemForRateUpdate = null },
            onSave = { newBuyRate, newSellPriceStr ->
                val target = itemForRateUpdate!!
                val now = System.currentTimeMillis()
                val sellNum = newSellPriceStr.toDoubleOrNull() ?: 0.0
                val existingHistory = parsePriceHistory(target.priceHistoryJson).toMutableList()
                val lastPoint = existingHistory.firstOrNull()
                if (lastPoint == null || Math.abs(now - lastPoint.date) > 3000 || lastPoint.buyRate != newBuyRate || lastPoint.sellPriceStr != newSellPriceStr) {
                    existingHistory.add(0, PricePoint(date = now, buyRate = newBuyRate, sellPrice = sellNum, sellPriceStr = newSellPriceStr))
                }
                val dedupedHistory = existingHistory.distinctBy { (it.date / 1000) to (it.sellPriceStr.ifBlank { it.sellPrice.toString() }) }

                val updated = target.copy(
                    buyRate = newBuyRate,
                    sellPrice = sellNum,
                    sellPriceStr = newSellPriceStr,
                    priceHistoryJson = encodePriceHistory(dedupedHistory),
                    lastUpdated = now
                )
                viewModel.updateRateItem(updated)
                itemForRateUpdate = null
            },
            onSaveVariantRate = { variantId, newBuyRate, newSellPriceStr ->
                val target = itemForRateUpdate!!
                val now = System.currentTimeMillis()
                val sellNum = newSellPriceStr.toDoubleOrNull() ?: 0.0
                val updatedVariants = target.getVariants().map { v ->
                    if (v.id == variantId) {
                        v.copy(buyRate = newBuyRate, sellPrice = sellNum, sellPriceStr = newSellPriceStr)
                    } else {
                        v
                    }
                }
                val firstVariant = updatedVariants.firstOrNull()
                val updated = target.copy(
                    hasMultipleVariants = true,
                    variantsJson = RateItemEntity.encodeVariants(updatedVariants),
                    buyRate = firstVariant?.buyRate ?: target.buyRate,
                    sellPrice = firstVariant?.sellPrice ?: target.sellPrice,
                    sellPriceStr = firstVariant?.getDisplaySellPrice() ?: target.sellPriceStr,
                    lastUpdated = now
                )
                viewModel.updateRateItem(updated)
                itemForRateUpdate = null
            }
        )
    }

    // Price History Modal Sheet
    if (historyItem != null) {
        PriceHistorySheet(
            item = historyItem!!,
            initialVariantId = selectedVariantIdForHistory,
            currencySymbol = state.currencySymbol,
            onDismiss = {
                historyItem = null
                selectedVariantIdForHistory = null
            },
            onUpdateHistory = { updatedItem ->
                viewModel.updateRateItem(updatedItem)
                historyItem = updatedItem
            }
        )
    }
}

@Composable
fun SparklineMicroChart(
    dataPoints: List<Double>,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier.size(width = 36.dp, height = 18.dp)
) {
    if (dataPoints.size < 2) return
    Canvas(modifier = modifier) {
        val minVal = dataPoints.minOrNull() ?: 0.0
        val maxVal = dataPoints.maxOrNull() ?: 1.0
        val range = if (maxVal == minVal) 1.0 else maxVal - minVal

        val w = size.width
        val h = size.height
        val stepX = w / (dataPoints.size - 1)

        val path = Path()
        dataPoints.forEachIndexed { i, value ->
            val normY = 1.0f - ((value - minVal) / range).toFloat()
            val x = i * stepX
            val y = (normY * (h - 4.dp.toPx())) + 2.dp.toPx()
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun IOSSelectionCheckmark(
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    accentColor: Color = StatusEmerald
) {
    val isDark = isAppInDarkTheme()
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.0f else 0.95f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "checkmarkScale"
    )

    Box(
        modifier = modifier
            .size(22.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(if (isSelected) accentColor else Color.Transparent)
            .border(
                width = if (isSelected) 0.dp else 1.5.dp,
                color = if (isSelected) Color.Transparent else if (isDark) Color(0xFF636366) else Color(0xFFC7C7CC),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = isSelected,
            enter = fadeIn(tween(120)) + scaleIn(spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)),
            exit = fadeOut(tween(90)) + scaleOut(tween(90))
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@Composable
private fun QuickCartStepperPill(
    quantity: Double,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isDark = isAppInDarkTheme()
    if (quantity <= 0.0) {
        Surface(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onIncrement()
            },
            shape = CircleShape,
            color = StatusEmerald.copy(alpha = if (isDark) 0.22f else 0.12f),
            border = BorderStroke(1.dp, StatusEmerald.copy(alpha = 0.45f)),
            modifier = modifier.size(26.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add to Cart",
                    tint = StatusEmerald,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    } else {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = StatusEmerald,
            modifier = modifier.height(26.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDecrement()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Decrease quantity",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Text(
                    text = if (quantity % 1.0 == 0.0) quantity.toInt().toString() else String.format(java.util.Locale.US, "%.1f", quantity),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onIncrement()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Increase quantity",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

@androidx.compose.runtime.Immutable
data class VariantCartSnapshot(
    val quantities: Map<String, com.example.data.InvoiceItem> = emptyMap()
) {
    companion object {
        val Empty = VariantCartSnapshot()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RateItemCard(
    item: RateItemEntity,
    rowIndex: Int = 0,
    currencySymbol: String,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    isCompactView: Boolean = false,
    isCounterMode: Boolean = false,
    cartQuantity: Double = 0.0,
    variantCartSnapshot: VariantCartSnapshot = VariantCartSnapshot.Empty,
    onAdjustCartQty: (key: String, desc: String, unitPrice: Double, delta: Double) -> Unit = { _, _, _, _ -> },
    onToggleSelect: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onViewHistory: (selectedVariantId: String?) -> Unit,
    onUpdateRateClick: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    val isDark = isAppInDarkTheme()
    var isExpanded by rememberSaveable(item.id) { mutableStateOf(false) }
    var offsetX by remember(item.id) { mutableFloatStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "rateSwipeOffset"
    )

    val density = LocalDensity.current
    val minSwipePx = remember(density) { with(density) { 60.dp.toPx() } }
    val maxSwipePx = remember(density) { with(density) { 100.dp.toPx() } }
    val openRightSwipePx = remember(density) { with(density) { 80.dp.toPx() } }
    val openLeftSwipePx = remember(density) { with(density) { -80.dp.toPx() } }

    val stripeBg = if (isCompactView && rowIndex % 2 == 1) {
        if (isDark) Color(0xFF202024) else Color(0xFFF8FAFC)
    } else {
        if (isDark) DarkSurface else SurfaceLight
    }

    val hasVariants = item.variants.isNotEmpty()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isDark) Color(0xFF1C1C1E) else Color.White)
    ) {
        // Swipe actions background (only composed when user actively swipes the row)
        if (!isSelectionMode && (offsetX != 0f || animatedOffsetX != 0f)) {
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (offsetX > 0 || animatedOffsetX > 0) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF007AFF).copy(alpha = 0.15f))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                offsetX = 0f
                                onEdit()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit item",
                            tint = Color(0xFF007AFF),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(38.dp))
                }

                if (offsetX < 0 || animatedOffsetX < 0) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF3B30).copy(alpha = 0.15f))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                offsetX = 0f
                                onDelete()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Delete item",
                            tint = Color(0xFFFF3B30),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(38.dp))
                }
            }
        }

        // Foreground Row Surface
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .background(
                    if (isSelected || cartQuantity > 0.0) {
                        if (isDark) StatusEmeraldDarkBg else StatusEmeraldLight
                    } else {
                        stripeBg
                    }
                )
                .pointerInput(isSelectionMode) {
                    if (!isSelectionMode) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                when {
                                    offsetX < -minSwipePx -> {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        offsetX = openLeftSwipePx
                                    }
                                    offsetX > minSwipePx -> {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        offsetX = openRightSwipePx
                                    }
                                    else -> offsetX = 0f
                                }
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                val newOffset = offsetX + dragAmount
                                offsetX = newOffset.coerceIn(-maxSwipePx, maxSwipePx)
                            }
                        )
                    }
                }
                .combinedClickable(
                    onClick = {
                        if (offsetX != 0f) {
                            offsetX = 0f
                        } else if (isSelectionMode) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggleSelect()
                        } else if (hasVariants) {
                            isExpanded = !isExpanded
                        } else {
                            onViewHistory(null)
                        }
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    }
                )
                .testTag("rate_item_card_${item.id}")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Parent Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 16.dp,
                            vertical = if (isCompactView) 3.dp else 6.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Selection Accent Checkbox
                    if (isSelectionMode) {
                        IOSSelectionCheckmark(
                            isSelected = isSelected,
                            accentColor = StatusEmerald,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    }

                    // 1. ITEM NAME (Left aligned)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = if (isCompactView) 13.5.sp else 15.sp
                            ),
                            fontWeight = FontWeight.Medium,
                            color = if (isDark) Color.White else Color(0xFF1C1C1E),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isCompactView && item.category.isNotBlank() && item.category != "General") {
                            Text(
                                text = item.category.take(8),
                                fontSize = 9.sp,
                                color = Color(0xFF8E8E93),
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    if (!hasVariants) {
                        // 2. BUY VALUE (Pre-cached on RateItemEntity, zero per-frame String.format)
                        val formattedBuyRate = item.getFormattedBuyRate(currencySymbol)
                        Text(
                            text = formattedBuyRate,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = if (isCompactView) 12.sp else 13.sp,
                                fontFeatureSettings = "tnum"
                            ),
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.End,
                            color = Color(0xFF8E8E93),
                            maxLines = 1,
                            modifier = Modifier.weight(0.85f)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        // 3. SELL VALUE + Optional Counter Pill (Pre-cached on RateItemEntity)
                        val hasSell = item.hasValidSellPrice
                        val displaySell = item.getFormattedSellPrice(currencySymbol)
                        val numericSellPrice = item.numericSellPrice

                        Row(
                            modifier = Modifier.weight(1.05f),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = displaySell,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = if (isCompactView) 13.5.sp else 15.sp,
                                    fontFeatureSettings = "tnum"
                                ),
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.End,
                                color = if (hasSell) StatusEmerald else Color(0xFF8E8E93),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (isCounterMode || isCompactView || cartQuantity > 0.0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                QuickCartStepperPill(
                                    quantity = cartQuantity,
                                    onIncrement = {
                                        onAdjustCartQty(item.id, item.name, numericSellPrice, 1.0)
                                    },
                                    onDecrement = {
                                        onAdjustCartQty(item.id, item.name, numericSellPrice, -1.0)
                                    }
                                )
                            }
                        }
                    } else {
                        // Multi-variant parent item header: NO buy/sell prices on top line. iOS chevron toggle & variant chip on right.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (isCompactView) {
                                Text(
                                    text = item.getDisplaySellPrice(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = StatusEmerald,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                            }
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    isExpanded = !isExpanded
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA).copy(alpha = 0.6f),
                                modifier = Modifier.testTag("variant_accordion_toggle_${item.id}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "${item.variants.size} sizes",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF8E8E93)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (isExpanded) "Collapse variants" else "Expand variants",
                                        tint = Color(0xFF8E8E93),
                                        modifier = Modifier
                                            .size(16.dp)
                                            .graphicsLayer { rotationZ = if (isExpanded) 180f else 0f }
                                    )
                                }
                            }
                        }
                    }
                }

                // Expanded Multi-Variant Accordion Table (Only composed when expanded)
                if (isExpanded && hasVariants) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 1.dp, bottom = 6.dp)
                            .testTag("variant_accordion_table_${item.id}"),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7),
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF38383A) else Color(0xFFE5E5EA))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            item.variants.forEachIndexed { index, v ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        color = if (isDark) Color(0xFF38383A) else Color(0xFFE5E5EA),
                                        thickness = 1.dp,
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    )
                                }

                                val vBuyStr = v.getFormattedBuyRate(currencySymbol)
                                val vSellStr = v.getFormattedSellPrice(currencySymbol)
                                val variantCartKey = "${item.id}_${v.id}"
                                val variantQty = variantCartSnapshot.quantities[variantCartKey]?.quantity ?: 0.0
                                val vNumericPrice = v.numericSellPrice

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isSelectionMode) {
                                                onToggleSelect()
                                            } else {
                                                onViewHistory(v.id)
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = v.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isDark) Color.White else Color(0xFF1C1C1E),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1.2f)
                                    )

                                    Text(
                                        text = vBuyStr,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                        fontWeight = FontWeight.Normal,
                                        textAlign = TextAlign.End,
                                        color = Color(0xFF8E8E93),
                                        maxLines = 1,
                                        modifier = Modifier.weight(0.95f)
                                    )

                                    Text(
                                        text = vSellStr,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.End,
                                        color = if (vSellStr != "-") StatusEmerald else Color(0xFF8E8E93),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(0.95f)
                                    )

                                    Row(
                                        modifier = Modifier.padding(start = 6.dp),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isCounterMode || isCompactView || variantQty > 0.0) {
                                            QuickCartStepperPill(
                                                quantity = variantQty,
                                                onIncrement = {
                                                    onAdjustCartQty(variantCartKey, "${item.name} (${v.label})", vNumericPrice, 1.0)
                                                },
                                                onDecrement = {
                                                    onAdjustCartQty(variantCartKey, "${item.name} (${v.label})", vNumericPrice, -1.0)
                                                }
                                            )

                                            Spacer(modifier = Modifier.width(4.dp))
                                        }

                                        IconButton(
                                            onClick = { onUpdateRateClick() },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Edit,
                                                contentDescription = "Edit rate",
                                                tint = Color(0xFF007AFF),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { onViewHistory(v.id) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.History,
                                                contentDescription = "View history",
                                                tint = StatusEmerald,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Strict 1px Hairline Divider at bottom of each row
                HorizontalDivider(
                    color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA),
                    thickness = 1.dp,
                    modifier = Modifier.padding(start = if (isSelectionMode) 50.dp else 16.dp)
                )
            }
        }
    }
}

@Composable
fun QuickUpdateRateDialog(
    item: RateItemEntity,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onSave: (newBuyRate: Double, newSellPriceStr: String) -> Unit,
    onSaveVariantRate: (variantId: String, newBuyRate: Double, newSellPriceStr: String) -> Unit = { _, _, _ -> }
) {
    val variants = remember(item) { item.getVariants() }
    var selectedVariantId by remember(item) { mutableStateOf(variants.firstOrNull()?.id ?: "") }
    val currentVariant = variants.find { it.id == selectedVariantId } ?: variants.firstOrNull()

    var buyRateStr by remember(currentVariant) {
        mutableStateOf(
            if (currentVariant != null) (if (currentVariant.buyRate > 0) currentVariant.buyRate.toString() else "")
            else (if (item.buyRate > 0) item.buyRate.toString() else "")
        )
    }
    var sellPriceStr by remember(currentVariant) {
        mutableStateOf(
            currentVariant?.getDisplaySellPrice() ?: item.getDisplaySellPrice()
        )
    }
    var isSubmitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "New Rate Entry",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (item.hasVariants() && variants.isNotEmpty()) {
                    Text(
                        text = "Select Size / Variant to update:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(
                            items = variants,
                            key = { v -> v.id },
                            contentType = { "variant_chip" }
                        ) { v ->
                            val isSel = v.id == selectedVariantId
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        selectedVariantId = v.id
                                        buyRateStr = if (v.buyRate > 0) v.buyRate.toString() else ""
                                        sellPriceStr = v.getDisplaySellPrice()
                                    }
                            ) {
                                Text(
                                    text = "${v.label} ($currencySymbol${v.getDisplaySellPrice()})",
                                    fontSize = 12.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = buyRateStr,
                    onValueChange = { buyRateStr = it },
                    label = { Text("New Buy Cost ($currencySymbol)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface)
                )

                OutlinedTextField(
                    value = sellPriceStr,
                    onValueChange = { sellPriceStr = it },
                    label = { Text("New Selling Price ($currencySymbol)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface)
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting,
                onClick = {
                    if (!isSubmitting) {
                        isSubmitting = true
                        val buy = buyRateStr.toDoubleOrNull() ?: 0.0
                        if (item.hasVariants() && currentVariant != null) {
                            onSaveVariantRate(currentVariant.id, buy, sellPriceStr)
                        } else {
                            onSave(buy, sellPriceStr)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun getCategoryIcon(cat: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when (cat) {
        "Pulses & Kirana" -> Icons.Default.ShoppingBag
        "Dry Fruit" -> Icons.Default.Eco
        "Rice" -> Icons.Default.Grass
        "Surf & Soap" -> Icons.Default.CleanHands
        "Tobacco" -> Icons.Default.SmokingRooms
        "Tea" -> Icons.Default.Coffee
        "Namkeen" -> Icons.Default.Fastfood
        "Oil & Ghee" -> Icons.Default.WaterDrop
        "Biscuit" -> Icons.Default.Cookie
        "Others" -> Icons.Default.Category
        else -> {
            val lower = cat.lowercase()
            when {
                lower.contains("pulse") || lower.contains("kirana") -> Icons.Default.ShoppingBag
                lower.contains("dry") || lower.contains("fruit") || lower.contains("nut") -> Icons.Default.Eco
                lower.contains("rice") || lower.contains("grain") -> Icons.Default.Grass
                lower.contains("surf") || lower.contains("soap") || lower.contains("clean") -> Icons.Default.CleanHands
                lower.contains("tobacco") || lower.contains("smoke") -> Icons.Default.SmokingRooms
                lower.contains("tea") || lower.contains("coffee") -> Icons.Default.Coffee
                lower.contains("namkeen") || lower.contains("snack") || lower.contains("food") -> Icons.Default.Fastfood
                lower.contains("oil") || lower.contains("ghee") || lower.contains("liquid") -> Icons.Default.WaterDrop
                lower.contains("biscuit") || lower.contains("cookie") -> Icons.Default.Cookie
                else -> Icons.Default.Category
            }
        }
    }
}

@Composable
private fun ZenOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    testTag: String? = null,
    isFocused: Boolean,
    anyFocused: Boolean,
    onFocusChanged: (Boolean) -> Unit
) {
    val opacity by animateFloatAsState(
        targetValue = if (anyFocused && !isFocused) 0.45f else 1.0f,
        animationSpec = tween(250),
        label = "ZenOpacity"
    )
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.015f else 1.0f,
        animationSpec = tween(250),
        label = "ZenScale"
    )

    var mod = modifier
        .graphicsLayer {
            alpha = opacity
            scaleX = scale
            scaleY = scale
        }
        .onFocusChanged { focusState ->
            onFocusChanged(focusState.isFocused)
        }

    if (testTag != null) {
        mod = mod.testTag(testTag)
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = mod
    )
}

fun inferDefaultUnit(category: String): String {
    return when (category.lowercase(java.util.Locale.US)) {
        "pulses & kirana", "rice", "sugar", "flour" -> "Kg"
        "oil & ghee", "milk", "beverages" -> "Ltr"
        "dry fruit" -> "gm"
        "surf & soap", "biscuit", "namkeen", "tobacco", "tea" -> "Pkt"
        else -> "Pcs"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditRateItemSheet(
    existingItem: RateItemEntity?,
    currencySymbol: String,
    categories: List<String>,
    existingItems: List<RateItemEntity> = emptyList(),
    categoryIconMap: Map<String, String> = emptyMap(),
    initialCategory: String = "ALL",
    availableUnits: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onAddCategoryWithIcon: ((String, String, String) -> Unit)? = null,
    onSave: (name: String, category: String, unit: String, qty: Double, buyRate: Double, sellPriceStr: String, hasMultipleVariants: Boolean, variants: List<com.example.data.RateVariant>) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(context) { AppPreferencesManager(context) }
    val effectiveCategories = if (categories.isNotEmpty()) categories else prefs.customCategories
    val activeCategories = remember(effectiveCategories) {
        val filtered = effectiveCategories.filter { it != "ALL" }
        if (filtered.isNotEmpty()) filtered else prefs.customCategories.filter { it != "ALL" }
    }
    val effectiveUnits = if (availableUnits.isNotEmpty()) availableUnits else prefs.customUnits

    AddEditItemSheet(
        currencySymbol = currencySymbol,
        activeCategories = activeCategories,
        existingItem = existingItem,
        existingItems = existingItems,
        initialCategory = initialCategory,
        availableUnits = effectiveUnits,
        onDismiss = onDismiss,
        onAddCategoryWithIcon = onAddCategoryWithIcon,
        onSave = onSave
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VerticalUnitWheelPicker(
    units: List<String>,
    selectedUnit: String,
    onUnitSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val itemHeight = 22.dp
    val pickerHeight = 66.dp // Compact fixed-height wheel picker (~3 visible rows)

    val selectedIndex = remember(units, selectedUnit) {
        val idx = units.indexOf(selectedUnit)
        if (idx >= 0) idx else 0
    }

    val lazyListState = remember(units) {
        androidx.compose.foundation.lazy.LazyListState(firstVisibleItemIndex = selectedIndex)
    }
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = lazyListState)

    LaunchedEffect(units, selectedIndex) {
        if (selectedIndex in units.indices && lazyListState.firstVisibleItemIndex != selectedIndex) {
            lazyListState.scrollToItem(selectedIndex)
        }
    }

    val settledIndex by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex.coerceIn(0, (units.size - 1).coerceAtLeast(0))
        }
    }

    LaunchedEffect(settledIndex) {
        val newUnit = units.getOrNull(settledIndex)
        if (newUnit != null && newUnit != selectedUnit) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onUnitSelected(newUnit)
        }
    }

    Box(
        modifier = modifier
            .height(pickerHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .border(
                BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                ),
                RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        // Center Active Selection Highlight Bar
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .height(itemHeight + 2.dp)
                .background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    RoundedCornerShape(8.dp)
                )
        )

        LazyColumn(
            state = lazyListState,
            flingBehavior = snapFlingBehavior,
            contentPadding = PaddingValues(vertical = itemHeight),
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(
                items = units,
                key = { _, u -> "unit_wheel_$u" },
                contentType = { _, _ -> "unit_wheel_item" }
            ) { index, u ->
                val isSelected = index == settledIndex
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = u,
                        fontSize = if (isSelected) 13.sp else 10.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    )
                }
            }
        }

        // Top & Bottom fade gradient masks to reinforce scroll wheel affordance
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                        0.28f to Color.Transparent,
                        0.72f to Color.Transparent,
                        1.0f to MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                    )
                )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditItemSheet(
    currencySymbol: String,
    activeCategories: List<String>,
    existingItem: RateItemEntity?,
    existingItems: List<RateItemEntity>,
    initialCategory: String,
    availableUnits: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onAddCategoryWithIcon: ((String, String, String) -> Unit)? = null,
    onSave: (name: String, category: String, unit: String, qty: Double, buyRate: Double, sellPriceStr: String, hasMultipleVariants: Boolean, variants: List<com.example.data.RateVariant>) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val prefs = remember(context) { AppPreferencesManager(context) }
    val unitOptions = remember(availableUnits) {
        if (availableUnits.isNotEmpty()) availableUnits else prefs.customUnits
    }

    val defaultInitialCat = when {
        existingItem != null -> existingItem.category
        initialCategory.isNotBlank() && !initialCategory.equals("ALL", ignoreCase = true) -> initialCategory.trim()
        else -> activeCategories.firstOrNull() ?: "Pulses & Kirana"
    }

    var name by remember { mutableStateOf(existingItem?.name ?: "") }
    var category by remember { mutableStateOf(defaultInitialCat) }
    var unit by remember {
        mutableStateOf(
            existingItem?.unit ?: unitOptions.firstOrNull { it.equals(inferDefaultUnit(category), ignoreCase = true) } ?: unitOptions.firstOrNull() ?: "Kg"
        )
    }
    var isUnitManuallySelected by remember { mutableStateOf(existingItem != null) }

    // Multi-Variant Opt-In State
    var hasMultipleVariants by remember { mutableStateOf(existingItem?.hasMultipleVariants ?: false) }
    val variants = remember(existingItem) {
        val initial = existingItem?.getVariants()?.map { v ->
            val cleanStr = if (v.sellPriceStr == "0" || v.sellPriceStr == "0.0" || v.sellPriceStr == "0.00") {
                ""
            } else {
                v.sellPriceStr.ifBlank { v.getDisplaySellPrice() }
            }
            v.copy(sellPriceStr = cleanStr)
        } ?: emptyList()
        mutableStateListOf<com.example.data.RateVariant>().apply { addAll(initial) }
    }

    val initialQtyStr = existingItem?.let {
        if (it.qty % 1.0 == 0.0) it.qty.toLong().toString() else it.qty.toString()
    } ?: "1"

    var qtyStr by remember { mutableStateOf(initialQtyStr) }
    var qtyTextFieldValue by remember {
        mutableStateOf(TextFieldValue(text = initialQtyStr, selection = TextRange(0, initialQtyStr.length)))
    }

    fun formatAutoCalc(value: Double): String {
        if (value <= 0.0 || value.isNaN() || value.isInfinite()) return ""
        return if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            String.format(java.util.Locale.US, "%.2f", value)
        }
    }

    val initialTotalBuy = existingItem?.buyRate ?: 0.0
    val initialQty = (existingItem?.qty ?: 1.0).coerceAtLeast(0.0001)
    val initialUnitBuy = if (initialQty > 0) initialTotalBuy / initialQty else initialTotalBuy

    var unitBuyStr by remember {
        mutableStateOf(
            if (existingItem != null && initialUnitBuy > 0) formatAutoCalc(initialUnitBuy) else ""
        )
    }
    var totalBuyStr by remember {
        mutableStateOf(
            if (existingItem != null && initialTotalBuy > 0) formatAutoCalc(initialTotalBuy) else ""
        )
    }
    var sellPriceStr by remember { mutableStateOf(existingItem?.getDisplaySellPrice() ?: "") }

    var showCategoryIconPicker by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var showValidationErrors by remember { mutableStateOf(false) }
    var isDiscountMode by remember { mutableStateOf(false) }
    var mrpStr by remember { mutableStateOf("") }
    var buyDiscountStr by remember { mutableStateOf("") }
    var sellDiscountStr by remember { mutableStateOf("") }

    val nameFocusRequester = remember { FocusRequester() }
    val sellPriceFocusRequester = remember { FocusRequester() }
    val qtyFocusRequester = remember { FocusRequester() }
    val unitBuyFocusRequester = remember { FocusRequester() }
    val totalBuyFocusRequester = remember { FocusRequester() }

    // Auto-focus Item Name field when modal opens
    LaunchedEffect(Unit) {
        try {
            nameFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    // Composite Uniqueness Validation
    val isDuplicate = remember(name, category, existingItems, existingItem) {
        val trimmedName = name.trim()
        val trimmedCat = category.trim()
        if (trimmedName.isBlank()) false
        else {
            existingItems.any { itm ->
                (existingItem == null || itm.id != existingItem.id) &&
                itm.name.trim().equals(trimmedName, ignoreCase = true) &&
                itm.category.trim().equals(trimmedCat, ignoreCase = true)
            }
        }
    }

    val parsedQty = (qtyStr.toDoubleOrNull() ?: 1.0).coerceAtLeast(0.0001)
    val parsedUnitBuy = unitBuyStr.toDoubleOrNull() ?: (totalBuyStr.toDoubleOrNull()?.let { if (parsedQty > 0) it / parsedQty else 0.0 } ?: 0.0)
    val parsedSell = sellPriceStr.toDoubleOrNull() ?: 0.0
    val liveProfit = if (parsedSell > 0 && parsedUnitBuy > 0) parsedSell - parsedUnitBuy else 0.0
    val liveMarginPct = if (parsedUnitBuy > 0 && parsedSell > 0) (liveProfit / parsedUnitBuy) * 100.0 else 0.0

    val isFormValid = name.trim().isNotBlank() && !isDuplicate && (!hasMultipleVariants || variants.isNotEmpty())

    fun handleSave() {
        if (name.trim().isBlank() || isDuplicate) {
            showValidationErrors = true
            AppHaptics.performError(view)
            return
        }
        if (isSubmitting) return
        isSubmitting = true
        AppHaptics.performConfirm(view)
        focusManager.clearFocus()
        keyboardController?.hide()

        if (hasMultipleVariants && variants.isNotEmpty()) {
            val validVariants = variants.mapIndexed { idx, v ->
                val label = v.label.trim().ifBlank { "Variant ${idx + 1}" }
                v.copy(label = label, unit = v.unit.ifBlank { unit })
            }
            val firstV = validVariants.first()
            val formattedPrices = validVariants.map { it.getDisplaySellPrice() }.joinToString(" / ")
            onSave(
                name.trim(),
                category,
                firstV.unit,
                firstV.qty,
                firstV.buyRate,
                formattedPrices,
                true,
                validVariants
            )
        } else {
            val qty = (qtyStr.toDoubleOrNull() ?: 1.0).coerceAtLeast(0.0001)
            val cleanSell = sellPriceStr.replace("₹", "").replace("$", "").trim()
            val finalSellStr = if (cleanSell.isNotBlank()) cleanSell else "0"

            if (isDiscountMode) {
                val mrpVal = mrpStr.toDoubleOrNull() ?: 0.0
                val buyDiscVal = buyDiscountStr.toDoubleOrNull() ?: 0.0
                val sellDiscVal = sellDiscountStr.toDoubleOrNull() ?: 0.0
                val calculatedUnitBuy = if (mrpVal > 0) mrpVal * (1.0 - (buyDiscVal / 100.0)) else 0.0
                val calculatedSell = if (mrpVal > 0) mrpVal * (1.0 - (sellDiscVal / 100.0)) else (cleanSell.toDoubleOrNull() ?: 0.0)
                val discSellStr = if (mrpVal > 0) String.format(java.util.Locale.US, "%.2f", calculatedSell) else finalSellStr
                val finalTotalBuy = calculatedUnitBuy * qty
                onSave(name.trim(), category, unit, qty, finalTotalBuy, discSellStr, false, emptyList())
            } else {
                val totalBuy = totalBuyStr.toDoubleOrNull() ?: (unitBuyStr.toDoubleOrNull()?.times(qty) ?: 0.0)
                onSave(name.trim(), category, unit, qty, totalBuy, finalSellStr, false, emptyList())
            }
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Title + Neutral Gray Close Button (44x44 touch target)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (existingItem == null) "Add New Item" else "Edit Item",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("close_add_item")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // ============================================================
            // SECTION 1: BASIC INFO
            // ============================================================
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "BASIC INFO",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.5.sp
                )

                // 1. ITEM NAME
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Item Name *",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (showValidationErrors && name.isBlank()) DebitRed else MaterialTheme.colorScheme.onSurface
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            if (showValidationErrors && it.isNotBlank()) showValidationErrors = false
                        },
                        placeholder = { Text("e.g., Basmati Rice, Toor Dal, Sugar", style = MaterialTheme.typography.bodyMedium) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { sellPriceFocusRequester.requestFocus() }),
                        isError = (showValidationErrors && name.isBlank()) || isDuplicate,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = if ((showValidationErrors && name.isBlank()) || isDuplicate) DebitRed else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(nameFocusRequester)
                            .testTag("rate_item_name_input")
                    )

                    if (showValidationErrors && name.isBlank()) {
                        Text(
                            text = "Please enter an item name",
                            color = DebitRed,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    } else if (isDuplicate) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = DebitRed,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Item already exists in $category",
                                color = DebitRed,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 2. CATEGORY PILLS
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Category",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = category,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(
                            items = activeCategories,
                            key = { cat -> "sheet_cat_$cat" },
                            contentType = { "sheet_category_pill" }
                        ) { cat ->
                            val isSelected = cat == category
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        val prevDefault = inferDefaultUnit(category)
                                        category = cat
                                        if (!isUnitManuallySelected || unit == prevDefault) {
                                            unit = inferDefaultUnit(cat)
                                        }
                                    }
                            ) {
                                Text(
                                    text = cat,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }

                        item(
                            key = "pick_vector_icon_btn",
                            contentType = "pick_vector_icon_btn"
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { showCategoryIconPicker = true }
                            ) {
                                Text(
                                    text = "+ New",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // 3. MULTIPLE SIZES / VARIANTS OPT-IN TICKMARK
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
                    border = BorderStroke(
                        1.dp,
                        if (hasMultipleVariants) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            hasMultipleVariants = !hasMultipleVariants
                            if (hasMultipleVariants && variants.isEmpty()) {
                                val defaultBuy = unitBuyStr.toDoubleOrNull() ?: 0.0
                                val defaultSell = sellPriceStr.toDoubleOrNull() ?: 0.0
                                variants.add(
                                    com.example.data.RateVariant(
                                        label = "",
                                        unit = unit,
                                        qty = (qtyStr.toDoubleOrNull() ?: 1.0).coerceAtLeast(0.0001),
                                        buyRate = defaultBuy,
                                        sellPrice = defaultSell,
                                        sellPriceStr = if (defaultSell > 0.0) sellPriceStr else ""
                                    )
                                )
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Checkbox(
                            checked = hasMultipleVariants,
                            onCheckedChange = { checked ->
                                hasMultipleVariants = checked
                                if (checked && variants.isEmpty()) {
                                    val defaultBuy = unitBuyStr.toDoubleOrNull() ?: 0.0
                                    val defaultSell = sellPriceStr.toDoubleOrNull() ?: 0.0
                                    variants.add(
                                        com.example.data.RateVariant(
                                            label = "",
                                            unit = unit,
                                            qty = (qtyStr.toDoubleOrNull() ?: 1.0).coerceAtLeast(0.0001),
                                            buyRate = defaultBuy,
                                            sellPrice = defaultSell,
                                            sellPriceStr = if (defaultSell > 0.0) sellPriceStr else ""
                                        )
                                    )
                                }
                            },
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .size(20.dp)
                                .testTag("toggle_multiple_variants_checkbox")
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "This item has multiple sizes/variants",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "e.g. 500mL and 1ltr, or Small/Medium/Large",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 1.dp
            )

            // ============================================================
            // SECTION 2: PRICING & COST
            // ============================================================
            if (!hasMultipleVariants) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "PRICING & COST",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )

                    // Row 1: Selling Price Input (~70%) + Vertical Scroll Wheel Unit Picker (~30%)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Selling Price Field
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Selling Price ($currencySymbol per $unit)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            OutlinedTextField(
                                value = sellPriceStr,
                                onValueChange = {
                                    sellPriceStr = it
                                    if (showValidationErrors && it.isNotBlank()) showValidationErrors = false
                                },
                                placeholder = { Text("0.00", style = MaterialTheme.typography.bodyMedium) },
                                singleLine = true,
                                leadingIcon = {
                                    Text(
                                        text = currencySymbol,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 12.dp, end = 2.dp)
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { qtyFocusRequester.requestFocus() }),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(sellPriceFocusRequester)
                                    .testTag("rate_item_sell_price_input")
                            )
                        }

                        // Stock Quantity Field (#7 grouped with Unit)
                        Column(
                            modifier = Modifier.width(108.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Stock Qty ($unit)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )

                            OutlinedTextField(
                                value = qtyTextFieldValue,
                                onValueChange = { newVal ->
                                    qtyTextFieldValue = newVal
                                    qtyStr = newVal.text
                                    val parsedQ = newVal.text.toDoubleOrNull()
                                    if (parsedQ != null && parsedQ > 0) {
                                        val unitVal = unitBuyStr.toDoubleOrNull()
                                        val totalVal = totalBuyStr.toDoubleOrNull()
                                        if (unitVal != null && unitVal > 0) {
                                            totalBuyStr = formatAutoCalc(unitVal * parsedQ)
                                        } else if (totalVal != null && totalVal > 0) {
                                            unitBuyStr = formatAutoCalc(totalVal / parsedQ)
                                        }
                                    }
                                },
                                placeholder = { Text("1") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { unitBuyFocusRequester.requestFocus() }),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(qtyFocusRequester)
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused && qtyTextFieldValue.text.isNotEmpty()) {
                                            qtyTextFieldValue = qtyTextFieldValue.copy(
                                                selection = TextRange(0, qtyTextFieldValue.text.length)
                                            )
                                        }
                                    }
                                    .testTag("rate_item_qty_input")
                            )
                        }
                    }

                    // Horizontal Segmented Unit Selector (#7 - replaces cramped vertical stack)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Unit of Measure",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(
                                items = unitOptions,
                                key = { u -> "unit_seg_$u" },
                                contentType = { "unit_segment" }
                            ) { u ->
                                val isSelectedUnit = u.equals(unit, ignoreCase = true)
                                Surface(
                                    onClick = {
                                        unit = u
                                        isUnitManuallySelected = true
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelectedUnit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelectedUnit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.testTag("unit_segment_$u")
                                ) {
                                    Text(
                                        text = u,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isSelectedUnit) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelectedUnit) Color.White else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Row 2: Dedicated Purchase Cost Group (#7: Buy Rate per Unit vs Total Purchase Cost)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Purchase Cost (Buy Rate vs Total Cost)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // 1. Buy Cost per Unit (₹/Unit)
                            OutlinedTextField(
                                value = unitBuyStr,
                                onValueChange = { newVal ->
                                    unitBuyStr = newVal
                                    val parsedUnit = newVal.toDoubleOrNull()
                                    val parsedQ = (qtyStr.toDoubleOrNull() ?: 1.0).coerceAtLeast(0.0001)
                                    if (parsedUnit != null && parsedQ > 0) {
                                        totalBuyStr = formatAutoCalc(parsedUnit * parsedQ)
                                    } else if (newVal.isBlank()) {
                                        totalBuyStr = ""
                                    }
                                },
                                label = { Text("Cost per $unit ($currencySymbol)", fontSize = 11.sp) },
                                placeholder = { Text("0") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { totalBuyFocusRequester.requestFocus() }),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(unitBuyFocusRequester)
                                    .testTag("rate_item_unit_buy_price_input")
                            )

                            // 2. Total Purchase Cost (₹ for Qty)
                            OutlinedTextField(
                                value = totalBuyStr,
                                onValueChange = { newVal ->
                                    totalBuyStr = newVal
                                    val parsedTotal = newVal.toDoubleOrNull()
                                    val parsedQ = (qtyStr.toDoubleOrNull() ?: 1.0).coerceAtLeast(0.0001)
                                    if (parsedTotal != null && parsedQ > 0) {
                                        unitBuyStr = formatAutoCalc(parsedTotal / parsedQ)
                                    } else if (newVal.isBlank()) {
                                        unitBuyStr = ""
                                    }
                                },
                                label = { Text("Total Purchase Cost ($currencySymbol)", fontSize = 11.sp) },
                                placeholder = { Text("0") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(totalBuyFocusRequester)
                                    .testTag("rate_item_buy_rate_input")
                            )
                        }
                    }

                    // Live Profit, Markup & Margin Badge (#7: explicitly labels both Markup and Margin)
                    if (parsedSell > 0 && parsedUnitBuy > 0) {
                        val markupAndMarginLabel = com.example.feature.items.ItemsViewModel.formatMarkupAndMargin(parsedUnitBuy, parsedSell)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (liveProfit >= 0) CreditGreen.copy(alpha = 0.12f) else DebitRed.copy(alpha = 0.12f),
                            border = BorderStroke(
                                1.dp,
                                if (liveProfit >= 0) CreditGreen.copy(alpha = 0.35f) else DebitRed.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
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
                                            imageVector = if (liveProfit >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                            contentDescription = null,
                                            tint = if (liveProfit >= 0) CreditGreen else DebitRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "${if (liveProfit >= 0) "Profit" else "Loss"}: ${Formatters.formatCurrency(Math.abs(liveProfit), currencySymbol)}/$unit",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (liveProfit >= 0) CreditGreen else DebitRed
                                        )
                                    }

                                    Surface(
                                        shape = CircleShape,
                                        color = if (liveProfit >= 0) CreditGreen else DebitRed
                                    ) {
                                        Text(
                                            text = markupAndMarginLabel,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // MULTI-VARIANT REPEATABLE BLOCK STRUCTURE
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SIZES & VARIANTS (${variants.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.5.sp
                        )
                    }

                    variants.forEachIndexed { vIndex, v ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Variant Header Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primary
                                        ) {
                                            Text(
                                                text = "${vIndex + 1}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Text(
                                            text = "Size / Variant Block",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    if (variants.size > 1) {
                                        IconButton(
                                            onClick = { variants.removeAt(vIndex) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove Variant",
                                                tint = DebitRed,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                // Row 1: Variant Label (Free text) + Selling Price
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = v.label,
                                        onValueChange = { newLabel ->
                                            variants[vIndex] = v.copy(label = newLabel)
                                        },
                                        label = { Text("Size Label") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                        ),
                                        modifier = Modifier
                                            .weight(1.1f)
                                            .testTag("variant_label_input_$vIndex")
                                    )

                                    OutlinedTextField(
                                        value = v.sellPriceStr,
                                        onValueChange = { newSell ->
                                            val cleanSell = newSell.replace("₹", "").replace("$", "").trim()
                                            val sellNum = cleanSell.toDoubleOrNull() ?: 0.0
                                            variants[vIndex] = v.copy(sellPrice = sellNum, sellPriceStr = cleanSell)
                                        },
                                        label = { Text("Sell ($currencySymbol)") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                        ),
                                        modifier = Modifier
                                            .weight(0.9f)
                                            .testTag("variant_sell_price_input_$vIndex")
                                    )
                                }

                                // Row 2: Purchase & Inventory Cost inline row (Qty | Buy | Total Buy) for variant
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val vQtyStr = if (v.qty % 1.0 == 0.0) v.qty.toLong().toString() else v.qty.toString()
                                    val vTotalBuy = v.buyRate
                                    val vUnitBuy = if (v.qty > 0) vTotalBuy / v.qty else vTotalBuy
                                    val vUnitBuyStr = if (vUnitBuy > 0) formatAutoCalc(vUnitBuy) else ""
                                    val vTotalBuyStr = if (vTotalBuy > 0) formatAutoCalc(vTotalBuy) else ""

                                    OutlinedTextField(
                                        value = vQtyStr,
                                        onValueChange = { newQtyStr ->
                                            val parsedQ = (newQtyStr.toDoubleOrNull() ?: 1.0).coerceAtLeast(0.0001)
                                            variants[vIndex] = v.copy(qty = parsedQ)
                                        },
                                        label = { Text("Qty", fontSize = 11.sp) },
                                        placeholder = { Text("1") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                        ),
                                        modifier = Modifier.weight(0.8f)
                                    )

                                    OutlinedTextField(
                                        value = vUnitBuyStr,
                                        onValueChange = { newBuyStr ->
                                            val parsedBuy = newBuyStr.toDoubleOrNull() ?: 0.0
                                            val total = parsedBuy * v.qty
                                            variants[vIndex] = v.copy(buyRate = total)
                                        },
                                        label = { Text("Buy ($currencySymbol/Unit)", fontSize = 11.sp) },
                                        placeholder = { Text("0.00") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                        ),
                                        modifier = Modifier.weight(1.1f)
                                    )

                                    OutlinedTextField(
                                        value = vTotalBuyStr,
                                        onValueChange = { newTotalStr ->
                                            val parsedTotal = newTotalStr.toDoubleOrNull() ?: 0.0
                                            variants[vIndex] = v.copy(buyRate = parsedTotal)
                                        },
                                        label = { Text("Total Buy ($currencySymbol)", fontSize = 11.sp) },
                                        placeholder = { Text("0.00") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                        ),
                                        modifier = Modifier.weight(1.1f)
                                    )
                                }
                            }
                        }
                    }

                    // Add Another Size button
                    OutlinedButton(
                        onClick = {
                            variants.add(
                                com.example.data.RateVariant(
                                    label = "",
                                    unit = unit,
                                    qty = 1.0,
                                    buyRate = 0.0,
                                    sellPrice = 0.0,
                                    sellPriceStr = ""
                                )
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_another_size_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("+ Add Another Size", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ============================================================
            // SECTION 4: BOTTOM ACTION BAR + INLINE DISABLED REASON (#37)
            // ============================================================
            if (!isFormValid) {
                Text(
                    text = if (name.isBlank()) "Enter an item name to enable Save" else "Enter a valid selling price",
                    style = MaterialTheme.typography.labelSmall,
                    color = DebitRed,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ghost / TextButton Cancel (matches iOS convention)
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Solid Full-Accent Primary Action Button (Disabled when name or sell price is invalid)
                Button(
                    onClick = { handleSave() },
                    enabled = isFormValid && !isSubmitting,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .padding(start = 16.dp)
                        .weight(1f)
                        .testTag("save_rate_item_button")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (existingItem == null) "Save & Add" else "Update Item",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }

    // Inline Category Vector Icon Search & Picker Dialog
    if (showCategoryIconPicker) {
        CategoryIconPickerDialog(
            onDismiss = { showCategoryIconPicker = false },
            onConfirm = { catName, iconKey, iconUrl ->
                category = catName
                onAddCategoryWithIcon?.invoke(catName, iconKey, iconUrl)
                showCategoryIconPicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceHistorySheet(
    item: RateItemEntity,
    initialVariantId: String? = null,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onUpdateHistory: (RateItemEntity) -> Unit = {}
) {
    val context = LocalContext.current
    val variants = remember(item) { item.getVariants() }
    val isMultiVariant = item.hasVariants()

    android.util.Log.d("VARIANT_TRACE", "Hop 6 - Item Detail Modal: item='${item.name}', isMultiVariant=$isMultiVariant, variantsSize=${variants.size}, selectedVariantId='$initialVariantId'")

    var selectedVariantId by rememberSaveable(item.id, initialVariantId) {
        mutableStateOf(
            initialVariantId?.takeIf { id -> variants.any { it.id == id } }
                ?: variants.firstOrNull()?.id
                ?: ""
        )
    }

    val selectedVariant = remember(variants, selectedVariantId) {
        variants.firstOrNull { it.id == selectedVariantId } ?: variants.firstOrNull()
    }

    val activeHistoryJson = remember(item, selectedVariant, isMultiVariant) {
        if (isMultiVariant && selectedVariant != null) {
            if (selectedVariant.priceHistoryJson.isNotBlank() && selectedVariant.priceHistoryJson != "[]") {
                selectedVariant.priceHistoryJson
            } else {
                val initialPoint = PricePoint(
                    date = if (item.lastUpdated > 0) item.lastUpdated else System.currentTimeMillis(),
                    buyRate = selectedVariant.buyRate,
                    sellPrice = selectedVariant.sellPrice,
                    sellPriceStr = selectedVariant.sellPriceStr
                )
                encodePriceHistory(listOf(initialPoint))
            }
        } else {
            if (item.priceHistoryJson.isNotBlank() && item.priceHistoryJson != "[]") {
                item.priceHistoryJson
            } else {
                val initialPoint = PricePoint(
                    date = if (item.lastUpdated > 0) item.lastUpdated else System.currentTimeMillis(),
                    buyRate = item.buyRate,
                    sellPrice = item.sellPrice,
                    sellPriceStr = item.sellPriceStr
                )
                encodePriceHistory(listOf(initialPoint))
            }
        }
    }

    var historyList by remember(activeHistoryJson) {
        val raw = parsePriceHistory(activeHistoryJson)
        // Filter out consecutive duplicate/unchanged price points (#36)
        val deduplicated = raw.fold(emptyList<PricePoint>()) { acc, point ->
            val prev = acc.lastOrNull()
            if (prev != null &&
                Math.abs(prev.buyRate - point.buyRate) < 0.005 &&
                Math.abs(prev.sellPrice - point.sellPrice) < 0.005 &&
                prev.sellPriceStr.trim() == point.sellPriceStr.trim()
            ) {
                acc
            } else {
                acc + point
            }
        }
        mutableStateOf(deduplicated)
    }

    // Guarantee starting 1st entry is logged and saved if empty
    LaunchedEffect(item.id, selectedVariantId, historyList.isEmpty()) {
        if (historyList.isEmpty()) {
            val activeBuy = selectedVariant?.buyRate ?: item.buyRate
            val activeSell = selectedVariant?.sellPrice ?: item.sellPrice
            val activeSellStr = selectedVariant?.sellPriceStr ?: item.sellPriceStr
            val initialPoint = PricePoint(
                date = if (item.lastUpdated > 0) item.lastUpdated else System.currentTimeMillis(),
                buyRate = activeBuy,
                sellPrice = activeSell,
                sellPriceStr = activeSellStr
            )
            val initialList = listOf(initialPoint)
            historyList = initialList
            if (isMultiVariant && selectedVariant != null) {
                val updatedV = selectedVariant.copy(priceHistoryJson = encodePriceHistory(initialList))
                val newVariants = variants.map { if (it.id == updatedV.id) updatedV else it }
                onUpdateHistory(item.copy(variantsJson = RateItemEntity.encodeVariants(newVariants)))
            } else {
                onUpdateHistory(item.copy(priceHistoryJson = encodePriceHistory(initialList)))
            }
        }
    }

    var showAddPricePointDialog by remember { mutableStateOf(false) }
    var editingPricePointIndex by remember { mutableStateOf<Int?>(null) }
    var isGraphExpanded by remember(item.id, historyList.size) { mutableStateOf(historyList.size >= 2) }
    val haptic = LocalHapticFeedback.current
    val isDark = isAppInDarkTheme()

    val animatedBgColor by animateColorAsState(
        targetValue = if (isGraphExpanded) Color(0xFF379358) else (if (isDark) Color(0xFF1B2E21) else Color(0xFFEAF6ED)),
        animationSpec = tween(220),
        label = "graphToggleBg"
    )
    val animatedIconColor by animateColorAsState(
        targetValue = if (isGraphExpanded) Color.White else Color(0xFF379358),
        animationSpec = tween(220),
        label = "graphToggleIcon"
    )
    val buttonScale by animateFloatAsState(
        targetValue = if (isGraphExpanded) 1.05f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "graphToggleScale"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { false }
        ),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        scrimColor = Color.Black.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .padding(20.dp)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val currentSellDisplay = if (isMultiVariant && selectedVariant != null) {
                        selectedVariant.getDisplaySellPrice()
                    } else {
                        item.getDisplaySellPrice()
                    }
                    Text(
                        text = "Current Sell: $currencySymbol$currentSellDisplay",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Circular Graph Toggle Button
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            isGraphExpanded = !isGraphExpanded
                        },
                        shape = CircleShape,
                        color = animatedBgColor,
                        border = BorderStroke(
                            1.dp,
                            if (isGraphExpanded) Color(0xFF379358) else Color(0xFF379358).copy(alpha = 0.25f)
                        ),
                        shadowElevation = if (isGraphExpanded) 3.dp else 1.dp,
                        modifier = Modifier
                            .size(42.dp)
                            .graphicsLayer {
                                scaleX = buttonScale
                                scaleY = buttonScale
                            }
                            .testTag("toggle_price_history_graph_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.ShowChart,
                                contentDescription = if (isGraphExpanded) "Collapse price history graph" else "Expand price history graph",
                                tint = animatedIconColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("close_price_history_sheet_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Centered Horizontal Variant Pill Selector (ONLY when 2+ variants exist)
            if (isMultiVariant) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    variants.forEach { v ->
                        val isSelected = (v.id == selectedVariant?.id)
                        val pillBg = if (isSelected) StatusEmerald else (if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7))
                        val pillText = if (isSelected) Color.White else (if (isDark) Color(0xFFA1A1AA) else Color(0xFF6B7280))
                        val pillBorder = if (isSelected) null else BorderStroke(1.dp, if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E5EA))

                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedVariantId = v.id
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = pillBg,
                            border = pillBorder,
                            shadowElevation = if (isSelected) 2.dp else 0.dp,
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .widthIn(max = 140.dp)
                                .testTag("variant_pill_${v.id}")
                        ) {
                            Text(
                                text = v.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = pillText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // Collapsible Price History Graph (Red = Buy, Green = Sell)
            AnimatedVisibility(
                visible = isGraphExpanded,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(animationSpec = tween(220)),
                exit = shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) + fadeOut(animationSpec = tween(180))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FluentPriceHistoryChart(
                        history = historyList,
                        currencySymbol = currencySymbol
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "History (${historyList.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Button(
                    onClick = { showAddPricePointDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Price", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                }
            }

            if (historyList.isEmpty()) {
                // Empty block
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    itemsIndexed(
                        items = historyList,
                        key = { _, point -> "${point.date}_${point.sellPrice}_${point.buyRate}" },
                        contentType = { _, _ -> "price_history_row" }
                    ) { index, point ->

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = Formatters.formatDate(point.date),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                    }
                                    Text(
                                        text = Formatters.formatTime(point.date),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    val sellFormatted = point.sellPrice.takeIf { it > 0 }?.let {
                                        Formatters.formatCurrency(it, currencySymbol)
                                    } ?: "$currencySymbol${point.getDisplaySellPrice()}"
                                    Text(
                                        text = "SELL: $sellFormatted",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Buy Cost: ${Formatters.formatCurrency(point.buyRate, currencySymbol)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Row {
                                    IconButton(
                                        onClick = { editingPricePointIndex = index },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit History Entry", modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            val newList = historyList.toMutableList()
                                            newList.removeAt(index)
                                            historyList = newList
                                            val updatedJson = encodePriceHistory(newList)
                                            val latest = newList.firstOrNull()
                                            if (isMultiVariant && selectedVariant != null) {
                                                val updatedV = selectedVariant.copy(
                                                    priceHistoryJson = updatedJson,
                                                    buyRate = latest?.buyRate ?: selectedVariant.buyRate,
                                                    sellPrice = latest?.sellPrice ?: selectedVariant.sellPrice,
                                                    sellPriceStr = latest?.sellPriceStr ?: selectedVariant.sellPriceStr
                                                )
                                                val newVariants = variants.map { if (it.id == updatedV.id) updatedV else it }
                                                onUpdateHistory(item.copy(variantsJson = RateItemEntity.encodeVariants(newVariants)))
                                            } else {
                                                val updatedItem = item.copy(
                                                    priceHistoryJson = updatedJson,
                                                    buyRate = latest?.buyRate ?: item.buyRate,
                                                    sellPrice = latest?.sellPrice ?: item.sellPrice,
                                                    sellPriceStr = latest?.sellPriceStr ?: item.sellPriceStr
                                                )
                                                onUpdateHistory(updatedItem)
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Entry", tint = DebitRed, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

        }
    }

    // Add Past Price History Dialog
    if (showAddPricePointDialog) {
        val dialogVariants = remember(item) { item.getVariants() }
        HistoricalPriceRecordDialog(
            title = "Add Price Record",
            itemName = item.name,
            currencySymbol = currencySymbol,
            variants = dialogVariants,
            initialBuyRate = 0.0,
            initialSellPriceStr = "",
            initialTimestamp = System.currentTimeMillis(),
            onDismiss = { showAddPricePointDialog = false },
            onSave = { timestamp, buyNum, sellStr ->
                val sellNum = sellStr.toDoubleOrNull() ?: 0.0
                val newPoint = PricePoint(date = timestamp, buyRate = buyNum, sellPrice = sellNum, sellPriceStr = sellStr)
                val newList = (historyList + newPoint)
                    .distinctBy { (it.date / 1000) to (it.sellPriceStr.ifBlank { it.sellPrice.toString() }) }
                    .sortedByDescending { it.date }
                historyList = newList
                val updatedJson = encodePriceHistory(newList)
                val latest = newList.firstOrNull()
                if (isMultiVariant && selectedVariant != null) {
                    val updatedV = selectedVariant.copy(
                        priceHistoryJson = updatedJson,
                        buyRate = latest?.buyRate ?: selectedVariant.buyRate,
                        sellPrice = latest?.sellPrice ?: selectedVariant.sellPrice,
                        sellPriceStr = latest?.sellPriceStr ?: selectedVariant.sellPriceStr
                    )
                    val newVariants = variants.map { if (it.id == updatedV.id) updatedV else it }
                    onUpdateHistory(item.copy(variantsJson = RateItemEntity.encodeVariants(newVariants)))
                } else {
                    val updatedItem = item.copy(
                        priceHistoryJson = updatedJson,
                        buyRate = latest?.buyRate ?: item.buyRate,
                        sellPrice = latest?.sellPrice ?: item.sellPrice,
                        sellPriceStr = latest?.sellPriceStr ?: item.sellPriceStr
                    )
                    onUpdateHistory(updatedItem)
                }
                showAddPricePointDialog = false
            },
            onSaveVariantRate = { variantId, timestamp, buyNum, sellStr ->
                val sellNum = sellStr.toDoubleOrNull() ?: 0.0
                val updatedVariants = dialogVariants.map { v ->
                    if (v.id == variantId) v.copy(buyRate = buyNum, sellPrice = sellNum, sellPriceStr = sellStr)
                    else v
                }
                val updatedVariantsJson = RateItemEntity.encodeVariants(updatedVariants)
                val newPoint = PricePoint(date = timestamp, buyRate = buyNum, sellPrice = sellNum, sellPriceStr = sellStr)
                val newList = (historyList + newPoint)
                    .distinctBy { (it.date / 1000) to (it.sellPriceStr.ifBlank { it.sellPrice.toString() }) }
                    .sortedByDescending { it.date }
                historyList = newList
                val historyJson = encodePriceHistory(newList)
                val updatedItem = item.copy(
                    variantsJson = updatedVariantsJson,
                    hasMultipleVariants = true,
                    priceHistoryJson = historyJson
                )
                onUpdateHistory(updatedItem)
                showAddPricePointDialog = false
            }
        )
    }

    // Edit Existing Price Point Dialog
    if (editingPricePointIndex != null) {
        val targetPoint = historyList[editingPricePointIndex!!]
        val dialogVariants = remember(item) { item.getVariants() }
        HistoricalPriceRecordDialog(
            title = "Edit Price Record",
            itemName = item.name,
            currencySymbol = currencySymbol,
            variants = dialogVariants,
            initialBuyRate = targetPoint.buyRate,
            initialSellPriceStr = targetPoint.getDisplaySellPrice(),
            initialTimestamp = targetPoint.date,
            onDismiss = { editingPricePointIndex = null },
            onSave = { timestamp, buyNum, sellStr ->
                val sellNum = sellStr.toDoubleOrNull() ?: 0.0
                val updatedPoint = PricePoint(date = timestamp, buyRate = buyNum, sellPrice = sellNum, sellPriceStr = sellStr)
                val newList = historyList.toMutableList()
                newList[editingPricePointIndex!!] = updatedPoint
                val sortedList = newList.sortedByDescending { it.date }
                historyList = sortedList
                val updatedJson = encodePriceHistory(sortedList)
                val latest = sortedList.firstOrNull()
                if (isMultiVariant && selectedVariant != null) {
                    val updatedV = selectedVariant.copy(
                        priceHistoryJson = updatedJson,
                        buyRate = latest?.buyRate ?: selectedVariant.buyRate,
                        sellPrice = latest?.sellPrice ?: selectedVariant.sellPrice,
                        sellPriceStr = latest?.sellPriceStr ?: selectedVariant.sellPriceStr
                    )
                    val newVariants = variants.map { if (it.id == updatedV.id) updatedV else it }
                    onUpdateHistory(item.copy(variantsJson = RateItemEntity.encodeVariants(newVariants)))
                } else {
                    val updatedItem = item.copy(
                        priceHistoryJson = updatedJson,
                        buyRate = latest?.buyRate ?: item.buyRate,
                        sellPrice = latest?.sellPrice ?: item.sellPrice,
                        sellPriceStr = latest?.sellPriceStr ?: item.sellPriceStr
                    )
                    onUpdateHistory(updatedItem)
                }
                editingPricePointIndex = null
            },
            onSaveVariantRate = { variantId, timestamp, buyNum, sellStr ->
                val sellNum = sellStr.toDoubleOrNull() ?: 0.0
                val updatedVariants = dialogVariants.map { v ->
                    if (v.id == variantId) v.copy(buyRate = buyNum, sellPrice = sellNum, sellPriceStr = sellStr)
                    else v
                }
                val updatedVariantsJson = RateItemEntity.encodeVariants(updatedVariants)
                val updatedPoint = PricePoint(date = timestamp, buyRate = buyNum, sellPrice = sellNum, sellPriceStr = sellStr)
                val newList = historyList.toMutableList()
                if (editingPricePointIndex!! < newList.size) {
                    newList[editingPricePointIndex!!] = updatedPoint
                }
                val sortedList = newList.sortedByDescending { it.date }
                historyList = sortedList
                val historyJson = encodePriceHistory(sortedList)
                val updatedItem = item.copy(
                    variantsJson = updatedVariantsJson,
                    hasMultipleVariants = true,
                    priceHistoryJson = historyJson
                )
                onUpdateHistory(updatedItem)
                editingPricePointIndex = null
            }
        )
    }
}

@Composable
fun VariantPricingTable(
    variants: List<RateVariant>,
    currencySymbol: String,
    modifier: Modifier = Modifier,
    selectedVariantId: String? = null,
    onVariantSelect: ((RateVariant) -> Unit)? = null
) {
    val isDark = isAppInDarkTheme()
    val tableBorderColor = if (isDark) Color(0xFF3A3A3C) else Color(0xFFE3EFE6)
    val headerBgColor = if (isDark) Color(0xFF252528) else Color(0xFFF2F8F4)
    val rowBgColor = if (isDark) Color(0xFF1C1C1E) else Color.White
    val selectedRowBgColor = if (isDark) StatusEmerald.copy(alpha = 0.25f) else StatusEmerald.copy(alpha = 0.12f)
    val textPrimaryColor = if (isDark) Color.White else Color(0xFF1C1C1E)
    val textMutedColor = if (isDark) Color(0xFFA1A1AA) else Color(0xFF6B7280)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = rowBgColor,
        border = BorderStroke(1.dp, tableBorderColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Table Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBgColor)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "VARIANT / SIZE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = textMutedColor,
                    modifier = Modifier.weight(1.2f)
                )
                Text(
                    text = "BUY COST",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = textMutedColor,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1.0f)
                )
                Text(
                    text = "SELL PRICE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = textMutedColor,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1.0f)
                )
            }

            HorizontalDivider(color = tableBorderColor, thickness = 1.dp)

            // Table Body Rows
            variants.forEachIndexed { index, variant ->
                val isSelected = selectedVariantId == variant.id
                val currentBg = if (isSelected) selectedRowBgColor else rowBgColor

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(currentBg)
                        .then(
                            if (onVariantSelect != null) {
                                Modifier.clickable { onVariantSelect(variant) }
                            } else Modifier
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Variant Label
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = StatusEmerald.copy(alpha = if (isSelected) 0.8f else 0.25f),
                            modifier = Modifier.size(6.dp)
                        ) {}
                        Text(
                            text = variant.label.ifBlank { "Standard" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            color = textPrimaryColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Buy Cost
                    val unitSuffix = if (variant.unit.isNotBlank()) "/${variant.unit.lowercase()}" else ""
                    val buyText = if (variant.buyRate > 0) {
                        val eff = if (variant.qty > 0) variant.buyRate / variant.qty else variant.buyRate
                        if (eff % 1.0 == 0.0) "$currencySymbol${eff.toLong()}$unitSuffix"
                        else "$currencySymbol${String.format(java.util.Locale.US, "%.2f", eff)}$unitSuffix"
                    } else "—"

                    Text(
                        text = buyText,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        color = textMutedColor,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1.0f)
                    )

                    // Sell Price
                    Text(
                        text = "$currencySymbol${variant.getDisplaySellPrice()}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.Bold,
                        color = StatusEmerald,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1.0f)
                    )
                }

                if (index < variants.size - 1) {
                    HorizontalDivider(color = tableBorderColor.copy(alpha = 0.5f), thickness = 0.5.dp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoricalPriceRecordDialog(
    title: String = "Add Price Record",
    itemName: String,
    currencySymbol: String,
    variants: List<RateVariant> = emptyList(),
    initialBuyRate: Double = 0.0,
    initialSellPriceStr: String = "",
    initialTimestamp: Long = System.currentTimeMillis(),
    onDismiss: () -> Unit,
    onSave: (timestamp: Long, buyRate: Double, sellPriceStr: String) -> Unit,
    onSaveVariantRate: (variantId: String, timestamp: Long, buyRate: Double, sellPriceStr: String) -> Unit = { _, _, _, _ -> }
) {
    val context = LocalContext.current
    val view = LocalView.current
    val focusManager = LocalFocusManager.current

    var selectedVariantId by remember(variants) { mutableStateOf(variants.firstOrNull()?.id ?: "") }
    val currentVariant = remember(selectedVariantId, variants) { variants.find { it.id == selectedVariantId } ?: variants.firstOrNull() }

    var buyStr by remember(currentVariant) {
        mutableStateOf(
            if (currentVariant != null) (if (currentVariant.buyRate > 0) currentVariant.buyRate.toString() else "")
            else (if (initialBuyRate > 0) initialBuyRate.toString() else "")
        )
    }
    var sellStr by remember(currentVariant) {
        mutableStateOf(
            currentVariant?.getDisplaySellPrice() ?: initialSellPriceStr
        )
    }
    var selectedTimestamp by remember { mutableLongStateOf(initialTimestamp) }
    var isSubmitting by remember { mutableStateOf(false) }

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }

    val isToday = remember(selectedTimestamp) {
        val cal1 = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
        val cal2 = Calendar.getInstance()
        cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
        cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    val formattedDateText = remember(selectedTimestamp, isToday) {
        if (isToday) "Today, ${Formatters.formatDate(selectedTimestamp)}"
        else Formatters.formatDate(selectedTimestamp)
    }

    val isFormValid = buyStr.isNotBlank() || sellStr.isNotBlank()
    val isDark = isAppInDarkTheme()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                ) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = if (isDark) Color(0xFF1C1C1E) else Color.White,
                    tonalElevation = 6.dp,
                    shadowElevation = 16.dp,
                    border = BorderStroke(1.dp, if (isDark) Color(0xFF2C2C2E) else Color(0xFFE3EFE6)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { /* prevent dismiss */ }
                        .imePadding()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header: Title + Subtitle + 44x44 Touch Target Close Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = if (title.contains("Edit", ignoreCase = true)) "Edit Price Record" else "Add Price Record",
                                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDark) Color.White else Color(0xFF1C1C1E),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = itemName,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                    color = if (isDark) Color(0xFFA1A1AA) else Color(0xFF6B7280),
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F4F3))
                                    .testTag("close_price_record_dialog")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = if (isDark) Color(0xFFD1D5DB) else Color(0xFF4B5563),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Multi-Variant Pricing Table (if item has variants)
                        if (variants.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "MULTI-VARIANT PRICING TABLE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color(0xFFA1A1AA) else Color(0xFF6B7280),
                                    letterSpacing = 0.5.sp
                                )
                                VariantPricingTable(
                                    variants = variants,
                                    currencySymbol = currencySymbol,
                                    selectedVariantId = selectedVariantId,
                                    onVariantSelect = { v ->
                                        selectedVariantId = v.id
                                        buyStr = if (v.buyRate > 0) v.buyRate.toString() else ""
                                        sellStr = v.getDisplaySellPrice()
                                    }
                                )
                            }
                        }

                        // Unified Tappable iOS-Style Date Row
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "RECORD DATE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color(0xFFA1A1AA) else Color(0xFF6B7280),
                                letterSpacing = 0.5.sp
                            )

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFFAFCF9),
                                border = BorderStroke(1.dp, if (isDark) Color(0xFF3A3A3C) else Color(0xFFE3EFE6)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        AppHaptics.performTap(view)
                                        val cal = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
                                        DatePickerDialog(
                                            context,
                                            if (isDark) android.R.style.Theme_DeviceDefault_Dialog else android.R.style.Theme_DeviceDefault_Light_Dialog,
                                            { _, y, m, d ->
                                                val newCal = Calendar.getInstance().apply {
                                                    set(Calendar.YEAR, y)
                                                    set(Calendar.MONTH, m)
                                                    set(Calendar.DAY_OF_MONTH, d)
                                                    set(Calendar.HOUR_OF_DAY, 12)
                                                    set(Calendar.MINUTE, 0)
                                                    set(Calendar.SECOND, 0)
                                                    set(Calendar.MILLISECOND, 0)
                                                }
                                                selectedTimestamp = newCal.timeInMillis
                                            },
                                            cal.get(Calendar.YEAR),
                                            cal.get(Calendar.MONTH),
                                            cal.get(Calendar.DAY_OF_MONTH)
                                        ).show()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.CalendarToday,
                                            contentDescription = null,
                                            tint = StatusEmerald,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = formattedDateText,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isDark) Color.White else Color(0xFF1C1C1E)
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowRight,
                                        contentDescription = "Select date",
                                        tint = if (isDark) Color(0xFF8E8E93) else Color(0xFF9CA3AF),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Input Fields: Buy Cost & Selling Price
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            // 1. Buy Cost
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = if (currentVariant != null) "Buy Cost for ${currentVariant.label} ($currencySymbol)" else "Buy Cost ($currencySymbol)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color.White else Color(0xFF1C1C1E)
                                )
                                OutlinedTextField(
                                    value = buyStr,
                                    onValueChange = { buyStr = it },
                                    placeholder = { Text("0.00", style = MaterialTheme.typography.bodyMedium, color = if (isDark) Color(0xFF71717A) else Color(0xFF9CA3AF)) },
                                    singleLine = true,
                                    leadingIcon = {
                                        Text(
                                            text = currencySymbol,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 16.sp,
                                            color = StatusEmerald,
                                            modifier = Modifier.padding(start = 12.dp, end = 2.dp)
                                        )
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = StatusEmerald,
                                        unfocusedBorderColor = if (isDark) Color(0xFF3A3A3C) else Color(0xFFE3EFE6),
                                        focusedContainerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFFAFCF9),
                                        unfocusedContainerColor = if (isDark) Color(0xFF2C2C2E) else Color.White,
                                        focusedTextColor = if (isDark) Color.White else Color(0xFF1C1C1E),
                                        unfocusedTextColor = if (isDark) Color.White else Color(0xFF1C1C1E)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("historical_buy_cost_input")
                                )
                            }

                            // 2. Selling Price
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = if (currentVariant != null) "Selling Price for ${currentVariant.label} ($currencySymbol)" else "Selling Price ($currencySymbol)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color.White else Color(0xFF1C1C1E)
                                )
                                OutlinedTextField(
                                    value = sellStr,
                                    onValueChange = { sellStr = it },
                                    placeholder = { Text("0.00", style = MaterialTheme.typography.bodyMedium, color = if (isDark) Color(0xFF71717A) else Color(0xFF9CA3AF)) },
                                    singleLine = true,
                                    leadingIcon = {
                                        Text(
                                            text = currencySymbol,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 16.sp,
                                            color = StatusEmerald,
                                            modifier = Modifier.padding(start = 12.dp, end = 2.dp)
                                        )
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = StatusEmerald,
                                        unfocusedBorderColor = if (isDark) Color(0xFF3A3A3C) else Color(0xFFE3EFE6),
                                        focusedContainerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFFAFCF9),
                                        unfocusedContainerColor = if (isDark) Color(0xFF2C2C2E) else Color.White,
                                        focusedTextColor = if (isDark) Color.White else Color(0xFF1C1C1E),
                                        unfocusedTextColor = if (isDark) Color.White else Color(0xFF1C1C1E)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("historical_sell_price_input")
                                )
                            }
                        }

                        // Action Buttons (Cancel + Primary Action)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text(
                                    text = "Cancel",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isDark) Color(0xFFA1A1AA) else Color(0xFF6B7280)
                                )
                            }

                            Button(
                                enabled = isFormValid && !isSubmitting,
                                onClick = {
                                    if (!isSubmitting) {
                                        isSubmitting = true
                                        AppHaptics.performConfirm(view)
                                        val buyNum = buyStr.toDoubleOrNull() ?: 0.0
                                        if (variants.isNotEmpty() && currentVariant != null) {
                                            onSaveVariantRate(currentVariant.id, selectedTimestamp, buyNum, sellStr)
                                        } else {
                                            onSave(selectedTimestamp, buyNum, sellStr)
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = StatusEmerald,
                                    contentColor = Color.White,
                                    disabledContainerColor = if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E7EB),
                                    disabledContentColor = if (isDark) Color(0xFF6B7280) else Color(0xFF9CA3AF)
                                ),
                                modifier = Modifier
                                    .height(48.dp)
                                    .weight(1f)
                                    .padding(start = 16.dp)
                                    .testTag("save_price_record_button")
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (title.contains("Edit", ignoreCase = true)) "Save Changes" else "Add Entry",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
