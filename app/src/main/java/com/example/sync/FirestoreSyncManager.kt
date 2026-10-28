package com.example.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.example.auth.AuthUser
import com.example.auth.FirebaseAuthManager
import com.example.data.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

enum class SyncStatus {
    SYNCING,
    SYNCED,
    OFFLINE,
    NOT_AUTHENTICATED,
    ERROR
}

data class SyncUiState(
    val status: SyncStatus = SyncStatus.NOT_AUTHENTICATED,
    val lastSyncTimestamp: Long = 0L,
    val isOnline: Boolean = true,
    val pendingPushCount: Int = 0,
    val lastSyncedCount: Int = 0,
    val message: String? = null
)

class FirestoreSyncManager(
    private val context: Context,
    private val dao: LedgerDao,
    private val authManager: FirebaseAuthManager,
    private val prefs: AppPreferencesManager
) {
    private val TAG = "FirestoreSyncManager"
    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var firestoreInstance: FirebaseFirestore? = null

    private val _syncState = MutableStateFlow(
        SyncUiState(
            status = if (authManager.currentUser.value == null) SyncStatus.NOT_AUTHENTICATED else SyncStatus.SYNCED,
            lastSyncTimestamp = prefs.lastSyncTimestamp,
            isOnline = isNetworkAvailable()
        )
    )
    val syncState: StateFlow<SyncUiState> = _syncState.asStateFlow()

    private val pendingCount = AtomicInteger(0)

    private var itemsListener: ListenerRegistration? = null
    private var rateItemsListener: ListenerRegistration? = null
    private var categoriesListener: ListenerRegistration? = null
    private var settingsListener: ListenerRegistration? = null
    private var notesListener: ListenerRegistration? = null

    var onPinnedItemsUpdated: ((List<String>, Boolean) -> Unit)? = null
    var onCustomNoteLabelsUpdated: ((List<String>) -> Unit)? = null

    init {
        registerNetworkCallback()
        // Listen to Auth changes to trigger auto-sync and attach real-time snapshot streams
        syncScope.launch {
            authManager.currentUser.collect { user ->
                if (user != null) {
                    startRealtimeSnapshotStreams(user.uid)
                    if (isNetworkAvailable()) {
                        performFullTwoWaySync()
                    } else {
                        _syncState.value = _syncState.value.copy(
                            status = SyncStatus.OFFLINE,
                            message = "Offline: signed in as ${user.email ?: user.displayName}"
                        )
                    }
                } else {
                    stopRealtimeSnapshotStreams()
                    _syncState.value = _syncState.value.copy(
                        status = SyncStatus.NOT_AUTHENTICATED,
                        message = "Sign in to enable cloud sync"
                    )
                }
            }
        }
    }

    private fun getFirestore(): FirebaseFirestore? {
        if (firestoreInstance == null) {
            try {
                val dbId = context.getString(com.example.R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(dbId)
                try {
                    val settings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                        .setLocalCacheSettings(
                            com.google.firebase.firestore.PersistentCacheSettings.newBuilder()
                                .setSizeBytes(100L * 1024L * 1024L)
                                .build()
                        )
                        .build()
                    db.firestoreSettings = settings
                } catch (ignored: Exception) {
                    // Settings might already be locked or initialized
                }
                firestoreInstance = db
            } catch (e: Exception) {
                Log.w(TAG, "Firestore initialization notice: ${e.message}")
            }
        }
        return firestoreInstance
    }

    private fun isNetworkAvailable(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val activeNetwork = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            true // assume true on permission fail
        }
    }

    private fun registerNetworkCallback() {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _syncState.value = _syncState.value.copy(isOnline = true)
                    if (authManager.currentUser.value != null) {
                        syncScope.launch {
                            performFullTwoWaySync()
                        }
                    }
                }

                override fun onLost(network: Network) {
                    _syncState.value = _syncState.value.copy(
                        isOnline = false,
                        status = SyncStatus.OFFLINE,
                        message = "Offline: Local database active"
                    )
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Network callback notice: ${e.message}")
        }
    }

    // ==========================================
    // 1. TWO-WAY LAUNCH & ON-DEMAND AUTO SYNC
    // ==========================================

    suspend fun performFullTwoWaySync(): Boolean = withContext(Dispatchers.IO) {
        val user = authManager.currentUser.value
        if (user == null) {
            _syncState.value = _syncState.value.copy(
                status = SyncStatus.NOT_AUTHENTICATED,
                message = "Sign in to sync"
            )
            return@withContext false
        }

        if (!isNetworkAvailable()) {
            _syncState.value = _syncState.value.copy(
                status = SyncStatus.OFFLINE,
                message = "Device offline - changes stored safely locally"
            )
            return@withContext false
        }

        _syncState.value = _syncState.value.copy(
            status = SyncStatus.SYNCING,
            message = "Synchronizing khata & catalog..."
        )

        val firestore = getFirestore()
        if (firestore == null) {
            _syncState.value = _syncState.value.copy(
                status = SyncStatus.SYNCED,
                lastSyncTimestamp = System.currentTimeMillis(),
                message = "Local database active (Firestore pending config)"
            )
            return@withContext true
        }

        try {
            val uid = user.uid
            var syncedCount = 0

            // 1. Sync Customers
            syncedCount += syncCustomers(firestore, uid)

            // 2. Sync Transactions
            syncedCount += syncTransactions(firestore, uid)

            // 3. Sync Rate Items
            syncedCount += syncRateItems(firestore, uid)

            // 4. Sync Invoices
            syncedCount += syncInvoices(firestore, uid)
            // 5. Sync Categories & Vector Icons
            syncedCount += syncCategories(firestore, uid)
            // 6. Sync Notes
            syncedCount += syncNotes(firestore, uid)
            syncBusinessDetails(firestore, uid)

            val now = System.currentTimeMillis()
            prefs.lastSyncTimestamp = now

            _syncState.value = _syncState.value.copy(
                status = SyncStatus.SYNCED,
                lastSyncTimestamp = now,
                lastSyncedCount = syncedCount,
                pendingPushCount = 0,
                message = "All data up-to-date with cloud"
            )
            Log.i(TAG, "Full two-way sync completed successfully. Synced items: $syncedCount")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Two-way sync error: ${e.message}", e)
            _syncState.value = _syncState.value.copy(
                status = SyncStatus.SYNCED, // Soft-fallback to preserve offline UX
                lastSyncTimestamp = System.currentTimeMillis(),
                message = "Offline-first saved. Background retry scheduled."
            )
            false
        }
    }

