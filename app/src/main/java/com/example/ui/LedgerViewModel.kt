package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.auth.AuthResult
import com.example.auth.AuthUser
import com.example.auth.FirebaseAuthManager
import com.example.data.*
import com.example.sync.FirestoreSyncManager
import com.example.sync.SyncStatus
import com.example.sync.SyncUiState
import com.example.utils.ExportUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.Stable
import com.example.ui.i18n.AppLanguage
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID

@Stable
data class LedgerUiState(
    val summary: BusinessSummary = BusinessSummary(),
    val customers: List<CustomerWithBalance> = emptyList(),
    val selectedCustomerWithBalance: CustomerWithBalance? = null,
    val invoices: List<InvoiceEntity> = emptyList(),
    val allTransactions: List<TransactionEntity> = emptyList(),
    val auditLogs: List<AuditLogEntity> = emptyList(),
    val rateItems: List<RateItemEntity> = emptyList(),
    val rateCategories: List<String> = listOf("ALL", "Pulses & Kirana", "Dry Fruit", "Rice", "Surf & Soap", "Tobacco", "Tea", "Namkeen", "Oil & Ghee", "Biscuit", "Others"),
    val selectedRateCategory: String = "ALL",
    val rateSearchQuery: String = "",
    val rateMinPriceFilter: Double = 0.0,
    val rateMaxPriceFilter: Double = 1000.0,
    val rateMaxPriceLimit: Double = 1000.0,
    val pinnedCustomerIds: Set<String> = emptySet(),
    val currencySymbol: String = "₹",
    val businessName: String = "My Business Ledger",
    val businessPhone: String = "",
    val businessAddress: String = "",
    val upiId: String = "merchant@upi",
    val businessGstin: String = "",
    val isPasscodeEnabled: Boolean = false,
    val isUnlocked: Boolean = true,
    val customerSearchQuery: String = "",
    val customerFilter: String = "CUSTOMER", // CUSTOMER, SUPPLIER
    val invoiceFilter: String = "ALL", // ALL, UNPAID, OVERDUE, PAID
    val userMessage: String? = null,
    val failedPinAttempts: Int = 0,
    val isLockedOut: Boolean = false,
    val targetRateItemIdToOpen: String? = null,
    val authUser: AuthUser? = null,
    val syncState: SyncUiState = SyncUiState(),
    val webClientId: String = "",
    val isCheckingAuth: Boolean = true,
    val isSigningIn: Boolean = false,
    val isDarkMode: Boolean = false,
    val categoryIconMap: Map<String, String> = emptyMap(),
    val selectedLanguage: AppLanguage = AppLanguage.ENGLISH,
    val availableUnits: List<String> = AppPreferencesManager.defaultSystemUnits,
    val hiddenCategories: Set<String> = emptySet()
)

sealed interface LedgerEvent {
    data class OnSearchQueryChange(val query: String) : LedgerEvent
    data class OnCustomerFilterChange(val filter: String) : LedgerEvent
    data class OnSelectCustomer(val id: String?) : LedgerEvent
    data class OnAddCustomer(val name: String, val phone: String, val businessName: String, val partyType: String, val notes: String) : LedgerEvent
    data class OnUpdateCustomer(val customer: CustomerEntity) : LedgerEvent
    data class OnDeleteCustomer(val id: String) : LedgerEvent
    data class OnDeleteSelectedCustomers(val ids: Set<String>) : LedgerEvent
    data class OnTogglePinCustomer(val id: String) : LedgerEvent
    data class OnAddTransaction(val customerId: String, val amount: Double, val type: String, val description: String, val paymentMethod: String, val invoiceRef: String, val dateTimestamp: Long) : LedgerEvent
    data class OnUpdateTransaction(val transaction: TransactionEntity) : LedgerEvent
    data class OnDeleteTransaction(val transaction: TransactionEntity) : LedgerEvent
    data class OnDeleteSelectedTransactions(val transactions: List<TransactionEntity>) : LedgerEvent
    data class OnInvoiceFilterChange(val filter: String) : LedgerEvent
    data class OnCreateInvoice(val customerId: String, val customerName: String, val items: List<InvoiceItem>, val dueDays: Int, val taxPercent: Double, val discountAmount: Double, val notes: String, val paymentMode: String) : LedgerEvent
    data class OnRecordInvoicePayment(val invoice: InvoiceEntity, val paymentAmount: Double, val paymentMethod: String) : LedgerEvent
    data class OnUpdateInvoice(val invoice: InvoiceEntity) : LedgerEvent
    data class OnDeleteInvoice(val invoice: InvoiceEntity) : LedgerEvent
    data class OnDeleteSelectedInvoices(val invoices: List<InvoiceEntity>) : LedgerEvent
    data class OnRateCategoryChange(val category: String) : LedgerEvent
    data class OnRateSearchQueryChange(val query: String) : LedgerEvent
    data class OnRateMinMaxFilterChange(val minPrice: Double, val maxPrice: Double) : LedgerEvent
    data class OnAddRateItem(val name: String, val category: String, val unit: String, val qty: Double, val buyRate: Double, val sellPrice: Double, val sellPriceStr: String, val hasMultipleVariants: Boolean = false, val variants: List<RateVariant> = emptyList()) : LedgerEvent
    data class OnUpdateRateItem(val item: RateItemEntity) : LedgerEvent
    data class OnDeleteRateItem(val item: RateItemEntity) : LedgerEvent
    data class OnBulkDeleteRateItems(val ids: List<String>) : LedgerEvent
    data class OnBulkAssignCategory(val ids: List<String>, val category: String) : LedgerEvent
    data class OnBulkAdjustPrice(val ids: List<String>, val percentDelta: Double) : LedgerEvent
    data class OnAddRateCategory(val categoryName: String, val iconKey: String = "", val iconUrl: String = "") : LedgerEvent
    data class OnEditRateCategory(val oldCategoryName: String, val newCategoryName: String, val iconKey: String = "", val iconUrl: String = "") : LedgerEvent
    data class OnDeleteRateCategory(val categoryName: String) : LedgerEvent
    data class OnUpdateSettings(val businessName: String, val businessPhone: String, val businessAddress: String, val currencySymbol: String, val upiId: String) : LedgerEvent
    data class OnSetPasscode(val pin: String, val enable: Boolean) : LedgerEvent
    data class OnVerifyPasscode(val pin: String) : LedgerEvent
    object OnLockApp : LedgerEvent
    data class OnRestoreAuditLog(val log: AuditLogEntity) : LedgerEvent
    object OnClearAuditHistory : LedgerEvent
    object OnClearUserMessage : LedgerEvent
    data class OnSignInWithEmail(val email: String, val pass: String) : LedgerEvent
    data class OnCreateUserWithEmail(val email: String, val pass: String) : LedgerEvent
    object OnSignOut : LedgerEvent
    object OnTriggerCloudSync : LedgerEvent
}

private data class SearchFilters(
    val query: String,
    val cFilter: String,
    val iFilter: String
)

private data class SecurityState(
    val selCustId: String?,
    val unlocked: Boolean,
    val msg: String?,
    val failedAttempts: Int,
    val lockedOut: Boolean
)

