package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CustomerEntity
import com.example.data.TransactionType
import com.example.ui.LedgerUiState
import com.example.ui.LedgerViewModel
import com.example.ui.components.AddCustomerDialog
import com.example.ui.components.AddQuickSaleDepositSheet
import com.example.ui.components.AddTransactionDialog
import com.example.ui.components.AddTransactionOrPersonSheet
import com.example.ui.components.ExpandableSpeedDialFab
import com.example.ui.components.FloatingPillNavigation
import com.example.ui.screens.*
import com.example.ui.theme.LedgerMateTheme
import com.example.ui.theme.PrimaryBlue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: LedgerViewModel by viewModels()
    private var pendingShortcutAction by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingShortcutAction = intent.action
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(android.graphics.Color.parseColor("#FAFCF9"))
        enableEdgeToEdge()
        pendingShortcutAction = intent?.action

        // Cold-Start Acceleration: Pre-warm Firebase Auth and core configs on background IO dispatcher concurrently
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            } catch (e: Exception) {
                // Ignore silent initialization errors if offline/not configured yet
            }
        }

        setContent {
            val state by viewModel.uiState.collectAsState()
            com.example.ui.i18n.ProvideAppLanguage(language = state.selectedLanguage) {
                LedgerMateTheme(darkTheme = state.isDarkMode) {
                    var isBypassAuth by remember { mutableStateOf(false) }

                    val isAuthenticated = state.authUser != null
                    if (!isAuthenticated && !isBypassAuth) {
                        LoginScreen(
                            isCheckingAuth = state.isCheckingAuth,
                            isSigningIn = state.isSigningIn,
                            onSignInWithEmail = { email, pass ->
                                viewModel.signInWithEmail(email, pass)
                            },
                            onCreateAccountWithEmail = { email, pass ->
                                viewModel.createUserWithEmail(email, pass)
                            },
                            onContinueOffline = {
                                isBypassAuth = true
                            },
                            userMessage = state.userMessage,
                            currentLang = state.selectedLanguage
                        )
                    } else {
                        LedgerApp(
                            viewModel = viewModel,
                            shortcutAction = pendingShortcutAction,
                            onClearShortcutAction = { pendingShortcutAction = null }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerApp(
    viewModel: LedgerViewModel,
    shortcutAction: String? = null,
    onClearShortcutAction: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }

    // Dialog States
    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var partyTypeToAdd by remember { mutableStateOf(CustomerEntity.PARTY_TYPE_CUSTOMER) }
    var showAddTransactionDialog by remember { mutableStateOf(false) }
    var initialTxnType by remember { mutableStateOf(TransactionType.GAVE_CREDIT.name) }
    var preselectedRateItemsForInvoice by remember { mutableStateOf<List<com.example.data.RateItemEntity>>(emptyList()) }
    var preselectedInvoiceItemsForInvoice by remember { mutableStateOf<List<com.example.data.InvoiceItem>>(emptyList()) }
    var preselectedCustomerIdForQuickEntry by remember { mutableStateOf<String?>(null) }
    var showAddRateItemSheet by remember { mutableStateOf(false) }
    var showAddTransactionOrPersonSheet by remember { mutableStateOf(false) }

    // Expandable Speed-Dial & Quick Sale/Deposit Modal States
    var isSpeedDialExpanded by remember { mutableStateOf(false) }
    var showQuickSaleDepositSheet by remember { mutableStateOf(false) }
    var quickEntryType by remember { mutableStateOf(TransactionType.GAVE_CREDIT.name) }
    var activeDateForQuickEntry by remember { mutableStateOf<Long?>(null) }
    var isRateItemSelectionMode by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()

    // User Snackbar notification with consolidated Undo action
    LaunchedEffect(state.userMessage) {
        state.userMessage?.let { msg ->
            scope.launch {
                val hasUndo = viewModel.canUndo()
                val result = snackbarHostState.showSnackbar(
                    message = msg,
                    actionLabel = if (hasUndo) "Undo" else null,
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.undoLastDelete()
                }
            }
            viewModel.clearUserMessage()
        }
    }

    // Home Screen Quick Actions / App Shortcuts dispatch
    LaunchedEffect(shortcutAction) {
        if (shortcutAction != null) {
            when (shortcutAction) {
                "com.example.ACTION_ADD_SALE" -> {
                    selectedTab = 1
                    quickEntryType = TransactionType.GAVE_CREDIT.name
                    showQuickSaleDepositSheet = true
                    onClearShortcutAction()
                }
                "com.example.ACTION_ADD_DEPOSIT" -> {
                    selectedTab = 1
                    quickEntryType = TransactionType.GOT_PAYMENT.name
                    showQuickSaleDepositSheet = true
                    onClearShortcutAction()
                }
                "com.example.ACTION_VIEW_TODAY" -> {
                    selectedTab = 1
                    activeDateForQuickEntry = System.currentTimeMillis()
                    onClearShortcutAction()
                }
            }
        }
    }

    // Security Passcode Lock Screen Overlay
    if (!state.isUnlocked) {
        PasscodeLockScreen(
            onVerifyPin = { pin -> viewModel.verifyPasscode(pin) },
            title = "Enter PIN",
            subtitle = ""
        )
    } else {
        NavHost(
            navController = navController,
            startDestination = "main_screen"
        ) {
            composable("main_screen") {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.surface,
                    snackbarHost = {
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(bottom = 88.dp)
                                .zIndex(150f)
                        ) { data ->
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.95f),
                                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                                    shadowElevation = 8.dp,
                                    modifier = Modifier
                                        .padding(horizontal = 24.dp)
                                        .wrapContentWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = data.visuals.message,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.inverseOnSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        data.visuals.actionLabel?.let { action ->
                                            Text(
                                                text = action,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF34D399), // High-contrast Emerald Accent
                                                modifier = Modifier
                                                    .clickable { data.performAction() }
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    bottomBar = {}
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding)
                    ) {
                        // Detailed Customer Screen View takes priority if a customer is selected
                        if (state.selectedCustomerWithBalance != null) {
                            val selCustTransactions by viewModel.selectedCustomerTransactions.collectAsState(initial = emptyList())

                            CustomerDetailScreen(
                                customerWithBalance = state.selectedCustomerWithBalance!!,
                                transactions = selCustTransactions,
                                currencySymbol = state.currencySymbol,
                                businessName = state.businessName,
                                businessPhone = state.businessPhone,
                                businessAddress = state.businessAddress,
                                upiId = state.upiId,
                                invoices = state.invoices,
                                onBackClick = { viewModel.selectCustomer(null) },
                                onAddTransactionClick = { type ->
                                    initialTxnType = type
                                    showAddTransactionDialog = true
                                },
                                onDeleteTransaction = { txn -> viewModel.deleteTransaction(txn) },
                                onUpdateTransaction = { txn -> viewModel.updateTransaction(txn) },
                                onUpdateCustomer = { cust -> viewModel.updateCustomer(cust) },
                                onDeleteCustomer = { id -> viewModel.deleteCustomer(id) },
                                onDeleteSelectedTransactions = { txns -> viewModel.deleteSelectedTransactions(txns) }
                            )
                        } else {
                            AnimatedContent(
                                targetState = selectedTab,
                                transitionSpec = {
                                    if (targetState > initialState) {
                                        (slideInHorizontally(
                                            initialOffsetX = { width -> width / 3 },
                                            animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f)
                                        ) + fadeIn(animationSpec = tween(220))).togetherWith(
                                            slideOutHorizontally(
                                                targetOffsetX = { width -> -width / 3 },
                                                animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f)
                                            ) + fadeOut(animationSpec = tween(180))
                                        )
                                    } else {
                                        (slideInHorizontally(
                                            initialOffsetX = { width -> -width / 3 },
                                            animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f)
                                        ) + fadeIn(animationSpec = tween(220))).togetherWith(
                                            slideOutHorizontally(
                                                targetOffsetX = { width -> width / 3 },
                                                animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f)
                                            ) + fadeOut(animationSpec = tween(180))
                                        )
                                    }
                                },
                                label = "TabSlideTransition"
                            ) { tab ->
                                when (tab) {
                                    0 -> ItemRateListScreen(
                                        state = state,
                                        viewModel = viewModel,
                                        onOpenSettings = { selectedTab = 3 },
                                        onSelectionModeChange = { isRateItemSelectionMode = it },
                                        onCreateInvoiceFromItems = { items ->
                                            preselectedInvoiceItemsForInvoice = emptyList()
                                            preselectedRateItemsForInvoice = items
                                            navController.navigate("receipt_generator_screen")
                                        },
                                        onCreateInvoiceWithCartItems = { cartItems ->
                                            preselectedRateItemsForInvoice = emptyList()
                                            preselectedInvoiceItemsForInvoice = cartItems
                                            navController.navigate("receipt_generator_screen")
                                        }
                                    )

                                    1 -> CustomersScreen(
                                        state = state,
                                        onSearchQueryChange = { query -> viewModel.setSearchQuery(query) },
                                        onFilterChange = { filter -> viewModel.setCustomerFilter(filter) },
                                        onSelectCustomer = { id -> viewModel.selectCustomer(id) },
                                        onAddCustomerClick = {
                                            partyTypeToAdd = CustomerEntity.PARTY_TYPE_CUSTOMER
                                            showAddCustomerDialog = true
                                        },
                                        onAddEntryForDateClick = { dateTs ->
                                            preselectedCustomerIdForQuickEntry = null
                                            activeDateForQuickEntry = dateTs
                                            quickEntryType = TransactionType.GAVE_CREDIT.name
                                            showQuickSaleDepositSheet = true
                                        },
                                        onActiveDateChange = { dateTs ->
                                            activeDateForQuickEntry = dateTs
                                        },
                                        onDeleteSelectedCustomers = { ids -> viewModel.deleteSelectedCustomers(ids) },
                                        onTriggerCloudSync = { viewModel.triggerCloudSync() },
                                        onDailyClosingClick = { viewModel.shareDailyClosing(context) },
                                        onOpenSettings = { selectedTab = 3 },
                                        onQuickEntryForCustomer = { custId, txnType ->
                                            preselectedCustomerIdForQuickEntry = custId
                                            quickEntryType = txnType
                                            showQuickSaleDepositSheet = true
                                        }
                                    )

                                    2 -> InvoicesScreen(
                                        state = state,
                                        onFilterChange = { filter -> viewModel.setInvoiceFilter(filter) },
                                        onCreateInvoiceClick = { navController.navigate("receipt_generator_screen") },
                                        onRecordPayment = { inv, amount, method ->
                                            viewModel.recordInvoicePayment(inv, amount, method)
                                        },
                                        onDeleteInvoice = { inv -> viewModel.deleteInvoice(inv) },
                                        onUpdateInvoice = { inv -> viewModel.updateInvoice(inv) },
                                        onDeleteSelectedInvoices = { invoices -> viewModel.deleteSelectedInvoices(invoices) }
                                    )

                                    3 -> ReportsSettingsScreen(
                                        state = state,
                                        onUpdateBusinessDetails = { name, phone, address, currency, upi, gstin ->
                                            viewModel.updateSettings(name, phone, address, currency, upi, gstin)
                                        },
                                        onSetPasscode = { pin, enable ->
                                            viewModel.setPasscode(pin, enable)
                                        },
                                        onRestoreAuditLog = { log -> viewModel.restoreAuditLog(log) },
                                        onDeleteAuditLog = { logId -> viewModel.deleteAuditLog(logId) },
                                        onClearAuditLogs = { viewModel.clearAuditHistory() },
                                        onSignOut = { viewModel.signOut() },
                                        onTriggerCloudSync = { viewModel.triggerCloudSync() },
                                        onToggleDarkMode = { enabled -> viewModel.setDarkMode(enabled) },
                                        onSetLanguage = { lang -> viewModel.setLanguage(lang) },
                                        onAddUnit = { unit -> viewModel.addUnit(unit) },
                                        onDeleteUnit = { unit -> viewModel.deleteUnit(unit) },
                                        onMoveUnitUp = { idx -> viewModel.moveUnitUp(idx) },
                                        onMoveUnitDown = { idx -> viewModel.moveUnitDown(idx) },
                                        onReorderUnits = { units -> viewModel.setUnits(units) },
                                        onToggleCategoryVisibility = { cat -> viewModel.toggleCategoryVisibility(cat) },
                                        onRenameCategory = { oldName, newName -> viewModel.renameRateCategory(oldName, newName) },
                                        onAddCategory = { cat -> viewModel.addRateCategory(cat) },
                                        onDeleteCategory = { cat -> viewModel.deleteRateCategory(cat) },
                                        onReorderCategories = { cats -> viewModel.setCategories(cats) },
                                        onSetCategoryIcon = { cat, iconKey -> viewModel.setCategoryIcon(cat, iconKey) }
                                    )
                                }
                            }

                            // Expandable Speed-Dial FAB Menu (Visible on Tab 1 / Ledger)
                            if (selectedTab == 1 && state.selectedCustomerWithBalance == null) {
                                ExpandableSpeedDialFab(
                                    isExpanded = isSpeedDialExpanded,
                                    onToggleExpand = { isSpeedDialExpanded = !isSpeedDialExpanded },
                                    onAddCustomerClick = {
                                        partyTypeToAdd = CustomerEntity.PARTY_TYPE_CUSTOMER
                                        showAddCustomerDialog = true
                                    },
                                    onAddSaleClick = {
                                        quickEntryType = TransactionType.GAVE_CREDIT.name
                                        showQuickSaleDepositSheet = true
                                    },
                                    onAddDepositClick = {
                                        quickEntryType = TransactionType.GOT_PAYMENT.name
                                        showQuickSaleDepositSheet = true
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // Floating Glassmorphic Pill Navigation Bar + FAB (Smoothly hidden while multi-selection mode is active)
                            androidx.compose.animation.AnimatedVisibility(
                                visible = !isRateItemSelectionMode,
                                enter = fadeIn(animationSpec = tween(180)),
                                exit = fadeOut(animationSpec = tween(180)),
                                modifier = Modifier.align(Alignment.BottomCenter)
                            ) {
                                FloatingPillNavigation(
                                    selectedTab = selectedTab,
                                    onTabSelected = { newTab ->
                                        if (isSpeedDialExpanded) isSpeedDialExpanded = false
                                        selectedTab = newTab
                                    },
                                    isDarkMode = state.isDarkMode,
                                    isExpanded = isSpeedDialExpanded && selectedTab == 1,
                                    onFabClick = {
                                        when (selectedTab) {
                                            0 -> showAddRateItemSheet = true
                                            1 -> {
                                                isSpeedDialExpanded = !isSpeedDialExpanded
                                            }
                                            2 -> navController.navigate("receipt_generator_screen")
                                            3 -> showAddTransactionOrPersonSheet = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // --- HARDWARE BACK BUTTON INTERCEPTION ---
                // Intercept back gesture so modals, customer details, or subtabs are dismissed without exiting the app
                if (isSpeedDialExpanded) {
                    BackHandler { isSpeedDialExpanded = false }
                } else if (showQuickSaleDepositSheet) {
                    BackHandler { showQuickSaleDepositSheet = false }
                } else if (showAddTransactionOrPersonSheet) {
                    BackHandler { showAddTransactionOrPersonSheet = false }
                } else if (showAddRateItemSheet) {
                    BackHandler { showAddRateItemSheet = false }
                } else if (showAddTransactionDialog) {
                    BackHandler { showAddTransactionDialog = false }
                } else if (showAddCustomerDialog) {
                    BackHandler { showAddCustomerDialog = false }
                } else if (state.selectedCustomerWithBalance != null) {
                    BackHandler { viewModel.selectCustomer(null) }
                } else if (selectedTab != 0) {
                    BackHandler { selectedTab = 0 }
                }

                // --- GLOBAL OPERATION DIALOGS ---
                if (showQuickSaleDepositSheet) {
                    AddQuickSaleDepositSheet(
                        initialType = quickEntryType,
                        initialCustomerId = preselectedCustomerIdForQuickEntry,
                        initialDateTimestamp = activeDateForQuickEntry,
                        customers = state.customers,
                        currencySymbol = state.currencySymbol,
                        onDismiss = {
                            showQuickSaleDepositSheet = false
                            preselectedCustomerIdForQuickEntry = null
                        },
                        onAddCustomerInline = { typedName ->
                            viewModel.addCustomerInline(typedName)
                        },
                        onConfirm = { custId, amount, type, desc, method, dateTs ->
                            viewModel.addTransaction(
                                customerId = custId,
                                amount = amount,
                                type = type,
                                description = desc,
                                paymentMethod = method,
                                invoiceRef = "",
                                dateTimestamp = dateTs
                            )
                        }
                    )
                }

                if (showAddTransactionOrPersonSheet) {
                    AddTransactionOrPersonSheet(
                        onDismiss = { showAddTransactionOrPersonSheet = false },
                        onAddTransaction = {
                            initialTxnType = TransactionType.GAVE_CREDIT.name
                            showAddTransactionDialog = true
                        },
                        onAddPerson = {
                            showAddCustomerDialog = true
                        },
                        onGenerateReceipt = {
                            navController.navigate("receipt_generator_screen")
                        }
                    )
                }

                if (showAddRateItemSheet) {
                    AddEditRateItemSheet(
                        existingItem = null,
                        currencySymbol = state.currencySymbol,
                        categories = state.rateCategories.filter { it != "ALL" },
                        existingItems = state.rateItems,
                        categoryIconMap = state.categoryIconMap,
                        initialCategory = state.selectedRateCategory,
                        availableUnits = state.availableUnits,
                        onDismiss = { showAddRateItemSheet = false },
                        onAddCategoryWithIcon = { catName, iconKey, iconUrl ->
                            viewModel.addRateCategory(catName, iconKey, iconUrl)
                        },
                        onSave = { name, category, unit, qty, buyRate, sellPriceStr, hasMultipleVariants, variants ->
                            val cleanSell = sellPriceStr.replace("₹", "").replace("$", "").trim()
                            val sellPriceNum = if (cleanSell.contains("-")) {
                                cleanSell.split("-").firstOrNull()?.trim()?.toDoubleOrNull() ?: 0.0
                            } else {
                                cleanSell.toDoubleOrNull() ?: 0.0
                            }
                            viewModel.addRateItem(name, category, unit, qty, buyRate, sellPriceNum, sellPriceStr, hasMultipleVariants, variants)
                            showAddRateItemSheet = false
                        }
                    )
                }

                if (showAddCustomerDialog) {
                    AddCustomerDialog(
                        initialPartyType = partyTypeToAdd,
                        currencySymbol = state.currencySymbol,
                        onDismiss = { showAddCustomerDialog = false },
                        onConfirmWithArea = { name, phone, businessName, partyType, notes, openingBalance, area ->
                            viewModel.addCustomer(name, phone, businessName, partyType, notes, openingBalance, area)
                            showAddCustomerDialog = false
                        },
                        onConfirm = { name, phone, businessName, partyType, notes, openingBalance ->
                            viewModel.addCustomer(name, phone, businessName, partyType, notes, openingBalance)
                            showAddCustomerDialog = false
                        }
                    )
                }

                if (showAddTransactionDialog) {
                    AddTransactionDialog(
                        initialCustomerId = state.selectedCustomerWithBalance?.customer?.id,
                        initialType = initialTxnType,
                        customers = state.customers,
                        currencySymbol = state.currencySymbol,
                        onDismiss = { showAddTransactionDialog = false },
                        onConfirm = { custId, amount, type, description, method, invoiceRef, dateTimestamp ->
                            viewModel.addTransaction(custId, amount, type, description, method, invoiceRef, dateTimestamp)
                        }
                    )
                }
            }

            composable("receipt_generator_screen") {
                ReceiptGeneratorScreen(
                    state = state,
                    preselectedRateItems = preselectedRateItemsForInvoice,
                    preselectedInvoiceItems = preselectedInvoiceItemsForInvoice,
                    onBackClick = {
                        preselectedRateItemsForInvoice = emptyList()
                        preselectedInvoiceItemsForInvoice = emptyList()
                        navController.popBackStack()
                    },
                    onSaveInvoice = { custId, custName, items, dueDays, tax, discount, notes, paymentMode ->
                        viewModel.createInvoice(custId, custName, items, dueDays, tax, discount, notes, paymentMode)
                    },
                    onAddCustomer = { name, phone, businessName, partyType, notes, openingBalance ->
                        viewModel.addCustomer(name, phone, businessName, partyType, notes, openingBalance)
                    }
                )
            }
        }
    }
}