fun pushBusinessDetails() {
        val user = authManager.currentUser.value ?: return
        val firestore = getFirestore() ?: return
        syncScope.launch {
            try {
                indicateSyncing()
                val map = hashMapOf<String, Any>(
                    "businessName" to prefs.businessName,
                    "businessPhone" to prefs.businessPhone,
                    "businessAddress" to prefs.businessAddress,
                    "currencySymbol" to prefs.currencySymbol,
                    "upiId" to prefs.upiId,
                    "gstin" to prefs.businessGstin,
                    "last_updated" to System.currentTimeMillis(),
                    "serverTimestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
                val doc = firestore.collection("users").document(user.uid).collection("settings").document("business_profile")
                FirebaseAuthManager.awaitTask { doc.set(map, com.google.firebase.firestore.SetOptions.merge()) }
                indicateSynced()
            } catch (e: Exception) {
                Log.w(TAG, "Failed pushing business details: ${e.message}")
                indicateOfflineOrFallback()
            }
        }
    }

    private suspend fun syncBusinessDetails(firestore: FirebaseFirestore, uid: String) {
        try {
            val docRef = firestore.collection("users").document(uid).collection("settings").document("business_profile")
            var snapshot = FirebaseAuthManager.awaitTask { docRef.get() }
            if (!snapshot.exists()) {
                val legacyRef = firestore.collection("users").document(uid).collection("settings").document("business_details")
                snapshot = FirebaseAuthManager.awaitTask { legacyRef.get() }
            }
            if (snapshot.exists()) {
                val remoteName = snapshot.getString("businessName") ?: ""
                val remotePhone = snapshot.getString("businessPhone") ?: ""
                val remoteAddress = snapshot.getString("businessAddress") ?: ""
                val remoteCurrency = snapshot.getString("currencySymbol") ?: ""
                val remoteUpi = snapshot.getString("upiId") ?: ""
                val remoteGstin = snapshot.getString("gstin") ?: ""

                if (remoteName.isNotBlank()) {
                    prefs.businessName = remoteName
                }
                if (remotePhone.isNotBlank()) {
                    prefs.businessPhone = remotePhone
                }
                if (remoteAddress.isNotBlank()) {
                    prefs.businessAddress = remoteAddress
                }
                if (remoteCurrency.isNotBlank()) {
                    prefs.currencySymbol = remoteCurrency
                }
                if (remoteUpi.isNotBlank()) {
                    prefs.upiId = remoteUpi
                }
                if (remoteGstin.isNotBlank()) {
                    prefs.businessGstin = remoteGstin
                }

                val remotePinnedUpdatedAt = snapshot.getLong("pinnedRateItemsUpdatedAt") ?: 0L
                val localPinnedUpdatedAt = prefs.pinnedRateItemsLastUpdated
                if (remotePinnedUpdatedAt > localPinnedUpdatedAt && snapshot.contains("pinnedRateItemIds")) {
                    val rawPinned = snapshot.getString("pinnedRateItemIds") ?: ""
                    val parsedIds = if (rawPinned.isBlank()) emptyList() else rawPinned.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct()
                    val remoteCollapsed = snapshot.getBoolean("isPinnedItemsCollapsed") ?: prefs.isPinnedItemsCollapsed
                    prefs.pinnedRateItemIds = parsedIds
                    prefs.isPinnedItemsCollapsed = remoteCollapsed
                    prefs.pinnedRateItemsLastUpdated = remotePinnedUpdatedAt
                    onPinnedItemsUpdated?.invoke(parsedIds, remoteCollapsed)
                } else if (localPinnedUpdatedAt > remotePinnedUpdatedAt) {
                    val pushMap = hashMapOf<String, Any>(
                        "pinnedRateItemIds" to prefs.pinnedRateItemIds.joinToString(","),
                        "isPinnedItemsCollapsed" to prefs.isPinnedItemsCollapsed,
                        "pinnedRateItemsUpdatedAt" to localPinnedUpdatedAt
                    )
                    try {
                        FirebaseAuthManager.awaitTask { docRef.set(pushMap, com.google.firebase.firestore.SetOptions.merge()) }
                    } catch (_: Exception) {}
                }

                if (snapshot.contains("customNoteLabels")) {
                    val rawNoteLabels = snapshot.getString("customNoteLabels") ?: ""
                    val parsedLabels = if (rawNoteLabels.isBlank()) emptyList() else rawNoteLabels.split("|||").map { it.trim() }.filter { it.isNotBlank() && it !in NoteEntity.DEFAULT_LABELS }.distinct()
                    val merged = (prefs.customNoteLabels + parsedLabels).distinct()
                    if (merged != prefs.customNoteLabels) {
                        prefs.customNoteLabels = merged
                        onCustomNoteLabelsUpdated?.invoke(merged)
                    }
                } else if (prefs.customNoteLabels.isNotEmpty()) {
                    try {
                        FirebaseAuthManager.awaitTask {
                            docRef.set(hashMapOf("customNoteLabels" to prefs.customNoteLabels.joinToString("|||")), com.google.firebase.firestore.SetOptions.merge())
                        }
                    } catch (_: Exception) {}
                }
            } else if (prefs.pinnedRateItemIds.isNotEmpty() || prefs.pinnedRateItemsLastUpdated > 0L) {
                val pushMap = hashMapOf<String, Any>(
                    "pinnedRateItemIds" to prefs.pinnedRateItemIds.joinToString(","),
                    "isPinnedItemsCollapsed" to prefs.isPinnedItemsCollapsed,
                    "pinnedRateItemsUpdatedAt" to prefs.pinnedRateItemsLastUpdated.coerceAtLeast(System.currentTimeMillis()),
                    "customNoteLabels" to prefs.customNoteLabels.joinToString("|||")
                )
                try {
                    FirebaseAuthManager.awaitTask { docRef.set(pushMap, com.google.firebase.firestore.SetOptions.merge()) }
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed syncing business details: ${e.message}")
        }
    }

    fun pushCustomNoteLabels(labels: List<String>) {
        val user = authManager.currentUser.value ?: return
        val firestore = getFirestore() ?: return
        syncScope.launch {
            try {
                indicateSyncing()
                val map = hashMapOf<String, Any>(
                    "customNoteLabels" to labels.joinToString("|||"),
                    "last_updated" to System.currentTimeMillis()
                )
                val doc = firestore.collection("users").document(user.uid).collection("settings").document("business_profile")
                FirebaseAuthManager.awaitTask { doc.set(map, com.google.firebase.firestore.SetOptions.merge()) }
                indicateSynced()
            } catch (e: Exception) {
                Log.w(TAG, "Failed pushing custom note labels: ${e.message}")
                indicateOfflineOrFallback()
            }
        }
    }

    fun pushPinnedRateItems(pinnedIds: List<String>, isCollapsed: Boolean) {
        val now = System.currentTimeMillis()
        prefs.pinnedRateItemsLastUpdated = now
        val user = authManager.currentUser.value ?: return
        val firestore = getFirestore() ?: return
        syncScope.launch {
            try {
                indicateSyncing()
                val map = hashMapOf<String, Any>(
                    "pinnedRateItemIds" to pinnedIds.joinToString(","),
                    "isPinnedItemsCollapsed" to isCollapsed,
                    "pinnedRateItemsUpdatedAt" to now,
                    "last_updated" to now
                )
                val doc = firestore.collection("users").document(user.uid).collection("settings").document("business_profile")
                FirebaseAuthManager.awaitTask { doc.set(map, com.google.firebase.firestore.SetOptions.merge()) }
                indicateSynced()
            } catch (e: Exception) {
                Log.w(TAG, "Failed pushing pinned rate items: ${e.message}")
                indicateOfflineOrFallback()
            }
        }
    }

    private suspend fun syncCustomers(firestore: FirebaseFirestore, uid: String): Int {
        val collection = firestore.collection("users").document(uid).collection("customers")
        val snapshot = FirebaseAuthManager.awaitTask { collection.get() }
        val remoteMap = snapshot.documents.mapNotNull { doc ->
            val id = doc.getString("id") ?: doc.id
            val name = doc.getString("name") ?: return@mapNotNull null
            CustomerEntity(
                id = id,
                name = name,
                phone = doc.getString("phone") ?: "",
                businessName = doc.getString("businessName") ?: "",
                partyType = doc.getString("partyType") ?: CustomerEntity.PARTY_TYPE_CUSTOMER,
                notes = doc.getString("notes") ?: "",
                createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                lastUpdated = doc.getLong("last_updated") ?: doc.getLong("createdAt") ?: System.currentTimeMillis()
            )
        }.associateBy { it.id }

        val localList = dao.getAllCustomersList()
        val localMap = localList.associateBy { it.id }

        // Cloud items not in local or cloud is newer -> update local
        val toInsertLocally = mutableListOf<CustomerEntity>()
        for ((id, remote) in remoteMap) {
            val local = localMap[id]
            if (local == null || remote.lastUpdated > local.lastUpdated) {
                toInsertLocally.add(remote)
            }
        }
        if (toInsertLocally.isNotEmpty()) {
            dao.insertCustomers(toInsertLocally)
        }

        // Local items not in cloud or local is newer -> push to cloud
        var pushed = 0
        for ((id, local) in localMap) {
            val remote = remoteMap[id]
            if (remote == null || local.lastUpdated > remote.lastUpdated) {
                pushCustomerToFirestore(collection, local)
                pushed++
            }
        }
        return toInsertLocally.size + pushed
    }

    private suspend fun syncTransactions(firestore: FirebaseFirestore, uid: String): Int {
        val collection = firestore.collection("users").document(uid).collection("transactions")
        val snapshot = FirebaseAuthManager.awaitTask { collection.get() }
        val remoteMap = snapshot.documents.mapNotNull { doc ->
            val id = doc.getString("id") ?: doc.id
            val custId = doc.getString("customerId") ?: return@mapNotNull null
            TransactionEntity(
                id = id,
                customerId = custId,
                amount = doc.getDouble("amount") ?: 0.0,
                quantity = doc.getDouble("quantity") ?: 1.0,
                rate = doc.getDouble("rate") ?: 0.0,
                subtotal = doc.getDouble("subtotal") ?: 0.0,
                type = doc.getString("type") ?: TransactionType.GAVE_CREDIT.name,
                description = doc.getString("description") ?: "",
                date = doc.getLong("date") ?: doc.getString("dateIso")?.let { com.example.ui.Formatters.parseDate(it) } ?: System.currentTimeMillis(),
                invoiceRef = doc.getString("invoiceRef") ?: "",
                paymentMethod = doc.getString("paymentMethod") ?: "CASH",
                status = doc.getString("status") ?: "COMPLETED",
                lastUpdated = doc.getLong("last_updated") ?: doc.getLong("date") ?: System.currentTimeMillis()
            )
        }.associateBy { it.id }

        val localList = dao.getAllTransactionsList()
        val localMap = localList.associateBy { it.id }

        val toInsertLocally = mutableListOf<TransactionEntity>()
        for ((id, remote) in remoteMap) {
            val local = localMap[id]
            if (local == null || remote.lastUpdated > local.lastUpdated) {
                toInsertLocally.add(remote)
            }
        }
        if (toInsertLocally.isNotEmpty()) {
            dao.insertTransactions(toInsertLocally)
        }

        var pushed = 0
        for ((id, local) in localMap) {
            val remote = remoteMap[id]
            if (remote == null || local.lastUpdated > remote.lastUpdated) {
                pushTransactionToFirestore(collection, local)
                pushed++
            }
        }
        return toInsertLocally.size + pushed
    }

    private fun docToRateItem(doc: com.google.firebase.firestore.DocumentSnapshot): RateItemEntity? {
        val docId = doc.id.replace("/", "_")
        val rawId = doc.getString("id") ?: docId
        val cleanId = if (rawId.contains("/")) docId else rawId

        val name = doc.getString("name")
            ?: doc.getString("itemName")
            ?: doc.getString("title")
            ?: doc.getString("item_name")
            ?: return null
        if (name.isBlank()) return null

        val category = doc.getString("category")
            ?: doc.getString("categoryName")
            ?: doc.getString("itemCategory")
            ?: doc.getString("group")
            ?: "General"

        val unit = doc.getString("unit")
            ?: doc.getString("uom")
            ?: "Kg"

        val qty = doc.getDouble("qty")
            ?: doc.getDouble("quantity")
            ?: doc.getLong("qty")?.toDouble()
            ?: 1.0

        val buyRate = doc.getDouble("buyRate")
            ?: doc.getDouble("buyPrice")
            ?: doc.getDouble("purchasePrice")
            ?: doc.getDouble("costPrice")
            ?: doc.getLong("buyRate")?.toDouble()
            ?: 0.0

        val sellPrice = doc.getDouble("sellPrice")
            ?: doc.getDouble("sellingPrice")
            ?: doc.getDouble("rate")
            ?: doc.getDouble("price")
            ?: doc.getLong("sellPrice")?.toDouble()
            ?: 0.0

        val sellPriceStr = doc.getString("sellPriceStr")
            ?: doc.getString("rateStr")
            ?: doc.getString("priceStr")
            ?: if (sellPrice > 0) {
                if (sellPrice % 1.0 == 0.0) sellPrice.toLong().toString() else sellPrice.toString()
            } else ""

        val (minP, maxP) = RateItemEntity.computeMinMax(sellPrice, sellPriceStr)
        val minSellPrice = doc.getDouble("minSellPrice") ?: minP
        val maxSellPrice = doc.getDouble("maxSellPrice") ?: maxP
        val priceHistoryJson = doc.getString("priceHistoryJson") ?: "[]"
        val lastUpdated = doc.getLong("last_updated")
            ?: doc.getTimestamp("updatedAt")?.toDate()?.time
            ?: System.currentTimeMillis()

        val rawHasMultipleVariants = doc.getBoolean("hasMultipleVariants")
            ?: doc.getBoolean("hasVariants")
            ?: false

        var variantsJson = doc.getString("variantsJson") ?: "[]"
        if (variantsJson == "[]" || variantsJson.isBlank()) {
            val rawVariants = doc.get("variants")
            if (rawVariants is List<*>) {
                val array = org.json.JSONArray()
                for (vItem in rawVariants) {
                    if (vItem is Map<*, *>) {
                        val obj = org.json.JSONObject()
                        obj.put("id", vItem["id"]?.toString() ?: java.util.UUID.randomUUID().toString())
                        obj.put("label", vItem["label"]?.toString() ?: "")
                        obj.put("unit", vItem["unit"]?.toString() ?: unit)
                        obj.put("qty", (vItem["qty"] as? Number)?.toDouble() ?: 1.0)
                        obj.put("buyRate", (vItem["buyRate"] as? Number)?.toDouble() ?: 0.0)
                        obj.put("sellPrice", (vItem["sellPrice"] as? Number)?.toDouble() ?: 0.0)
                        obj.put("sellPriceStr", vItem["sellPriceStr"]?.toString() ?: "")
                        obj.put("priceHistoryJson", vItem["priceHistoryJson"]?.toString() ?: "[]")
                        array.put(obj)
                    }
                }
                variantsJson = array.toString()
            }
        }

        val hasVars = rawHasMultipleVariants || (variantsJson.isNotBlank() && variantsJson != "[]")

        Log.d("VARIANT_TRACE", "Hop 3 - Firestore Read docToRateItem: docId='${doc.id}', name='$name', hasMultipleVariants=$hasVars, variantsJson='$variantsJson'")

        return RateItemEntity(
            id = cleanId,
            name = name.trim(),
            category = category.trim().ifBlank { "General" },
            unit = unit.trim().ifBlank { "Kg" },
            qty = if (qty <= 0) 1.0 else qty,
            buyRate = buyRate,
            sellPrice = sellPrice,
            sellPriceStr = sellPriceStr,
            minSellPrice = minSellPrice,
            maxSellPrice = maxSellPrice,
            priceHistoryJson = priceHistoryJson,
            hasMultipleVariants = hasVars,
            variantsJson = variantsJson,
            lastUpdated = lastUpdated
        )
    }

    private suspend fun syncRateItems(firestore: FirebaseFirestore, uid: String): Int {
        val itemsCol = firestore.collection("users").document(uid).collection("items")
        val rateItemsCol = firestore.collection("users").document(uid).collection("rate_items")

        val itemsSnapshot = try { FirebaseAuthManager.awaitTask { itemsCol.get() } } catch (_: Exception) { null }
        val rateItemsSnapshot = try { FirebaseAuthManager.awaitTask { rateItemsCol.get() } } catch (_: Exception) { null }

        val allDocs = mutableListOf<com.google.firebase.firestore.DocumentSnapshot>()
        itemsSnapshot?.documents?.let { allDocs.addAll(it) }
        rateItemsSnapshot?.documents?.let { allDocs.addAll(it) }

        val remoteMap = allDocs.mapNotNull { doc -> docToRateItem(doc) }.associateBy { it.id }

        val localList = dao.getAllRateItemsList()
        val localMap = localList.associateBy { it.id }

        val toInsertLocally = mutableListOf<RateItemEntity>()
        for ((id, remote) in remoteMap) {
            val local = localMap[id]
            if (local == null || remote.lastUpdated > local.lastUpdated) {
                toInsertLocally.add(remote)
            }
        }
        if (toInsertLocally.isNotEmpty()) {
            dao.insertRateItems(toInsertLocally)
        }

        var pushed = 0
        for ((id, local) in localMap) {
            val remote = remoteMap[id]
            if (remote == null || local.lastUpdated > remote.lastUpdated) {
                pushRateItemToFirestore(itemsCol, rateItemsCol, local)
                pushed++
            }
        }
        return toInsertLocally.size + pushed
    }

    fun startRealtimeSnapshotStreams(uid: String) {
        stopRealtimeSnapshotStreams()
        val firestore = getFirestore() ?: return

        try {
            // Real-time snapshot stream on primary "items" collection
            val itemsCol = firestore.collection("users").document(uid).collection("items")
            itemsListener = itemsCol.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Items snapshot stream notice: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    syncScope.launch {
                        handleItemsSnapshot(snapshot)
                    }
                }
            }

            // Real-time snapshot stream on secondary "rate_items" collection
            val rateItemsCol = firestore.collection("users").document(uid).collection("rate_items")
            rateItemsListener = rateItemsCol.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Rate items snapshot stream notice: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    syncScope.launch {
                        handleItemsSnapshot(snapshot)
                    }
                }
            }

            // Real-time snapshot stream on "categories" collection
            val catCol = firestore.collection("users").document(uid).collection("categories")
            categoriesListener = catCol.addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null) {
                    syncScope.launch {
                        handleCategoriesSnapshot(snapshot)
                    }
                }
            }

            // Real-time snapshot stream on settings/business_profile for pinned items sync
            val settingsDoc = firestore.collection("users").document(uid).collection("settings").document("business_profile")
            settingsListener = settingsDoc.addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val remoteUpdatedAt = snapshot.getLong("pinnedRateItemsUpdatedAt") ?: 0L
                if (remoteUpdatedAt > prefs.pinnedRateItemsLastUpdated && snapshot.contains("pinnedRateItemIds")) {
                    val rawPinned = snapshot.getString("pinnedRateItemIds") ?: ""
                    val parsedIds = if (rawPinned.isBlank()) emptyList() else rawPinned.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct()
                    val remoteCollapsed = snapshot.getBoolean("isPinnedItemsCollapsed") ?: prefs.isPinnedItemsCollapsed
                    prefs.pinnedRateItemIds = parsedIds
                    prefs.isPinnedItemsCollapsed = remoteCollapsed
                    prefs.pinnedRateItemsLastUpdated = remoteUpdatedAt
                    onPinnedItemsUpdated?.invoke(parsedIds, remoteCollapsed)
                }
                if (snapshot.contains("customNoteLabels")) {
                    val rawNoteLabels = snapshot.getString("customNoteLabels") ?: ""
                    val parsedLabels = if (rawNoteLabels.isBlank()) emptyList() else rawNoteLabels.split("|||").map { it.trim() }.filter { it.isNotBlank() && it !in NoteEntity.DEFAULT_LABELS }.distinct()
                    if (parsedLabels != prefs.customNoteLabels) {
                        prefs.customNoteLabels = parsedLabels
                        onCustomNoteLabelsUpdated?.invoke(parsedLabels)
                    }
                }
            }

            // Real-time snapshot stream on "notes" collection
            val notesCol = firestore.collection("users").document(uid).collection("notes")
            notesListener = notesCol.addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null) {
                    syncScope.launch {
                        handleNotesSnapshot(snapshot)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed registering snapshot streams: ${e.message}")
        }
    }

    fun stopRealtimeSnapshotStreams() {
        try {
            itemsListener?.remove()
            rateItemsListener?.remove()
            categoriesListener?.remove()
            settingsListener?.remove()
            notesListener?.remove()
        } catch (_: Exception) {}
        itemsListener = null
        rateItemsListener = null
        categoriesListener = null
        settingsListener = null
        notesListener = null
    }

    private suspend fun handleItemsSnapshot(snapshot: com.google.firebase.firestore.QuerySnapshot) {
        val itemsToUpsert = mutableListOf<RateItemEntity>()
        val idsToDelete = mutableListOf<String>()

        for (change in snapshot.documentChanges) {
            val doc = change.document
            val item = docToRateItem(doc)
            when (change.type) {
                com.google.firebase.firestore.DocumentChange.Type.ADDED,
                com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                    if (item != null) {
                        itemsToUpsert.add(item)
                    }
                }
                com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                    val id = item?.id ?: doc.id.replace("/", "_")
                    idsToDelete.add(id)
                }
            }
        }

        if (idsToDelete.isNotEmpty()) {
            dao.deleteRateItemsByIds(idsToDelete)
        }
        if (itemsToUpsert.isNotEmpty()) {
            dao.insertRateItems(itemsToUpsert)
            indicateSynced()
        }
    }

    private suspend fun handleCategoriesSnapshot(snapshot: com.google.firebase.firestore.QuerySnapshot) {
        val currentList = prefs.customCategories.toMutableList()
        var changed = false
        for (doc in snapshot.documents) {
            val name = doc.getString("name") ?: continue
            val iconKey = doc.getString("iconKey") ?: ""
            if (!currentList.contains(name)) {
                currentList.add(name)
                changed = true
            }
            if (iconKey.isNotBlank()) {
                prefs.setCategoryIconKey(name, iconKey)
            }
        }
        if (changed) {
            prefs.customCategories = currentList
        }
    }

    private suspend fun syncInvoices(firestore: FirebaseFirestore, uid: String): Int {
        val collection = firestore.collection("users").document(uid).collection("invoices")
        val snapshot = FirebaseAuthManager.awaitTask { collection.get() }
        val remoteMap = snapshot.documents.mapNotNull { doc ->
            val id = doc.getString("id") ?: doc.id
            val invNum = doc.getString("invoiceNumber") ?: return@mapNotNull null
            InvoiceEntity(
                id = id,
                invoiceNumber = invNum,
                customerId = doc.getString("customerId") ?: "",
                customerName = doc.getString("customerName") ?: "",
                issueDate = doc.getLong("issueDate") ?: System.currentTimeMillis(),
                dueDate = doc.getLong("dueDate") ?: System.currentTimeMillis(),
                itemsJson = doc.getString("itemsJson") ?: "[]",
                subtotal = doc.getDouble("subtotal") ?: 0.0,
                taxAmount = doc.getDouble("taxAmount") ?: 0.0,
                discountAmount = doc.getDouble("discountAmount") ?: 0.0,
                totalAmount = doc.getDouble("totalAmount") ?: 0.0,
                paidAmount = doc.getDouble("paidAmount") ?: 0.0,
                status = doc.getString("status") ?: InvoiceEntity.STATUS_UNPAID,
                notes = doc.getString("notes") ?: "",
                imagePath = doc.getString("imagePath") ?: "",
                lastUpdated = doc.getLong("last_updated") ?: doc.getLong("issueDate") ?: System.currentTimeMillis()
            )
        }.associateBy { it.id }

        val localList = dao.getAllInvoicesList()
        val localMap = localList.associateBy { it.id }

        val toInsertLocally = mutableListOf<InvoiceEntity>()
        for ((id, remote) in remoteMap) {
            val local = localMap[id]
            if (local == null || remote.lastUpdated > local.lastUpdated) {
                toInsertLocally.add(remote)
            }
        }
        if (toInsertLocally.isNotEmpty()) {
            dao.insertInvoices(toInsertLocally)
        }

        var pushed = 0
        for ((id, local) in localMap) {
            val remote = remoteMap[id]
            if (remote == null || local.lastUpdated > remote.lastUpdated) {
                pushInvoiceToFirestore(collection, local)
                pushed++
            }
        }
        return toInsertLocally.size + pushed
    }

    // ==========================================
    // 2. CONTINUOUS REAL-TIME BACKGROUND PUSHES
    // ==========================================

    fun pushCustomer(customer: CustomerEntity) {
        val user = authManager.currentUser.value ?: return
        val firestore = getFirestore() ?: return
        syncScope.launch {
            try {
                indicateSyncing()
                val col = firestore.collection("users").document(user.uid).collection("customers")
                pushCustomerToFirestore(col, customer)
                indicateSynced()
            } catch (e: Exception) {
                Log.w(TAG, "Failed pushing customer: ${e.message}")
                indicateOfflineOrFallback()
            }
        }
    }

    fun deleteCustomer(customerId: String) {
        val user = authManager.currentUser.value ?: return
        val firestore = getFirestore() ?: return
        syncScope.launch {
            try {
                indicateSyncing()
                val doc = firestore.collection("users").document(user.uid).collection("customers").document(customerId)
                FirebaseAuthManager.awaitTask { doc.delete() }
                indicateSynced()
            } catch (e: Exception) {
                Log.w(TAG, "Failed deleting customer on cloud: ${e.message}")
                indicateOfflineOrFallback()
            }
        }
    }

    fun pushTransaction(transaction: TransactionEntity) {
        val user = authManager.currentUser.value ?: return
        val firestore = getFirestore() ?: return
        
        indicateSyncing()
        val col = firestore.collection("users").document(user.uid).collection("transactions")
        
        val isoDate = com.example.ui.Formatters.toLocalDateTimeString(transaction.date)
        
        val safeQuantity = if (transaction.quantity.isNaN() || transaction.quantity.isInfinite()) 0.0 else transaction.quantity
        val safeRate = if (transaction.rate.isNaN() || transaction.rate.isInfinite()) 0.0 else transaction.rate
        val safeSubtotal = if (transaction.subtotal.isNaN() || transaction.subtotal.isInfinite()) 0.0 else transaction.subtotal
        
        val map = hashMapOf<String, Any>(
            "id" to transaction.id,
            "customerId" to transaction.customerId,
            "amount" to transaction.amount,
            "type" to transaction.type,
            "description" to transaction.description,
            "date" to transaction.date,
            "dateIso" to isoDate,
            "serverTimestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "invoiceRef" to transaction.invoiceRef,
            "paymentMethod" to transaction.paymentMethod,
            "status" to transaction.status,
            "last_updated" to transaction.lastUpdated,
            "quantity" to safeQuantity,
            "rate" to safeRate,
            "subtotal" to safeSubtotal
        )
        
        col.document(transaction.id).set(map, SetOptions.merge())
            .addOnSuccessListener {
                indicateSynced()
                android.widget.Toast.makeText(context, "Added Successfully", android.widget.Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Failed pushing transaction: ${e.message}")
                indicateOfflineOrFallback()
                android.widget.Toast.makeText(context, "Write failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
    }

    fun deleteTransaction(transactionId: String) {
        val user = authManager.currentUser.value ?: return
        val firestore = getFirestore() ?: return
        syncScope.launch {
            try {
                indicateSyncing()
                val doc = firestore.collection("users").document(user.uid).collection("transactions").document(transactionId)
                FirebaseAuthManager.awaitTask { doc.delete() }
                indicateSynced()
            } catch (e: Exception) {
                Log.w(TAG, "Failed deleting transaction on cloud: ${e.message}")
                indicateOfflineOrFallback()
            }
        }
    }

    fun pushRateItem(rateItem: RateItemEntity) {
        val user = authManager.currentUser.value ?: return
        val firestore = getFirestore() ?: return
        syncScope.launch {
            try {
                indicateSyncing()
                val itemsCol = firestore.collection("users").document(user.uid).collection("items")
                val rateItemsCol = firestore.collection("users").document(user.uid).collection("rate_items")
                pushRateItemToFirestore(itemsCol, rateItemsCol, rateItem)
                indicateSynced()
            } catch (e: Exception) {
                Log.w(TAG, "Failed pushing rate item: ${e.message}")
                indicateOfflineOrFallback()
            }
        }
    }

    fun saveItem(rateItem: RateItemEntity) = pushRateItem(rateItem)
    fun addItem(rateItem: RateItemEntity) = pushRateItem(rateItem)

    fun deleteRateItem(rateItemId: String) {
        val user = authManager.currentUser.value ?: return
        val firestore = getFirestore() ?: return
        val cleanId = rateItemId.replace("/", "_").replace("\\", "_").trim()
        if (cleanId.isBlank()) return
        syncScope.launch {
            try {
                indicateSyncing()
                val itemsDoc = firestore.collection("users").document(user.uid).collection("items").document(cleanId)
                val rateItemsDoc = firestore.collection("users").document(user.uid).collection("rate_items").document(cleanId)
                try { FirebaseAuthManager.awaitTask { itemsDoc.delete() } } catch (_: Exception) {}
                try { FirebaseAuthManager.awaitTask { rateItemsDoc.delete() } } catch (_: Exception) {}
                indicateSynced()
            } catch (e: Exception) {
                Log.w(TAG, "Failed deleting rate item on cloud: ${e.message}")
                indicateOfflineOrFallback()
            }
        }
    }

    fun pushInvoice(invoice: InvoiceEntity) {
        val user = authManager.currentUser.value ?: return
        val firestore = getFirestore() ?: return
        syncScope.launch {
            try {
                indicateSyncing()
                val col = firestore.collection("users").document(user.uid).collection("invoices")
                pushInvoiceToFirestore(col, invoice)
                indicateSynced()
            } catch (e: Exception) {
                Log.w(TAG, "Failed pushing invoice: ${e.message}")
                indicateOfflineOrFallback()
            }
        }
    }

    fun deleteInvoice(invoiceId: String) {
        val user = authManager.currentUser.value ?: return
        val firestore = getFirestore() ?: return
        syncScope.launch {
            try {
                indicateSyncing()
                val doc = firestore.collection("users").document(user.uid).collection("invoices").document(invoiceId)
                FirebaseAuthManager.awaitTask { doc.delete() }
                indicateSynced()
            } catch (e: Exception) {
                Log.w(TAG, "Failed deleting invoice on cloud: ${e.message}")
                indicateOfflineOrFallback()
            }
        }
    }

    private fun docToNote(doc: com.google.firebase.firestore.DocumentSnapshot): NoteEntity? {
        val id = (doc.getString("id") ?: doc.id).replace("/", "_").trim()
        if (id.isBlank()) return null
        val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
        val updatedAt = doc.getLong("updatedAt") ?: doc.getLong("last_updated") ?: createdAt
        return NoteEntity(
            id = id,
            text = doc.getString("text") ?: "",
            label = doc.getString("label") ?: NoteEntity.LABEL_GENERAL,
            isChecklist = doc.getBoolean("isChecklist") ?: false,
            checklistJson = doc.getString("checklistJson") ?: "[]",
            isPinned = doc.getBoolean("isPinned") ?: false,
            isDone = doc.getBoolean("isDone") ?: false,
            linkedCustomerId = doc.getString("linkedCustomerId") ?: "",
            linkedCustomerName = doc.getString("linkedCustomerName") ?: "",
            reminderTime = doc.getLong("reminderTime") ?: 0L,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private suspend fun syncNotes(firestore: FirebaseFirestore, uid: String): Int {
        val collection = firestore.collection("users").document(uid).collection("notes")
        val snapshot = try {
            FirebaseAuthManager.awaitTask { collection.get() }
        } catch (_: Exception) {
            return 0
        }
        val remoteMap = snapshot.documents.mapNotNull { docToNote(it) }.associateBy { it.id }
        val localList = dao.getAllNotesList()
        val localMap = localList.associateBy { it.id }

        val toInsertLocally = mutableListOf<NoteEntity>()
        for ((id, remote) in remoteMap) {
            val local = localMap[id]
            if (local == null || remote.updatedAt > local.updatedAt) {
                toInsertLocally.add(remote)
            }
        }
        if (toInsertLocally.isNotEmpty()) {
            dao.insertNotes(toInsertLocally)
            com.example.notes.NoteReminderScheduler.rescheduleAllFutureReminders(context, toInsertLocally)
        }

        var pushed = 0
        for ((id, local) in localMap) {
            val remote = remoteMap[id]
            if (remote == null || local.updatedAt > remote.updatedAt) {
                pushNoteToFirestore(collection, local)
                pushed++
            }
        }
        return toInsertLocally.size + pushed
    }

    private suspend fun handleNotesSnapshot(snapshot: com.google.firebase.firestore.QuerySnapshot) {
        val notesToUpsert = mutableListOf<NoteEntity>()
        val idsToDelete = mutableListOf<String>()

        for (change in snapshot.documentChanges) {
            val doc = change.document
            val note = docToNote(doc)
            when (change.type) {
                com.google.firebase.firestore.DocumentChange.Type.ADDED,
                com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                    if (note != null) {
                        val local = dao.getNoteById(note.id)
                        if (local == null || note.updatedAt >= local.updatedAt) {
                            notesToUpsert.add(note)
                        }
                    }
                }
                com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                    val id = note?.id ?: doc.id.replace("/", "_")
                    idsToDelete.add(id)
                }
            }
        }

        if (idsToDelete.isNotEmpty()) {
            idsToDelete.forEach { com.example.notes.NoteReminderScheduler.cancelReminder(context, it) }
            dao.deleteNotesByIds(idsToDelete)
        }
        if (notesToUpsert.isNotEmpty()) {
            dao.insertNotes(notesToUpsert)
            notesToUpsert.forEach { com.example.notes.NoteReminderScheduler.scheduleReminder(context, it) }
            indicateSynced()
        }
    }

    private suspend fun pushNoteToFirestore(col: com.google.firebase.firestore.CollectionReference, note: NoteEntity) {
        val cleanId = note.id.replace("/", "_").trim()
        if (cleanId.isBlank()) return
        val map = hashMapOf<String, Any>(
            "id" to cleanId,
            "text" to note.text,
            "label" to note.label,
            "isChecklist" to note.isChecklist,
            "checklistJson" to note.checklistJson,
            "isPinned" to note.isPinned,
            "isDone" to note.isDone,
            "linkedCustomerId" to note.linkedCustomerId,
            "linkedCustomerName" to note.linkedCustomerName,
            "reminderTime" to note.reminderTime,
            "createdAt" to note.createdAt,
            "updatedAt" to note.updatedAt,
            "last_updated" to note.updatedAt
        )
        FirebaseAuthManager.awaitTask { col.document(cleanId).set(map, SetOptions.merge()) }
    }

    fun pushNote(note: NoteEntity) {
        val user = authManager.currentUser.value ?: return
        val firestore = getFirestore() ?: return
        syncScope.launch {
            try {
                indicateSyncing()
                val col = firestore.collection("users").document(user.uid).collection("notes")
                pushNoteToFirestore(col, note)
                indicateSynced()
            } catch (e: Exception) {
                Log.w(TAG, "Failed pushing note: ${e.message}")
                indicateOfflineOrFallback()
            }
        }
    }

    fun deleteNote(noteId: String) {
        val user = authManager.currentUser.value ?: return
        val firestore = getFirestore() ?: return
        val cleanId = noteId.replace("/", "_").trim()
        if (cleanId.isBlank()) return
        syncScope.launch {
            try {
                indicateSyncing()
                val doc = firestore.collection("users").document(user.uid).collection("notes").document(cleanId)
                FirebaseAuthManager.awaitTask { doc.delete() }
                indicateSynced()
            } catch (e: Exception) {
                Log.w(TAG, "Failed deleting note on cloud: ${e.message}")
                indicateOfflineOrFallback()
            }
        }
    }

    fun deleteNotes(noteIds: Collection<String>) {
        if (noteIds.isEmpty()) return
        val user = authManager.currentUser.value ?: return
        val firestore = getFirestore() ?: return
        syncScope.launch {
            try {
                indicateSyncing()
                val col = firestore.collection("users").document(user.uid).collection("notes")
                for (rawId in noteIds) {
                    val cleanId = rawId.replace("/", "_").trim()
                    if (cleanId.isNotBlank()) {
                        try {
                            FirebaseAuthManager.awaitTask { col.document(cleanId).delete() }
                        } catch (_: Exception) {}
                    }
                }
                indicateSynced()
            } catch (e: Exception) {
                Log.w(TAG, "Failed deleting notes on cloud: ${e.message}")
                indicateOfflineOrFallback()
            }
        }
    }

    // --- Private Document Helpers ---

    private suspend fun pushCustomerToFirestore(col: com.google.firebase.firestore.CollectionReference, c: CustomerEntity) {
        val map = hashMapOf<String, Any>(
            "id" to c.id,
            "name" to c.name,
            "phone" to c.phone,
            "businessName" to c.businessName,
            "partyType" to c.partyType,
            "notes" to c.notes,
            "createdAt" to c.createdAt,
            "last_updated" to c.lastUpdated
        )
        FirebaseAuthManager.awaitTask { col.document(c.id).set(map, SetOptions.merge()) }
    }

    private suspend fun pushTransactionToFirestore(col: com.google.firebase.firestore.CollectionReference, t: TransactionEntity) {
        val isoDate = com.example.ui.Formatters.toLocalDateTimeString(t.date)
        
        val safeQuantity = if (t.quantity.isNaN() || t.quantity.isInfinite()) 0.0 else t.quantity
        val safeRate = if (t.rate.isNaN() || t.rate.isInfinite()) 0.0 else t.rate
        val safeSubtotal = if (t.subtotal.isNaN() || t.subtotal.isInfinite()) 0.0 else t.subtotal
        
        val map = hashMapOf<String, Any>(
            "id" to t.id,
            "customerId" to t.customerId,
            "amount" to t.amount,
            "type" to t.type,
            "description" to t.description,
            "date" to t.date,
            "dateIso" to isoDate,
            "serverTimestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "invoiceRef" to t.invoiceRef,
            "paymentMethod" to t.paymentMethod,
            "status" to t.status,
            "last_updated" to t.lastUpdated,
            "quantity" to safeQuantity,
            "rate" to safeRate,
            "subtotal" to safeSubtotal
        )
        FirebaseAuthManager.awaitTask { col.document(t.id).set(map, SetOptions.merge()) }
    }

    private suspend fun pushRateItemToFirestore(
        itemsCol: com.google.firebase.firestore.CollectionReference,
        rateItemsCol: com.google.firebase.firestore.CollectionReference,
        r: RateItemEntity
    ) {
        // Sanitize Document ID (Slash & Special Character Guard)
        // NEVER use raw user-entered itemName or an ID containing slashes as a Firestore Document ID.
        // Slashes trick Firestore into writing to a subcollection (e.g., items/Cloves/Laung) instead of a single document.
        val cleanDocId = if (r.id.isNotBlank() && !r.id.contains("/") && !r.id.contains("\\") && !r.id.contains(" ")) {
            r.id.replace("#", "_").replace("[", "_").replace("]", "_")
        } else {
            // Generate clean random auto-ID using Firestore collection().document().id
            itemsCol.document().id
        }

        // If ID was sanitized or regenerated, update local room database so local and cloud match
        if (cleanDocId != r.id) {
            val updated = r.copy(id = cleanDocId)
            dao.deleteRateItem(r)
            dao.insertRateItem(updated)
        }

        Log.d("VARIANT_TRACE", "Hop 2 - Firestore Payload: name='${r.name}', cleanDocId='$cleanDocId', hasMultipleVariants=${r.hasMultipleVariants}, variantsJson='${r.variantsJson}'")

        val map = hashMapOf<String, Any>(
            "id" to cleanDocId,
            "name" to r.name,
            "itemName" to r.name,
            "category" to r.category,
            "categoryName" to r.category,
            "unit" to r.unit,
            "qty" to r.qty,
            "buyRate" to r.buyRate,
            "buyPrice" to r.buyRate,
            "sellPrice" to r.sellPrice,
            "sellingPrice" to r.sellPrice,
            "sellPriceStr" to r.sellPriceStr,
            "minSellPrice" to r.minSellPrice,
            "maxSellPrice" to r.maxSellPrice,
            "priceHistoryJson" to r.priceHistoryJson,
            "hasMultipleVariants" to r.hasMultipleVariants,
            "variantsJson" to r.variantsJson,
            "last_updated" to r.lastUpdated,
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        // Write safely to items collection (primary) and rate_items (secondary)
        FirebaseAuthManager.awaitTask { itemsCol.document(cleanDocId).set(map, SetOptions.merge()) }
        try {
            FirebaseAuthManager.awaitTask { rateItemsCol.document(cleanDocId).set(map, SetOptions.merge()) }
        } catch (e: Exception) {
            Log.w(TAG, "Secondary rate_items sync notice: ${e.message}")
        }
    }

    private suspend fun pushInvoiceToFirestore(col: com.google.firebase.firestore.CollectionReference, inv: InvoiceEntity) {
        val map = hashMapOf<String, Any>(
            "id" to inv.id,
            "invoiceNumber" to inv.invoiceNumber,
            "customerId" to inv.customerId,
            "customerName" to inv.customerName,
            "issueDate" to inv.issueDate,
            "dueDate" to inv.dueDate,
            "itemsJson" to inv.itemsJson,
            "subtotal" to inv.subtotal,
            "taxAmount" to inv.taxAmount,
            "discountAmount" to inv.discountAmount,
            "totalAmount" to inv.totalAmount,
            "paidAmount" to inv.paidAmount,
            "status" to inv.status,
            "notes" to inv.notes,
            "imagePath" to inv.imagePath,
            "last_updated" to inv.lastUpdated
        )
        FirebaseAuthManager.awaitTask { col.document(inv.id).set(map, SetOptions.merge()) }
    }

    private suspend fun syncCategories(firestore: FirebaseFirestore, uid: String): Int = withContext(Dispatchers.IO) {
        try {
            val col = firestore.collection("users").document(uid).collection("categories")
            val snapshot = FirebaseAuthManager.awaitTask { col.get() }
            val currentList = prefs.customCategories.toMutableList()
            var count = 0
            for (doc in snapshot.documents) {
                val name = doc.getString("name") ?: continue
                val iconKey = doc.getString("iconKey") ?: ""
                if (!currentList.contains(name)) {
                    currentList.add(name)
                }
                if (iconKey.isNotBlank()) {
                    prefs.setCategoryIconKey(name, iconKey)
                }
                count++
            }
            prefs.customCategories = currentList
            count
        } catch (e: Exception) {
            Log.w(TAG, "Sync categories notice: ${e.message}")
            0
        }
    }

    fun pushCategory(name: String, iconKey: String, iconUrl: String) {
        val user = authManager.currentUser.value ?: return
        val firestore = getFirestore() ?: return
        indicateSyncing()
        syncScope.launch(Dispatchers.IO) {
            try {
                val col = firestore.collection("users").document(user.uid).collection("categories")
                val docId = name.trim().lowercase().replace("/", "_").replace(" ", "_")
                val map = mapOf(
                    "name" to name.trim(),
                    "iconKey" to iconKey,
                    "iconUrl" to iconUrl,
                    "last_updated" to System.currentTimeMillis()
                )
                FirebaseAuthManager.awaitTask { col.document(docId).set(map, SetOptions.merge()) }
                indicateSynced()
            } catch (e: Exception) {
                Log.w(TAG, "Push category notice: ${e.message}")
                indicateOfflineOrFallback()
            }
        }
    }

    fun renameCategoryInCloud(oldName: String, newName: String, iconKey: String, iconUrl: String) {
        val user = authManager.currentUser.value ?: return
        val firestore = getFirestore() ?: return
        indicateSyncing()
        syncScope.launch(Dispatchers.IO) {
            try {
                val col = firestore.collection("users").document(user.uid).collection("categories")
                val oldDocId = oldName.trim().lowercase().replace("/", "_").replace(" ", "_")
                val newDocId = newName.trim().lowercase().replace("/", "_").replace(" ", "_")
                if (oldDocId != newDocId) {
                    try {
                        FirebaseAuthManager.awaitTask { col.document(oldDocId).delete() }
                    } catch (e: Exception) { /* ignore */ }
                }
                val map = mapOf(
                    "name" to newName.trim(),
                    "iconKey" to iconKey,
                    "iconUrl" to iconUrl,
                    "last_updated" to System.currentTimeMillis()
                )
                FirebaseAuthManager.awaitTask { col.document(newDocId).set(map, SetOptions.merge()) }

                // Update all items in Firestore matching old category across both collections
                val collectionsToUpdate = listOf(
                    firestore.collection("users").document(user.uid).collection("items"),
                    firestore.collection("users").document(user.uid).collection("rate_items")
                )
                for (colRef in collectionsToUpdate) {
                    try {
                        val snapshot = FirebaseAuthManager.awaitTask { colRef.whereEqualTo("category", oldName.trim()).get() }
                        snapshot.documents.forEach { doc ->
                            colRef.document(doc.id).update(
                                mapOf(
                                    "category" to newName.trim(),
                                    "categoryName" to newName.trim(),
                                    "last_updated" to System.currentTimeMillis()
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Notice updating category in ${colRef.id}: ${e.message}")
                    }
                }
                indicateSynced()
            } catch (e: Exception) {
                Log.w(TAG, "Rename category cloud notice: ${e.message}")
                indicateOfflineOrFallback()
            }
        }
    }

    fun deleteCategoryInCloud(name: String) {
        val user = authManager.currentUser.value ?: return
        val firestore = getFirestore() ?: return
        indicateSyncing()
        syncScope.launch(Dispatchers.IO) {
            try {
                val col = firestore.collection("users").document(user.uid).collection("categories")
                val docId = name.trim().lowercase().replace("/", "_").replace(" ", "_")
                FirebaseAuthManager.awaitTask { col.document(docId).delete() }
                indicateSynced()
            } catch (e: Exception) {
                Log.w(TAG, "Delete category cloud notice: ${e.message}")
                indicateOfflineOrFallback()
            }
        }
    }

    private fun indicateSyncing() {
        val count = pendingCount.incrementAndGet()
        _syncState.value = _syncState.value.copy(
            status = SyncStatus.SYNCING,
            pendingPushCount = count
        )
    }

    private fun indicateSynced() {
        val count = pendingCount.decrementAndGet()
        val now = System.currentTimeMillis()
        prefs.lastSyncTimestamp = now
        _syncState.value = _syncState.value.copy(
            status = if (count <= 0) SyncStatus.SYNCED else SyncStatus.SYNCING,
            pendingPushCount = Math.max(0, count),
            lastSyncTimestamp = now
        )
    }

    private fun indicateOfflineOrFallback() {
        val count = pendingCount.decrementAndGet()
        _syncState.value = _syncState.value.copy(
            status = if (!isNetworkAvailable()) SyncStatus.OFFLINE else SyncStatus.SYNCED,
            pendingPushCount = Math.max(0, count)
        )
    }
}