private data class RawData(
    val summary: BusinessSummary,
    val custs: List<CustomerWithBalance>,
    val invs: List<InvoiceEntity>,
    val logs: List<AuditLogEntity>,
    val transactions: List<TransactionEntity> = emptyList()
)

private data class FilterState(
    val query: String,
    val cFilter: String,
    val iFilter: String,
    val selCustId: String?,
    val unlocked: Boolean,
    val msg: String?,
    val failedAttempts: Int,
    val lockedOut: Boolean
)

private data class RateFilterState(
    val items: List<RateItemEntity>,
    val categories: List<String>,
    val searchQuery: String,
    val category: String,
    val minPrice: Double,
    val maxPrice: Double,
    val maxLimit: Double,
    val categoryIcons: Map<String, String> = emptyMap()
)

class LedgerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = LedgerDatabase.getDatabase(application)
    private val dao = db.ledgerDao()
    val prefs = AppPreferencesManager(application)
    val authManager = FirebaseAuthManager(application, prefs)
    val syncManager = FirestoreSyncManager(application, dao, authManager, prefs)
    val repository = LedgerRepository(dao, syncManager)

    private val _searchQuery = MutableStateFlow("")
    private val _customerFilter = MutableStateFlow("CUSTOMER")
    private val _invoiceFilter = MutableStateFlow("ALL")
    private val _selectedCustomerId = MutableStateFlow<String?>(null)
    private val _userMessage = MutableStateFlow<String?>(null)
    private val _isUnlocked = MutableStateFlow(!prefs.isPasscodeEnabled || prefs.isUnlocked)
    private val _failedPinAttempts = MutableStateFlow(0)
    private val _isLockedOut = MutableStateFlow(false)
    private val _pinnedCustomerIds = MutableStateFlow<Set<String>>(prefs.pinnedCustomerIds)

    private val _rateSearchQuery = MutableStateFlow("")
    private val _selectedRateCategory = MutableStateFlow("ALL")
    private val _rateMinPriceFilter = MutableStateFlow(0.0)
    private val _rateMaxPriceFilter = MutableStateFlow(1000.0)
    private val _customCategories = MutableStateFlow<List<String>>(prefs.customCategories)
    private val _categoryIconMap = MutableStateFlow<Map<String, String>>(loadCategoryIconMap())
    private val _targetRateItemIdToOpen = MutableStateFlow<String?>(null)
    private val _webClientId = MutableStateFlow(prefs.webClientId)
    private val _isCheckingAuth = MutableStateFlow(true)
    private val _isSigningIn = MutableStateFlow(false)
    private val _isDarkMode = MutableStateFlow(prefs.isDarkMode)
    private val _selectedLanguage = MutableStateFlow(AppLanguage.fromCode(prefs.selectedLanguageCode))
    private val _availableUnits = MutableStateFlow<List<String>>(prefs.customUnits)
    private val _hiddenCategories = MutableStateFlow<Set<String>>(prefs.hiddenCategories)

    private fun loadCategoryIconMap(): Map<String, String> {
        return try {
            val json = org.json.JSONObject(prefs.categoryIconMapJson)
            val map = mutableMapOf<String, String>()
            json.keys().forEach { k -> map[k] = json.optString(k) }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    init {
        repository.syncManager = syncManager
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.ensureStartingEntriesForRateItems()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        // Auto check for existing Google login session on opening the app on IO thread
        viewModelScope.launch(Dispatchers.IO) {
            _isCheckingAuth.value = true
            try {
                authManager.attemptSilentSignIn()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isCheckingAuth.value = false
            }
        }
    }

    private val rawDataFlow: Flow<RawData> = combine(
        repository.businessSummary,
        repository.customersWithBalance,
        repository.allInvoices,
        repository.allAuditLogs,
        repository.allTransactions
    ) { summary, custs, invs, logs, txns ->
        RawData(summary, custs, invs, logs, txns)
    }.distinctUntilChanged()

    private val searchFiltersFlow: Flow<SearchFilters> = combine(
        _searchQuery,
        _customerFilter,
        _invoiceFilter
    ) { query, cFilter, iFilter ->
        SearchFilters(query, cFilter, iFilter)
    }.distinctUntilChanged()

    private val securityStateFlow: Flow<SecurityState> = combine(
        _selectedCustomerId,
        _isUnlocked,
        _userMessage,
        _failedPinAttempts,
        _isLockedOut
    ) { selCustId, unlocked, msg, failedAttempts, lockedOut ->
        SecurityState(selCustId, unlocked, msg, failedAttempts, lockedOut)
    }.distinctUntilChanged()

    private val filterStateFlow: Flow<FilterState> = combine(
        searchFiltersFlow,
        securityStateFlow
    ) { sf, sec ->
        FilterState(sf.query, sf.cFilter, sf.iFilter, sec.selCustId, sec.unlocked, sec.msg, sec.failedAttempts, sec.lockedOut)
    }.distinctUntilChanged()

    private data class CloudAndFilterState(
        val filter: FilterState,
        val authUser: AuthUser?,
        val syncState: SyncUiState,
        val webClientId: String,
        val isCheckingAuth: Boolean,
        val isSigningIn: Boolean,
        val isDarkMode: Boolean,
        val selectedLanguage: AppLanguage,
        val availableUnits: List<String>,
        val hiddenCategories: Set<String>
    )

    private val cloudAndFilterFlow: Flow<CloudAndFilterState> = combine(
        filterStateFlow,
        authManager.currentUser,
        syncManager.syncState,
        _webClientId,
        _isCheckingAuth,
        _isSigningIn,
        _isDarkMode,
        _selectedLanguage,
        _availableUnits,
        _hiddenCategories
    ) { args: Array<Any?> ->
        CloudAndFilterState(
            filter = args[0] as FilterState,
            authUser = args[1] as AuthUser?,
            syncState = args[2] as SyncUiState,
            webClientId = args[3] as String,
            isCheckingAuth = args[4] as Boolean,
            isSigningIn = args[5] as Boolean,
            isDarkMode = args[6] as Boolean,
            selectedLanguage = args[7] as AppLanguage,
            availableUnits = args[8] as List<String>,
            hiddenCategories = args[9] as Set<String>
        )
    }.distinctUntilChanged()

    private data class RatePriceFilter(val minPrice: Double, val maxPrice: Double)
    private val ratePriceFilterFlow = combine(_rateMinPriceFilter, _rateMaxPriceFilter) { minP, maxP ->
        RatePriceFilter(minP, maxP)
    }.distinctUntilChanged()

    private data class RateFilterConfig(
        val query: String,
        val category: String,
        val customCats: List<String>,
        val priceFilter: RatePriceFilter,
        val iconMap: Map<String, String>
    )

    private val rateConfigFlow = combine(
        _rateSearchQuery,
        _selectedRateCategory,
        _customCategories,
        ratePriceFilterFlow,
        _categoryIconMap
    ) { query, cat, customCats, priceFilter, iconMap ->
        RateFilterConfig(query, cat, customCats, priceFilter, iconMap)
    }.distinctUntilChanged()

    private val rateFilterStateFlow: Flow<RateFilterState> = combine(
        repository.allRateItems,
        rateConfigFlow
    ) { rawItems, config ->
        val query = config.query.trim()
        val isQueryBlank = query.isEmpty()
        val category = config.category
        val customCats = config.customCats
        val priceFilter = config.priceFilter
        val iconMap = config.iconMap

        // Deduplicate raw items by ID to eliminate phantom race condition rows
        val dedupedItems = rawItems.distinctBy { it.id }

        val minP = priceFilter.minPrice
        val maxP = priceFilter.maxPrice
        val maxComputedPrice = dedupedItems.maxOfOrNull { it.maxSellPrice.coerceAtLeast(it.sellPrice) } ?: 1000.0
        val maxLimit = Math.max(1000.0, Math.ceil(maxComputedPrice / 100.0) * 100.0)

        // Only apply price filtering if the user explicitly moved the price sliders away from the defaults
        val isDefaultPriceFilter = minP <= 0.0 && (maxP >= 1000.0 || maxP >= maxLimit)

        val cleanSelectedCat = category.trim()
        val isAllCat = cleanSelectedCat.isEmpty() || cleanSelectedCat.equals("ALL", ignoreCase = true)
        val normalizedSelectedCat = if (isAllCat) "" else cleanSelectedCat.replace(" / ", "/").replace(" & ", "&")

        val lowerQuery = query.lowercase()
        val filtered = if (isQueryBlank && isAllCat && isDefaultPriceFilter) {
            dedupedItems
        } else {
            dedupedItems.filter { item ->
                val matchesCat = if (isAllCat) {
                    true
                } else {
                    val cleanItemCat = item.category.trim()
                    cleanItemCat.equals(cleanSelectedCat, ignoreCase = true) ||
                    cleanItemCat.replace(" / ", "/").replace(" & ", "&")
                        .equals(normalizedSelectedCat, ignoreCase = true)
                }
                if (!matchesCat) return@filter false

                val itemPrice = if (item.sellPrice > 0) item.sellPrice else item.minSellPrice
                val matchesPrice = if (isDefaultPriceFilter) true else (itemPrice >= minP && (maxP >= maxLimit || itemPrice <= maxP))
                if (!matchesPrice) return@filter false

                isQueryBlank || item.searchTokens.contains(lowerQuery)
            }
        }

        val catSet = linkedSetOf("ALL")
        catSet.addAll(customCats.map { it.trim() })
        for (item in dedupedItems) {
            val trimmedCat = item.category.trim()
            if (trimmedCat.isNotBlank() && !trimmedCat.equals("ALL", ignoreCase = true)) {
                catSet.add(trimmedCat)
            }
        }

        RateFilterState(filtered, catSet.toList(), config.query, category, minP, maxP, maxLimit, iconMap)
    }.distinctUntilChanged().flowOn(Dispatchers.Default)

    val uiState: StateFlow<LedgerUiState> = combine(
        rawDataFlow,
        cloudAndFilterFlow,
        _pinnedCustomerIds,
        rateFilterStateFlow,
        _targetRateItemIdToOpen
    ) { data, cloudFilter, pinnedIds, rateState, targetItemId ->
        val filter = cloudFilter.filter
        val query = filter.query.trim()
        val isQueryBlank = query.isEmpty()
        val isSupplierFilter = filter.cFilter == "SUPPLIER"

        val lowerCustQuery = query.lowercase()
        val filteredCusts = data.custs.filter { cb ->
            val expectedPartyType = if (isSupplierFilter) CustomerEntity.PARTY_TYPE_SUPPLIER else CustomerEntity.PARTY_TYPE_CUSTOMER
            if (cb.customer.partyType != expectedPartyType) return@filter false
            if (filter.cFilter == "OWED" && cb.netBalance <= 0.0) return@filter false
            if (filter.cFilter == "ADVANCE" && cb.netBalance >= 0.0) return@filter false

            isQueryBlank || cb.customer.searchTokens.contains(lowerCustQuery)
        }.sortedWith(
            compareByDescending<CustomerWithBalance> { pinnedIds.contains(it.customer.id) }
                .thenByDescending { it.lastTransactionDate ?: 0L }
        )

        val now = System.currentTimeMillis()
        val filteredInvs = if (filter.iFilter == "ALL") {
            data.invs
        } else {
            data.invs.filter { inv ->
                when (filter.iFilter) {
                    "UNPAID" -> inv.status == InvoiceEntity.STATUS_UNPAID || inv.status == InvoiceEntity.STATUS_PARTIAL
                    "OVERDUE" -> (inv.status == InvoiceEntity.STATUS_UNPAID || inv.status == InvoiceEntity.STATUS_PARTIAL) && inv.dueDate < now
                    "PAID" -> inv.status == InvoiceEntity.STATUS_PAID
                    else -> true
                }
            }
        }

        val selCustWithBalance = if (filter.selCustId == null) null else data.custs.find { it.customer.id == filter.selCustId }

        LedgerUiState(
            summary = data.summary,
            customers = filteredCusts,
            selectedCustomerWithBalance = selCustWithBalance,
            invoices = filteredInvs,
            allTransactions = data.transactions,
            auditLogs = data.logs,
            rateItems = rateState.items,
            rateCategories = rateState.categories,
            selectedRateCategory = rateState.category,
            rateSearchQuery = rateState.searchQuery,
            rateMinPriceFilter = rateState.minPrice,
            rateMaxPriceFilter = rateState.maxPrice,
            rateMaxPriceLimit = rateState.maxLimit,
            pinnedCustomerIds = pinnedIds,
            currencySymbol = prefs.currencySymbol,
            businessName = prefs.businessName,
            businessPhone = prefs.businessPhone,
            businessAddress = prefs.businessAddress,
            upiId = prefs.upiId,
            businessGstin = prefs.businessGstin,
            isPasscodeEnabled = prefs.isPasscodeEnabled,
            isUnlocked = filter.unlocked,
            failedPinAttempts = filter.failedAttempts,
            isLockedOut = filter.lockedOut,
            customerSearchQuery = filter.query,
            customerFilter = filter.cFilter,
            invoiceFilter = filter.iFilter,
            userMessage = filter.msg,
            targetRateItemIdToOpen = targetItemId,
            authUser = cloudFilter.authUser,
            syncState = cloudFilter.syncState,
            webClientId = cloudFilter.webClientId,
            isCheckingAuth = cloudFilter.isCheckingAuth,
            isSigningIn = cloudFilter.isSigningIn,
            isDarkMode = cloudFilter.isDarkMode,
            categoryIconMap = rateState.categoryIcons,
            selectedLanguage = cloudFilter.selectedLanguage,
            availableUnits = cloudFilter.availableUnits,
            hiddenCategories = cloudFilter.hiddenCategories
        )
    }.distinctUntilChanged().flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LedgerUiState()
    )

    val selectedCustomerTransactions: Flow<List<TransactionEntity>> = _selectedCustomerId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else repository.getTransactionsForCustomer(id).distinctUntilChanged()
    }

    // --- MVI EVENT DISPATCHER ---
    fun onEvent(event: LedgerEvent) {
        when (event) {
            is LedgerEvent.OnSearchQueryChange -> setSearchQuery(event.query)
            is LedgerEvent.OnCustomerFilterChange -> setCustomerFilter(event.filter)
            is LedgerEvent.OnSelectCustomer -> selectCustomer(event.id)
            is LedgerEvent.OnAddCustomer -> addCustomer(event.name, event.phone, event.businessName, event.partyType, event.notes)
            is LedgerEvent.OnUpdateCustomer -> updateCustomer(event.customer)
            is LedgerEvent.OnDeleteCustomer -> deleteCustomer(event.id)
            is LedgerEvent.OnDeleteSelectedCustomers -> deleteSelectedCustomers(event.ids)
            is LedgerEvent.OnTogglePinCustomer -> togglePinCustomer(event.id)
            is LedgerEvent.OnAddTransaction -> addTransaction(event.customerId, event.amount, event.type, event.description, event.paymentMethod, event.invoiceRef, event.dateTimestamp)
            is LedgerEvent.OnUpdateTransaction -> updateTransaction(event.transaction)
            is LedgerEvent.OnDeleteTransaction -> deleteTransaction(event.transaction)
            is LedgerEvent.OnDeleteSelectedTransactions -> deleteSelectedTransactions(event.transactions)
            is LedgerEvent.OnInvoiceFilterChange -> setInvoiceFilter(event.filter)
            is LedgerEvent.OnCreateInvoice -> createInvoice(event.customerId, event.customerName, event.items, event.dueDays, event.taxPercent, event.discountAmount, event.notes, event.paymentMode)
            is LedgerEvent.OnRecordInvoicePayment -> recordInvoicePayment(event.invoice, event.paymentAmount, event.paymentMethod)
            is LedgerEvent.OnUpdateInvoice -> updateInvoice(event.invoice)
            is LedgerEvent.OnDeleteInvoice -> deleteInvoice(event.invoice)
            is LedgerEvent.OnDeleteSelectedInvoices -> deleteSelectedInvoices(event.invoices)
            is LedgerEvent.OnRateCategoryChange -> setRateCategoryFilter(event.category)
            is LedgerEvent.OnRateSearchQueryChange -> setRateSearchQuery(event.query)
            is LedgerEvent.OnRateMinMaxFilterChange -> setRateMinMaxFilter(event.minPrice, event.maxPrice)
            is LedgerEvent.OnAddRateItem -> addRateItem(event.name, event.category, event.unit, event.qty, event.buyRate, event.sellPrice, event.sellPriceStr, event.hasMultipleVariants, event.variants)
            is LedgerEvent.OnUpdateRateItem -> updateRateItem(event.item)
            is LedgerEvent.OnDeleteRateItem -> deleteRateItem(event.item)
            is LedgerEvent.OnBulkDeleteRateItems -> bulkDeleteRateItems(event.ids)
            is LedgerEvent.OnBulkAssignCategory -> bulkAssignCategory(event.ids, event.category)
            is LedgerEvent.OnBulkAdjustPrice -> bulkAdjustPrice(event.ids, event.percentDelta)
            is LedgerEvent.OnAddRateCategory -> addRateCategory(event.categoryName, event.iconKey, event.iconUrl)
            is LedgerEvent.OnEditRateCategory -> editRateCategory(event.oldCategoryName, event.newCategoryName, event.iconKey, event.iconUrl)
            is LedgerEvent.OnDeleteRateCategory -> deleteRateCategory(event.categoryName)
            is LedgerEvent.OnUpdateSettings -> updateSettings(event.businessName, event.businessPhone, event.businessAddress, event.currencySymbol, event.upiId)
            is LedgerEvent.OnSetPasscode -> setPasscode(event.pin, event.enable)
            is LedgerEvent.OnVerifyPasscode -> verifyPasscode(event.pin)
            is LedgerEvent.OnLockApp -> lockApp()
            is LedgerEvent.OnRestoreAuditLog -> restoreAuditLog(event.log)
            is LedgerEvent.OnClearAuditHistory -> clearAuditHistory()
            is LedgerEvent.OnClearUserMessage -> clearUserMessage()
            is LedgerEvent.OnSignInWithEmail -> signInWithEmail(event.email, event.pass)
            is LedgerEvent.OnCreateUserWithEmail -> createUserWithEmail(event.email, event.pass)
            is LedgerEvent.OnSignOut -> signOut()
            is LedgerEvent.OnTriggerCloudSync -> triggerCloudSync()
        }
    }

    fun signInWithEmail(email: String, pass: String) {
        viewModelScope.launch {
            _isSigningIn.value = true
            _userMessage.value = null
            try {
                when (val result = authManager.signInWithEmail(email, pass)) {
                    is AuthResult.Success -> {
                        _userMessage.value = "Signed in as ${result.user.email}"
                        syncManager.performFullTwoWaySync()
                    }
                    is AuthResult.Cancelled -> {
                        _userMessage.value = null
                    }
                    is AuthResult.Error -> {
                        _userMessage.value = result.message
                    }
                }
            } finally {
                _isSigningIn.value = false
            }
        }
    }

    fun createUserWithEmail(email: String, pass: String) {
        viewModelScope.launch {
            _isSigningIn.value = true
            _userMessage.value = null
            try {
                when (val result = authManager.createUserWithEmail(email, pass)) {
                    is AuthResult.Success -> {
                        _userMessage.value = "Account created for ${result.user.email}"
                        syncManager.performFullTwoWaySync()
                    }
                    is AuthResult.Cancelled -> {
                        _userMessage.value = null
                    }
                    is AuthResult.Error -> {
                        _userMessage.value = result.message
                    }
                }
            } finally {
                _isSigningIn.value = false
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authManager.signOut()
            _userMessage.value = "Signed out"
        }
    }

    fun triggerCloudSync() {
        viewModelScope.launch {
            val success = syncManager.performFullTwoWaySync()
            if (!success && authManager.currentUser.value == null) {
                _userMessage.value = "Please sign in to sync"
            }
        }
    }

    fun addRateCategory(categoryName: String, iconKey: String = "", iconUrl: String = "") {
        val trimmed = categoryName.trim()
        if (trimmed.isBlank()) return
        val current = _customCategories.value.toMutableList()
        if (!current.any { it.equals(trimmed, ignoreCase = true) }) {
            current.add(trimmed)
            _customCategories.value = current
            prefs.customCategories = current
        }
        if (iconKey.isNotBlank()) {
            prefs.setCategoryIconKey(trimmed, iconKey)
            val updatedMap = _categoryIconMap.value.toMutableMap()
            updatedMap[trimmed] = iconKey
            _categoryIconMap.value = updatedMap
            syncManager.pushCategory(trimmed, iconKey, iconUrl)
        }
        _selectedRateCategory.value = trimmed
        _userMessage.value = "Category '$trimmed' added"
    }

    fun editRateCategory(oldCategoryName: String, newCategoryName: String, iconKey: String = "", iconUrl: String = "") {
        val oldTrimmed = oldCategoryName.trim()
        val newTrimmed = newCategoryName.trim()
        if (newTrimmed.isBlank()) return

        val current = _customCategories.value.toMutableList()
        val index = current.indexOfFirst { it.equals(oldTrimmed, ignoreCase = true) }
        if (index != -1) {
            current[index] = newTrimmed
        } else if (!current.any { it.equals(newTrimmed, ignoreCase = true) }) {
            current.add(newTrimmed)
        }
        _customCategories.value = current
        prefs.customCategories = current

        val updatedMap = _categoryIconMap.value.toMutableMap()
        if (!oldTrimmed.equals(newTrimmed, ignoreCase = true)) {
            updatedMap.remove(oldTrimmed)
        }
        if (iconKey.isNotBlank()) {
            updatedMap[newTrimmed] = iconKey
            prefs.setCategoryIconKey(newTrimmed, iconKey)
        }
        _categoryIconMap.value = updatedMap

        if (_selectedRateCategory.value.equals(oldTrimmed, ignoreCase = true)) {
            _selectedRateCategory.value = newTrimmed
        }

        viewModelScope.launch {
            repository.renameCategory(oldTrimmed, newTrimmed)
            syncManager.renameCategoryInCloud(oldTrimmed, newTrimmed, iconKey, iconUrl)
            com.example.widget.ItemRateWidgetProvider.notifyDataChanged(getApplication())
            _userMessage.value = "Category updated to '$newTrimmed'"
        }
    }

    fun deleteRateCategory(categoryName: String) {
        val trimmed = categoryName.trim()
        val current = _customCategories.value.toMutableList()
        current.removeAll { it.equals(trimmed, ignoreCase = true) }
        _customCategories.value = current
        prefs.customCategories = current

        val updatedMap = _categoryIconMap.value.toMutableMap()
        updatedMap.remove(trimmed)
        _categoryIconMap.value = updatedMap

        if (_selectedRateCategory.value.equals(trimmed, ignoreCase = true)) {
            _selectedRateCategory.value = "ALL"
        }

        viewModelScope.launch {
            repository.renameCategory(trimmed, "General")
            syncManager.deleteCategoryInCloud(trimmed)
            com.example.widget.ItemRateWidgetProvider.notifyDataChanged(getApplication())
            _userMessage.value = "Category '$trimmed' removed"
        }
    }

    fun setRateMinMaxFilter(minP: Double, maxP: Double) {
        _rateMinPriceFilter.value = minP
        _rateMaxPriceFilter.value = maxP
    }

    fun togglePinCustomer(customerId: String) {
        val current = prefs.pinnedCustomerIds
        val isCurrentlyPinned = current.contains(customerId)
        val updated = if (isCurrentlyPinned) current - customerId else current + customerId
        prefs.pinnedCustomerIds = updated
        _pinnedCustomerIds.value = updated
        _userMessage.value = if (isCurrentlyPinned) "Customer unpinned" else "Customer pinned to top"
    }

    fun selectCustomer(id: String?) {
        _selectedCustomerId.value = id
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCustomerFilter(filter: String) {
        _customerFilter.value = filter
    }

    fun setInvoiceFilter(filter: String) {
        _invoiceFilter.value = filter
    }

    fun addCustomerInline(
        name: String,
        partyType: String = CustomerEntity.PARTY_TYPE_CUSTOMER
    ): String {
        val newId = java.util.UUID.randomUUID().toString()
        viewModelScope.launch {
            repository.insertCustomer(
                CustomerEntity(
                    id = newId,
                    name = name.trim(),
                    phone = "",
                    businessName = "",
                    partyType = partyType,
                    notes = "Created inline"
                )
            )
            _userMessage.value = "Added customer ${name.trim()}"
        }
        return newId
    }

    fun addCustomer(
        name: String,
        phone: String,
        businessName: String,
        partyType: String,
        notes: String,
        openingBalance: Double = 0.0,
        area: String = ""
    ) {
        if (name.isBlank() && businessName.isBlank()) return
        viewModelScope.launch {
            val customerId = java.util.UUID.randomUUID().toString()
            repository.insertCustomer(
                CustomerEntity(
                    id = customerId,
                    name = name.ifBlank { businessName }.trim(),
                    phone = phone.trim(),
                    businessName = businessName.trim(),
                    partyType = partyType,
                    notes = notes.trim(),
                    area = area.trim()
                )
            )
            if (openingBalance > 0.0) {
                val txnType = if (partyType == CustomerEntity.PARTY_TYPE_SUPPLIER) {
                    TransactionType.GOT_PAYMENT.name
                } else {
                    TransactionType.GAVE_CREDIT.name
                }
                repository.insertTransaction(
                    TransactionEntity(
                        customerId = customerId,
                        amount = openingBalance,
                        type = txnType,
                        description = "Opening Balance",
                        paymentMethod = "CASH"
                    )
                )
            }
            _userMessage.value = "Added customer ${name.ifBlank { businessName }.trim()}"
        }
    }

    fun deleteCustomer(id: String) {
        viewModelScope.launch {
            val cust = repository.getCustomerById(id)
            val txns = repository.getTransactionsForCustomer(id).first()
            if (cust != null) {
                lastDeletedSnapshot = DeletedRecordSnapshot.Customers(listOf(cust to txns))
            }
            repository.deleteCustomerWithData(id)
            if (_selectedCustomerId.value == id) {
                _selectedCustomerId.value = null
            }
            _userMessage.value = "Customer deleted"
        }
    }

    fun addTransaction(
        customerId: String,
        amount: Double,
        type: String,
        description: String,
        paymentMethod: String,
        invoiceRef: String,
        dateTimestamp: Long = System.currentTimeMillis()
    ) {
        if (amount <= 0) return
        
        viewModelScope.launch {
            // Deduplication: prevent duplicate records within the same second
            val existing = repository.getTransactionsForCustomer(customerId).first().find { 
                it.amount == amount && 
                it.type == type && 
                it.description == description && 
                Math.abs(it.date - dateTimestamp) < 2000
            }
            if (existing != null) {
                _userMessage.value = "Duplicate entry prevented"
                return@launch
            }

            repository.insertTransaction(
                TransactionEntity(
                    customerId = customerId,
                    amount = amount,
                    type = type,
                    description = description,
                    paymentMethod = paymentMethod,
                    invoiceRef = invoiceRef,
                    date = dateTimestamp
                )
            )
            _userMessage.value = if (type == TransactionType.GAVE_CREDIT.name) "Sale on credit recorded" else "Payment received recorded"
        }
    }

    fun updateTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.updateTransaction(transaction)
            _userMessage.value = "Transaction updated"
        }
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        lastDeletedSnapshot = DeletedRecordSnapshot.Transactions(listOf(transaction))
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
            _userMessage.value = "Transaction deleted"
        }
    }

    fun deleteSelectedTransactions(transactions: List<TransactionEntity>) {
        lastDeletedSnapshot = DeletedRecordSnapshot.Transactions(transactions)
        viewModelScope.launch {
            repository.deleteTransactions(transactions)
            _userMessage.value = "${transactions.size} transaction(s) deleted"
        }
    }

    fun deleteSelectedCustomers(ids: Set<String>) {
        viewModelScope.launch {
            val snapshots = ids.mapNotNull { id ->
                val cust = repository.getCustomerById(id)
                if (cust != null) {
                    val txns = repository.getTransactionsForCustomer(id).first()
                    cust to txns
                } else null
            }
            if (snapshots.isNotEmpty()) {
                lastDeletedSnapshot = DeletedRecordSnapshot.Customers(snapshots)
            }
            repository.deleteCustomers(ids)
            _userMessage.value = "${ids.size} customer(s) deleted"
        }
    }

    fun createInvoice(
        customerId: String,
        customerName: String,
        items: List<InvoiceItem>,
        dueDays: Int,
        taxPercent: Double,
        discountAmount: Double,
        notes: String,
        paymentMode: String = "DUE"
    ) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            val subtotal = items.sumOf { it.total }
            val taxAmount = subtotal * (taxPercent / 100.0)
            val totalAmount = Math.max(0.0, subtotal + taxAmount - discountAmount)
            val now = System.currentTimeMillis()
            val dueDate = now + (dueDays * 86400000L)
            val invNumber = repository.generateNextInvoiceNumber()

            // Create JSON string for items
            val jsonArr = JSONArray()
            items.forEach { item ->
                val obj = JSONObject()
                obj.put("description", item.description)
                obj.put("quantity", item.quantity)
                obj.put("unitPrice", item.unitPrice)
                jsonArr.put(obj)
            }

            val isPaidMode = paymentMode != "DUE"
            val initialPaidAmount = if (isPaidMode) totalAmount else 0.0
            val initialStatus = if (isPaidMode) InvoiceEntity.STATUS_PAID else InvoiceEntity.STATUS_UNPAID
            val formattedNotes = if (isPaidMode && notes.isNotBlank()) "$notes [Paid via $paymentMode]" else if (isPaidMode) "Paid via $paymentMode" else notes

            val newInvoice = InvoiceEntity(
                invoiceNumber = invNumber,
                customerId = customerId,
                customerName = customerName,
                issueDate = now,
                dueDate = dueDate,
                itemsJson = jsonArr.toString(),
                subtotal = subtotal,
                taxAmount = taxAmount,
                discountAmount = discountAmount,
                totalAmount = totalAmount,
                paidAmount = initialPaidAmount,
                status = initialStatus,
                notes = formattedNotes
            )

            val imageFile = try {
                com.example.ui.utils.InvoiceImageGenerator.saveInvoiceImageFile(
                    context = getApplication(),
                    invoice = newInvoice,
                    businessName = prefs.businessName,
                    businessPhone = prefs.businessPhone,
                    businessAddress = prefs.businessAddress,
                    currencySymbol = prefs.currencySymbol
                )
            } catch (e: Exception) {
                null
            }

            repository.insertInvoice(newInvoice.copy(imagePath = imageFile?.absolutePath ?: ""))

            // Ledger Transaction Logging for linked customers
            if (customerId.isNotBlank()) {
                if (paymentMode == "DUE") {
                    repository.insertTransaction(
                        TransactionEntity(
                            customerId = customerId,
                            amount = totalAmount,
                            type = TransactionType.GAVE_CREDIT.name,
                            description = "Invoice issued #$invNumber (DUE)",
                            invoiceRef = invNumber,
                            paymentMethod = "INVOICE"
                        )
                    )
                } else {
                    repository.insertTransaction(
                        TransactionEntity(
                            customerId = customerId,
                            amount = totalAmount,
                            type = TransactionType.GAVE_CREDIT.name,
                            description = "Invoice issued #$invNumber",
                            invoiceRef = invNumber,
                            paymentMethod = "INVOICE"
                        )
                    )
                    repository.insertTransaction(
                        TransactionEntity(
                            customerId = customerId,
                            amount = totalAmount,
                            type = TransactionType.GOT_PAYMENT.name,
                            description = "Paid Bill #$invNumber ($paymentMode)",
                            invoiceRef = invNumber,
                            paymentMethod = paymentMode
                        )
                    )
                }
            }

            _userMessage.value = "Invoice #$invNumber issued ($paymentMode)"
        }
    }

    fun recordInvoicePayment(invoice: InvoiceEntity, paymentAmount: Double, paymentMethod: String) {
        viewModelScope.launch {
            repository.recordInvoicePayment(invoice, paymentAmount, paymentMethod)
            _userMessage.value = "Payment recorded for Invoice #${invoice.invoiceNumber}"
        }
    }

    fun updateInvoice(invoice: InvoiceEntity) {
        viewModelScope.launch {
            val imageFile = try {
                com.example.ui.utils.InvoiceImageGenerator.saveInvoiceImageFile(
                    context = getApplication(),
                    invoice = invoice,
                    businessName = prefs.businessName,
                    businessPhone = prefs.businessPhone,
                    businessAddress = prefs.businessAddress,
                    currencySymbol = prefs.currencySymbol
                )
            } catch (e: Exception) {
                null
            }
            val updatedWithImage = if (imageFile != null) invoice.copy(imagePath = imageFile.absolutePath) else invoice
            repository.updateInvoice(updatedWithImage)
            _userMessage.value = "Invoice updated"
        }
    }

    fun deleteInvoice(invoice: InvoiceEntity) {
        lastDeletedSnapshot = DeletedRecordSnapshot.Invoices(listOf(invoice))
        viewModelScope.launch {
            repository.deleteInvoice(invoice)
            _userMessage.value = "Receipt #${invoice.invoiceNumber} deleted"
        }
    }

    fun deleteSelectedInvoices(invoices: List<InvoiceEntity>) {
        lastDeletedSnapshot = DeletedRecordSnapshot.Invoices(invoices)
        viewModelScope.launch {
            repository.deleteInvoices(invoices)
            _userMessage.value = "${invoices.size} receipt(s) deleted"
        }
    }

    fun verifyPasscode(pin: String): Boolean {
        if (_isLockedOut.value) return false

        return if (pin == prefs.passcodePin) {
            prefs.isUnlocked = true
            _isUnlocked.value = true
            _failedPinAttempts.value = 0
            true
        } else {
            val newAttempts = _failedPinAttempts.value + 1
            _failedPinAttempts.value = newAttempts
            if (newAttempts >= 5) {
                _isLockedOut.value = true
                _userMessage.value = "Too many failed attempts. Temporary lockout active."
            } else {
                _userMessage.value = "Incorrect PIN. ${5 - newAttempts} attempt(s) remaining."
            }
            false
        }
    }

    fun setPasscode(pin: String, enable: Boolean) {
        prefs.passcodePin = pin
        prefs.isPasscodeEnabled = enable
        prefs.isUnlocked = true
        _isUnlocked.value = true
        _failedPinAttempts.value = 0
        _isLockedOut.value = false
        _userMessage.value = if (enable) "Passcode security enabled" else "Passcode security disabled"
    }

    fun lockApp() {
        prefs.isUnlocked = false
        _isUnlocked.value = false
    }

    fun updateCustomer(customer: CustomerEntity) {
        viewModelScope.launch {
            repository.updateCustomer(customer)
            _userMessage.value = "Customer updated"
        }
    }

    fun updateSettings(
        businessName: String,
        businessPhone: String,
        businessAddress: String,
        currencySymbol: String,
        upiId: String = "merchant@upi",
        businessGstin: String = ""
    ) {
        prefs.businessName = businessName
        prefs.businessPhone = businessPhone
        prefs.businessAddress = businessAddress
        prefs.currencySymbol = currencySymbol
        prefs.upiId = upiId
        prefs.businessGstin = businessGstin
        syncManager.pushBusinessDetails()
        _userMessage.value = "Business details updated"
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    suspend fun getExportJson(): String {
        val custs = repository.allCustomers.first()
        val txns = repository.allTransactions.first()
        val invs = repository.allInvoices.first()
        val rates = repository.allRateItems.first()
        return repository.exportDataToJson(custs, txns, invs, rates)
    }

    suspend fun getCompleteDatabaseCsv(): String {
        val custs = repository.allCustomers.first()
        val txns = repository.allTransactions.first()
        val invs = repository.allInvoices.first()
        val rates = repository.allRateItems.first()
        return ExportUtils.generateCompleteDatabaseCsv(custs, txns, invs, rates)
    }

    suspend fun getLedgerCsv(): String {
        val custs = repository.customersWithBalance.first()
        val txns = repository.allTransactions.first()
        val invs = repository.allInvoices.first()
        return ExportUtils.generateLedgerCsv(custs, txns, invs)
    }

    suspend fun getRateListCsv(): String {
        val rates = repository.allRateItems.first()
        return ExportUtils.generateRateListCsv(rates)
    }

    fun restoreAuditLog(log: AuditLogEntity) {
        viewModelScope.launch {
            val success = repository.restoreAuditLog(log)
            _userMessage.value = if (success) "Restored successfully" else "Failed to restore item"
        }
    }

    fun deleteAuditLog(id: String) {
        viewModelScope.launch {
            repository.deleteAuditLog(id)
            _userMessage.value = "Item removed from trash"
        }
    }

    fun clearAuditHistory() {
        viewModelScope.launch {
            repository.clearAuditLogs()
            _userMessage.value = "Trash emptied"
        }
    }

    fun setRateSearchQuery(query: String) {
        _rateSearchQuery.value = query
    }

    fun setRateCategoryFilter(category: String) {
        _selectedRateCategory.value = category
    }

    fun addRateItem(
        name: String,
        category: String,
        unit: String,
        qty: Double,
        buyRate: Double,
        sellPrice: Double,
        sellPriceStr: String,
        hasMultipleVariants: Boolean = false,
        variants: List<RateVariant> = emptyList()
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return
        val cleanCat = category.trim().ifBlank { "General" }
        val cleanSell = sellPriceStr.replace("₹", "").replace("$", "").trim()
        val sellPriceNum = if (sellPrice > 0) {
            sellPrice
        } else if (cleanSell.contains("-")) {
            cleanSell.split("-").firstOrNull()?.trim()?.toDoubleOrNull() ?: 0.0
        } else {
            cleanSell.toDoubleOrNull() ?: 0.0
        }
        val (minP, maxP) = RateItemEntity.computeMinMax(sellPriceNum, cleanSell.ifBlank { sellPriceNum.toString() })
        
        viewModelScope.launch {
            try {
                val safeId = UUID.randomUUID().toString()
                val encodedVariants = if (hasMultipleVariants && variants.isNotEmpty()) RateItemEntity.encodeVariants(variants) else "[]"
                val newItem = RateItemEntity(
                    id = safeId,
                    name = trimmedName,
                    category = cleanCat,
                    unit = unit.trim().ifBlank { "Kg" },
                    qty = if (qty <= 0) 1.0 else qty,
                    buyRate = buyRate,
                    sellPrice = sellPriceNum,
                    sellPriceStr = cleanSell.ifBlank { if (sellPriceNum > 0) sellPriceNum.toString() else "0" },
                    minSellPrice = minP,
                    maxSellPrice = maxP,
                    hasMultipleVariants = hasMultipleVariants && variants.isNotEmpty(),
                    variantsJson = encodedVariants,
                    lastUpdated = System.currentTimeMillis()
                )

                android.util.Log.d("VARIANT_TRACE", "Hop 2 - Room Payload: name='$trimmedName', hasMultipleVariants=$hasMultipleVariants, variantsCount=${variants.size}, encodedVariants='$encodedVariants'")

                repository.insertRateItem(newItem)

                // Ensure category is registered in custom categories if new
                if (!_customCategories.value.any { it.equals(cleanCat, ignoreCase = true) }) {
                    val updatedCats = _customCategories.value + cleanCat
                    _customCategories.value = updatedCats
                    prefs.customCategories = updatedCats
                }

                if (_selectedRateCategory.value != "ALL" && !_selectedRateCategory.value.trim().equals(cleanCat, ignoreCase = true)) {
                    _selectedRateCategory.value = cleanCat
                }
                if (_rateSearchQuery.value.isNotBlank()) {
                    _rateSearchQuery.value = ""
                }

                com.example.widget.ItemRateWidgetProvider.notifyDataChanged(getApplication())
                _userMessage.value = "Added '$trimmedName' to ItemList"
            } catch (e: Exception) {
                android.util.Log.e("LedgerViewModel", "Failed to add rate item", e)
                _userMessage.value = "Failed to add item: ${e.localizedMessage ?: "Unknown error"}"
            }
        }
    }

    fun saveItem(
        name: String,
        category: String,
        unit: String,
        qty: Double,
        buyRate: Double,
        sellPrice: Double,
        sellPriceStr: String,
        hasMultipleVariants: Boolean = false,
        variants: List<RateVariant> = emptyList()
    ) = addRateItem(name, category, unit, qty, buyRate, sellPrice, sellPriceStr, hasMultipleVariants, variants)

    fun updateRateItem(item: RateItemEntity) {
        viewModelScope.launch {
            repository.updateRateItem(item)
            com.example.widget.ItemRateWidgetProvider.notifyDataChanged(getApplication())
            _userMessage.value = "Updated '${item.name}' rate details"
        }
    }

    private sealed interface DeletedRecordSnapshot {
        data class Customers(val items: List<Pair<CustomerEntity, List<TransactionEntity>>>) : DeletedRecordSnapshot
        data class Transactions(val items: List<TransactionEntity>) : DeletedRecordSnapshot
        data class Invoices(val items: List<InvoiceEntity>) : DeletedRecordSnapshot
    }

    private var lastDeletedRateItems: List<RateItemEntity>? = null
    private var lastDeletedSnapshot: DeletedRecordSnapshot? = null

    fun canUndo(): Boolean = !lastDeletedRateItems.isNullOrEmpty() || lastDeletedSnapshot != null

    fun undoLastDelete() {
        val rateItems = lastDeletedRateItems
        if (!rateItems.isNullOrEmpty()) {
            lastDeletedRateItems = null
            restoreRateItems(rateItems)
            return
        }
        val snapshot = lastDeletedSnapshot ?: return
        lastDeletedSnapshot = null
        viewModelScope.launch {
            when (snapshot) {
                is DeletedRecordSnapshot.Customers -> {
                    snapshot.items.forEach { (cust, txns) ->
                        repository.insertCustomer(cust)
                        txns.forEach { repository.insertTransaction(it) }
                    }
                    _userMessage.value = "Restored ${snapshot.items.size} customer(s)"
                }
                is DeletedRecordSnapshot.Transactions -> {
                    snapshot.items.forEach { repository.insertTransaction(it) }
                    _userMessage.value = "Restored ${snapshot.items.size} transaction(s)"
                }
                is DeletedRecordSnapshot.Invoices -> {
                    snapshot.items.forEach { repository.insertInvoice(it) }
                    _userMessage.value = "Restored ${snapshot.items.size} receipt(s)"
                }
            }
        }
    }

    fun deleteRateItem(item: RateItemEntity) {
        lastDeletedSnapshot = null
        lastDeletedRateItems = listOf(item)
        viewModelScope.launch {
            repository.deleteRateItem(item)
            com.example.widget.ItemRateWidgetProvider.notifyDataChanged(getApplication())
            _userMessage.value = "Removed '${item.name}' from Rate List"
        }
    }

    fun bulkDeleteRateItems(ids: List<String>) {
        lastDeletedSnapshot = null
        lastDeletedRateItems = uiState.value.rateItems.filter { ids.contains(it.id) }
        viewModelScope.launch {
            repository.bulkDeleteRateItems(ids)
            com.example.widget.ItemRateWidgetProvider.notifyDataChanged(getApplication())
            _userMessage.value = "Deleted ${ids.size} item(s) from Rate List"
        }
    }

    fun deleteRateItems(ids: Set<String>) {
        bulkDeleteRateItems(ids.toList())
    }

    fun restoreRateItem(item: RateItemEntity) {
        viewModelScope.launch {
            repository.insertRateItem(item)
            com.example.widget.ItemRateWidgetProvider.notifyDataChanged(getApplication())
            _userMessage.value = "Restored '${item.name}'"
        }
    }

    fun restoreRateItems(items: List<RateItemEntity>) {
        viewModelScope.launch {
            items.forEach { repository.insertRateItem(it) }
            com.example.widget.ItemRateWidgetProvider.notifyDataChanged(getApplication())
            _userMessage.value = "Restored ${items.size} item(s)"
        }
    }

    fun bulkAssignCategory(ids: List<String>, category: String) {
        viewModelScope.launch {
            repository.bulkAssignCategory(ids, category)
            com.example.widget.ItemRateWidgetProvider.notifyDataChanged(getApplication())
            _userMessage.value = "Updated category for ${ids.size} item(s)"
        }
    }

    fun bulkAdjustPrice(ids: List<String>, percentDelta: Double) {
        viewModelScope.launch {
            repository.bulkAdjustPrice(ids, percentDelta)
            com.example.widget.ItemRateWidgetProvider.notifyDataChanged(getApplication())
            val sign = if (percentDelta >= 0) "+$percentDelta%" else "$percentDelta%"
            _userMessage.value = "Adjusted prices by $sign for ${ids.size} item(s)"
        }
    }

    fun openRateItemDetails(itemId: String?) {
        _targetRateItemIdToOpen.value = itemId
    }

    fun clearTargetRateItemId() {
        _targetRateItemIdToOpen.value = null
    }

    fun getPriceHistoryForItem(itemId: String): Flow<List<PriceHistoryEntity>> {
        return repository.getPriceHistoryForItem(itemId)
    }

    fun importBackupData(jsonStr: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.importDataFromCsv(jsonStr)
            _userMessage.value = if (success) "Backup restored successfully!" else "Failed to parse backup file"
            onResult(success)
        }
    }

    suspend fun getDailyClosingSummary(): DailyClosingSummary {
        return repository.getDailyClosingSummary(
            businessName = uiState.value.businessName,
            currencySymbol = uiState.value.currencySymbol
        )
    }

    fun shareDailyClosing(context: Context) {
        viewModelScope.launch {
            try {
                val summary = repository.getDailyClosingSummary(
                    businessName = uiState.value.businessName,
                    currencySymbol = uiState.value.currencySymbol
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Daily Closing Summary - ${summary.dateStr}")
                    putExtra(Intent.EXTRA_TEXT, summary.formattedShareText)
                }
                val chooser = Intent.createChooser(intent, "Share Daily Closing Summary")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            } catch (e: Exception) {
                e.printStackTrace()
                _userMessage.value = "Failed to generate daily closing: ${e.message}"
            }
        }
    }

    fun toggleDarkMode() {
        val newMode = !_isDarkMode.value
        prefs.isDarkMode = newMode
        _isDarkMode.value = newMode
    }

    fun setDarkMode(enabled: Boolean) {
        prefs.isDarkMode = enabled
        _isDarkMode.value = enabled
    }

    fun setLanguage(lang: AppLanguage) {
        prefs.selectedLanguageCode = lang.code
        _selectedLanguage.value = lang
    }

    fun addUnit(newUnit: String) {
        val clean = newUnit.trim()
        if (clean.isBlank()) return
        val current = _availableUnits.value.toMutableList()
        if (!current.any { it.equals(clean, ignoreCase = true) }) {
            current.add(clean)
            prefs.customUnits = current
            _availableUnits.value = current
        }
    }

    fun deleteUnit(unitToDelete: String) {
        val current = _availableUnits.value.toMutableList()
        current.removeAll { it.equals(unitToDelete, ignoreCase = true) }
        prefs.customUnits = current
        _availableUnits.value = current
    }

    fun moveUnitUp(index: Int) {
        if (index <= 0) return
        val current = _availableUnits.value.toMutableList()
        if (index < current.size) {
            val item = current.removeAt(index)
            current.add(index - 1, item)
            prefs.customUnits = current
            _availableUnits.value = current
        }
    }

    fun moveUnitDown(index: Int) {
        val current = _availableUnits.value.toMutableList()
        if (index in 0 until (current.size - 1)) {
            val item = current.removeAt(index)
            current.add(index + 1, item)
            prefs.customUnits = current
            _availableUnits.value = current
        }
    }

    fun toggleCategoryVisibility(category: String) {
        val current = _hiddenCategories.value.toMutableSet()
        if (current.contains(category)) {
            current.remove(category)
        } else {
            current.add(category)
        }
        prefs.hiddenCategories = current
        _hiddenCategories.value = current
    }

    fun renameRateCategory(oldName: String, newName: String) {
        editRateCategory(oldName, newName)
    }

    fun setUnits(newUnits: List<String>) {
        prefs.customUnits = newUnits
        _availableUnits.value = newUnits
    }

    fun setCategories(newCategories: List<String>) {
        val finalCategories = if (newCategories.contains("ALL")) newCategories else listOf("ALL") + newCategories
        prefs.customCategories = finalCategories
        _customCategories.value = finalCategories
    }

    fun setCategoryIcon(category: String, iconKey: String) {
        val map = _categoryIconMap.value.toMutableMap()
        map[category] = iconKey
        val json = org.json.JSONObject()
        map.forEach { (k, v) -> json.put(k, v) }
        prefs.categoryIconMapJson = json.toString()
        _categoryIconMap.value = map
    }
}

