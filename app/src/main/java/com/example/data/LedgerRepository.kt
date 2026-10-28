package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import com.example.sync.FirestoreSyncManager

data class DailyClosingSummary(
    val dateStr: String,
    val totalQrUpi: Double,
    val totalCash: Double,
    val totalOther: Double,
    val totalCollections: Double,
    val totalDueIssued: Double,
    val totalTransactionsCount: Int,
    val formattedShareText: String
)

class LedgerRepository(
    private val dao: LedgerDao,
    var syncManager: FirestoreSyncManager? = null
) {

    val allCustomers: Flow<List<CustomerEntity>> = dao.getAllCustomers().distinctUntilChanged()
    val allTransactions: Flow<List<TransactionEntity>> = dao.getAllTransactions().distinctUntilChanged()
    val allInvoices: Flow<List<InvoiceEntity>> = dao.getAllInvoices().distinctUntilChanged()
    val allAuditLogs: Flow<List<AuditLogEntity>> = dao.getAllAuditLogs().distinctUntilChanged()
    val allRateItems: Flow<List<RateItemEntity>> = dao.getAllRateItems().distinctUntilChanged()
    val allNotes: Flow<List<NoteEntity>> = dao.getAllNotes().distinctUntilChanged()

    // Combined Flow: Customer list enriched with computed running balance
    val customersWithBalance: Flow<List<CustomerWithBalance>> = combine(
        allCustomers,
        allTransactions
    ) { customers, transactions ->
        val txnsByCustomer = transactions.groupBy { it.customerId }
        customers.map { customer ->
            val custTxns = txnsByCustomer[customer.id] ?: emptyList()
            var totalGave = 0.0 // Money given / credit out
            var totalGot = 0.0  // Money received / payment in
            var latestTxnTs: Long? = null

            for (txn in custTxns) {
                when (txn.type) {
                    TransactionType.GAVE_CREDIT.name, TransactionType.INVOICE_BILL.name -> totalGave += txn.amount
                    TransactionType.GOT_PAYMENT.name -> totalGot += txn.amount
                }
                if (latestTxnTs == null || txn.date > latestTxnTs) {
                    latestTxnTs = txn.date
                }
            }
            val net = totalGave - totalGot

            CustomerWithBalance(
                customer = customer,
                netBalance = net,
                lastTransactionDate = latestTxnTs
            )
        }
    }.distinctUntilChanged().flowOn(Dispatchers.Default)

    // Business Summary Metrics Flow including 7-day sparkline trend points
    val businessSummary: Flow<BusinessSummary> = combine(
        customersWithBalance,
        allInvoices,
        allTransactions
    ) { custBalances, invoices, transactions ->
        var totalReceivable = 0.0
        var totalPayable = 0.0

        for (cb in custBalances) {
            if (cb.netBalance > 0) {
                totalReceivable += cb.netBalance
            } else if (cb.netBalance < 0) {
                totalPayable += Math.abs(cb.netBalance)
            }
        }

        val netCashflow = totalReceivable - totalPayable
        val now = System.currentTimeMillis()
        var unpaidCount = 0
        var overdueCount = 0
        var totalInvoicesSum = 0.0
        for (inv in invoices) {
            totalInvoicesSum += inv.totalAmount
            if (inv.status == InvoiceEntity.STATUS_UNPAID || inv.status == InvoiceEntity.STATUS_PARTIAL) {
                unpaidCount++
                if (inv.dueDate < now) {
                    overdueCount++
                }
            }
        }

        // Compute 7-day sparkline trend points in single pass over recent transactions
        val dayMillis = 24 * 3600 * 1000L
        val windowStart = now - (6 * dayMillis)
        val recentTxns = transactions.filter { it.date >= windowStart }
        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val trendPoints = ArrayList<DailyTrendPoint>(7)

        for (i in 6 downTo 0) {
            val dayStart = now - (i * dayMillis)
            val dayEnd = dayStart + dayMillis
            var dayVolume = 0.0
            for (t in recentTxns) {
                if (t.date in dayStart..dayEnd) {
                    if (t.type == TransactionType.GAVE_CREDIT.name || t.type == TransactionType.INVOICE_BILL.name) {
                        dayVolume += t.amount
                    } else if (t.type == TransactionType.GOT_PAYMENT.name) {
                        dayVolume -= t.amount
                    }
                }
            }
            trendPoints.add(
                DailyTrendPoint(
                    dayLabel = dayFormat.format(Date(dayStart)),
                    netVolume = dayVolume,
                    timestamp = dayStart
                )
            )
        }

        BusinessSummary(
            totalReceivable = totalReceivable,
            totalPayable = totalPayable,
            netCashflow = netCashflow,
            unpaidInvoiceCount = unpaidCount,
            overdueInvoiceCount = overdueCount,
            customerCount = custBalances.size,
            totalInvoicesAmount = totalInvoicesSum,
            sevenDayTrend = trendPoints
        )
    }.distinctUntilChanged().flowOn(Dispatchers.Default)

    fun getTransactionsForCustomer(customerId: String): Flow<List<TransactionEntity>> {
        return dao.getTransactionsForCustomer(customerId)
    }

    suspend fun getCustomerById(id: String): CustomerEntity? {
        return withContext(Dispatchers.IO) {
            dao.getCustomerById(id)
        }
    }

    suspend fun insertCustomer(customer: CustomerEntity): Long {
        return withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val updated = customer.copy(lastUpdated = now)
            val id = dao.insertCustomer(updated)
            syncManager?.pushCustomer(updated)
            id
        }
    }

    suspend fun updateCustomer(customer: CustomerEntity) {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val updated = customer.copy(lastUpdated = now)
            dao.updateCustomer(updated)
            syncManager?.pushCustomer(updated)
            val linkedNotes = dao.getAllNotesList().filter { it.linkedCustomerId == customer.id && it.linkedCustomerName != customer.name }
            if (linkedNotes.isNotEmpty()) {
                val updatedNotes = linkedNotes.map { it.copy(linkedCustomerName = customer.name, updatedAt = now) }
                dao.insertNotes(updatedNotes)
                updatedNotes.forEach { syncManager?.pushNote(it) }
            }
        }
    }

    suspend fun deleteCustomerWithData(customerId: String) {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val linkedNotes = dao.getAllNotesList().filter { it.linkedCustomerId == customerId }
            if (linkedNotes.isNotEmpty()) {
                dao.unlinkCustomerFromNotes(customerId, now)
                linkedNotes.forEach { note ->
                    syncManager?.pushNote(note.copy(linkedCustomerId = "", linkedCustomerName = "", updatedAt = now))
                }
            }
            dao.deleteTransactionsForCustomer(customerId)
            dao.deleteCustomerById(customerId)
            syncManager?.deleteCustomer(customerId)
        }
    }

    suspend fun deleteCustomers(ids: Set<String>) {
        withContext(Dispatchers.IO) {
            ids.forEach { id ->
                deleteCustomerWithData(id)
            }
        }
    }

    // --- NOTES (PRIVATE NOTEPAD - NEVER LOGGED TO TRASH/AUDIT OR EXPORTS) ---
    suspend fun getNoteById(id: String): NoteEntity? = withContext(Dispatchers.IO) {
        dao.getNoteById(id)
    }

    suspend fun upsertNote(note: NoteEntity) {
        withContext(Dispatchers.IO) {
            dao.insertNote(note)
            syncManager?.pushNote(note)
        }
    }

    suspend fun upsertNotes(notes: List<NoteEntity>) {
        if (notes.isEmpty()) return
        withContext(Dispatchers.IO) {
            dao.insertNotes(notes)
            notes.forEach { syncManager?.pushNote(it) }
        }
    }

    suspend fun deleteNotePermanently(noteId: String) {
        withContext(Dispatchers.IO) {
            dao.deleteNoteById(noteId)
            syncManager?.deleteNote(noteId)
        }
    }

    suspend fun deleteNotesPermanently(noteIds: List<String>) {
        if (noteIds.isEmpty()) return
        withContext(Dispatchers.IO) {
            dao.deleteNotesByIds(noteIds)
            syncManager?.deleteNotes(noteIds)
        }
    }

    suspend fun reassignNoteLabelToGeneral(deletedLabel: String) {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val affected = dao.getAllNotesList().filter { it.label.equals(deletedLabel, ignoreCase = true) }
            if (affected.isNotEmpty()) {
                dao.reassignNoteLabel(deletedLabel, NoteEntity.LABEL_GENERAL, now)
                affected.forEach { note ->
                    syncManager?.pushNote(note.copy(label = NoteEntity.LABEL_GENERAL, updatedAt = now))
                }
            }
        }
    }

    suspend fun insertTransaction(transaction: TransactionEntity): Long {
        return withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val updated = transaction.copy(lastUpdated = now)
            val id = dao.insertTransaction(updated)
            syncManager?.pushTransaction(updated)
            id
        }
    }

    suspend fun updateTransaction(transaction: TransactionEntity) {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val updated = transaction.copy(lastUpdated = now)
            dao.updateTransaction(updated)
            syncManager?.pushTransaction(updated)
        }
    }

    suspend fun deleteTransaction(transaction: TransactionEntity) {
        withContext(Dispatchers.IO) {
            val json = JSONObject().apply {
                put("type", "TRANSACTION")
                put("id", transaction.id)
                put("customerId", transaction.customerId)
                put("amount", transaction.amount)
                put("txnType", transaction.type)
                put("description", transaction.description)
                put("date", transaction.date)
                put("invoiceRef", transaction.invoiceRef)
                put("paymentMethod", transaction.paymentMethod)
            }.toString()

            dao.insertAuditLog(
                AuditLogEntity(
                    actionType = "DELETE_TRANSACTION",
                    title = "Deleted Transaction: ${transaction.description.ifBlank { transaction.type }}",
                    description = "Amount: ${transaction.amount}",
                    detailsJson = json
                )
            )

            dao.deleteTransaction(transaction)
            syncManager?.deleteTransaction(transaction.id)
        }
    }

    suspend fun deleteTransactions(transactions: List<TransactionEntity>) {
        withContext(Dispatchers.IO) {
            transactions.forEach { deleteTransaction(it) }
        }
    }

    suspend fun insertInvoice(invoice: InvoiceEntity): Long {
        return withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val updated = invoice.copy(lastUpdated = now)
            val id = dao.insertInvoice(updated)
            syncManager?.pushInvoice(updated)
            id
        }
    }

    suspend fun updateInvoice(invoice: InvoiceEntity) {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val updated = invoice.copy(lastUpdated = now)
            dao.updateInvoice(updated)
            syncManager?.pushInvoice(updated)
        }
    }

    suspend fun deleteInvoice(invoice: InvoiceEntity) {
        withContext(Dispatchers.IO) {
            val json = JSONObject().apply {
                put("type", "INVOICE")
                put("id", invoice.id)
                put("invoiceNumber", invoice.invoiceNumber)
                put("customerId", invoice.customerId)
                put("customerName", invoice.customerName)
                put("issueDate", invoice.issueDate)
                put("dueDate", invoice.dueDate)
                put("itemsJson", invoice.itemsJson)
                put("subtotal", invoice.subtotal)
                put("taxAmount", invoice.taxAmount)
                put("discountAmount", invoice.discountAmount)
                put("totalAmount", invoice.totalAmount)
                put("paidAmount", invoice.paidAmount)
                put("status", invoice.status)
                put("notes", invoice.notes)
            }.toString()

            dao.insertAuditLog(
                AuditLogEntity(
                    actionType = "DELETE_INVOICE",
                    title = "Deleted Invoice #${invoice.invoiceNumber}",
                    description = "Customer: ${invoice.customerName}, Total: ${invoice.totalAmount}",
                    detailsJson = json
                )
            )

            dao.deleteInvoice(invoice)
            syncManager?.deleteInvoice(invoice.id)
        }
    }

    suspend fun deleteInvoices(invoices: List<InvoiceEntity>) {
        withContext(Dispatchers.IO) {
            invoices.forEach { deleteInvoice(it) }
        }
    }

    suspend fun generateNextInvoiceNumber(): String {
        return withContext(Dispatchers.IO) {
            val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
            val dateStr = dateFormat.format(Date())
            val prefix = "INV-$dateStr-"
            val latest = dao.getLatestInvoiceWithPrefix("$prefix%")
            val nextSeq = if (latest != null) {
                val lastNumStr = latest.invoiceNumber.removePrefix(prefix).takeWhile { it.isDigit() }
                (lastNumStr.toIntOrNull() ?: 0) + 1
            } else {
                1
            }
            String.format(Locale.US, "INV-%s-%03d", dateStr, nextSeq)
        }
    }

    suspend fun recordInvoicePayment(invoice: InvoiceEntity, paymentAmount: Double, method: String) {
        withContext(Dispatchers.IO) {
            val newPaidAmount = invoice.paidAmount + paymentAmount
            val newStatus = if (newPaidAmount >= invoice.totalAmount) {
                InvoiceEntity.STATUS_PAID
            } else {
                InvoiceEntity.STATUS_PARTIAL
            }

            val updatedInvoice = invoice.copy(
                paidAmount = newPaidAmount,
                status = newStatus
            )
            dao.updateInvoice(updatedInvoice)

            // Record a GOT_PAYMENT ledger transaction
            dao.insertTransaction(
                TransactionEntity(
                    customerId = invoice.customerId,
                    amount = paymentAmount,
                    type = TransactionType.GOT_PAYMENT.name,
                    description = "Payment for Invoice #${invoice.invoiceNumber}",
                    invoiceRef = invoice.invoiceNumber,
                    paymentMethod = method
                )
            )
        }
    }

    // --- RATE ITEMS & PRICE HISTORY ---
    fun getPriceHistoryForItem(itemId: String): Flow<List<PriceHistoryEntity>> {
        return dao.getPriceHistoryForItem(itemId)
    }

    private fun appendPriceHistoryJson(existingJson: String, buyRate: Double, sellPrice: Double, sellPriceStr: String, timestamp: Long): String {
        val list = mutableListOf<JSONObject>()
        try {
            if (existingJson.isNotBlank() && existingJson != "[]") {
                val arr = JSONArray(existingJson)
                for (i in 0 until arr.length()) {
                    list.add(arr.getJSONObject(i))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val latest = list.firstOrNull()
        val cleanSellStr = sellPriceStr.trim()
        if (latest != null) {
            val prevBuy = latest.optDouble("buyRate", -1.0)
            val prevSell = latest.optDouble("sellPrice", -1.0)
            val prevSellStr = latest.optString("sellPriceStr", "").trim()
            if (kotlin.math.abs(prevBuy - buyRate) < 0.005 &&
                kotlin.math.abs(prevSell - sellPrice) < 0.005 &&
                (prevSellStr == cleanSellStr || (prevSellStr.isBlank() && cleanSellStr.isBlank()))
            ) {
                return existingJson
            }
        }
        val newObj = JSONObject().apply {
            put("date", timestamp)
            put("buyRate", buyRate)
            put("sellPrice", sellPrice)
            put("sellPriceStr", cleanSellStr)
        }
        list.add(0, newObj)
        val resultArr = JSONArray()
        list.take(50).forEach { resultArr.put(it) }
        return resultArr.toString()
    }

    suspend fun ensureStartingEntriesForRateItems() {
        withContext(Dispatchers.IO) {
            try {
                // Purge placeholder test invoices named "x" or "y"
                val existingInvoices = dao.getAllInvoices().first()
                for (inv in existingInvoices) {
                    val cleanCust = inv.customerName.trim().lowercase(Locale.US)
                    if (cleanCust == "x" || cleanCust == "y" || cleanCust.startsWith("x (") || cleanCust.startsWith("y (")) {
                        dao.deleteInvoice(inv)
                    }
                }

                val allItems = dao.getAllRateItemsList()
                for (item in allItems) {
                    val hasHistoryJson = item.priceHistoryJson.isNotBlank() && item.priceHistoryJson != "[]"
                    val hasVariantsEncoded = item.variantsJson.isNotBlank() && item.variantsJson != "[]"
                    var needsUpdate = false
                    var updated = item

                    if (!hasHistoryJson) {
                        val date = if (item.lastUpdated > 0) item.lastUpdated else System.currentTimeMillis()
                        val startingJson = appendPriceHistoryJson("", item.buyRate, item.sellPrice, item.sellPriceStr, date)
                        updated = updated.copy(
                            priceHistoryJson = startingJson,
                            lastUpdated = date
                        )
                        needsUpdate = true
                        dao.insertPriceHistory(
                            PriceHistoryEntity(
                                itemId = item.id,
                                buyRate = item.buyRate,
                                sellPrice = item.sellPrice,
                                sellPriceStr = item.sellPriceStr,
                                timestamp = date
                            )
                        )
                    }

                    if (hasVariantsEncoded && !updated.hasMultipleVariants) {
                        updated = updated.copy(hasMultipleVariants = true)
                        needsUpdate = true
                    }

                    if (needsUpdate) {
                        dao.updateRateItem(updated)
                        syncManager?.pushRateItem(updated)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun insertRateItem(item: RateItemEntity): Long {
        return withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val (minP, maxP) = RateItemEntity.computeMinMax(item.sellPrice, item.sellPriceStr)
            val historyJson = if (item.priceHistoryJson.isBlank() || item.priceHistoryJson == "[]") {
                appendPriceHistoryJson("", item.buyRate, item.sellPrice, item.sellPriceStr, now)
            } else {
                item.priceHistoryJson
            }
            val updated = item.copy(
                minSellPrice = minP,
                maxSellPrice = maxP,
                priceHistoryJson = historyJson,
                lastUpdated = now
            )
            val rowId = dao.insertRateItem(updated)
            // Log to price history table
            dao.insertPriceHistory(
                PriceHistoryEntity(
                    itemId = item.id,
                    buyRate = item.buyRate,
                    sellPrice = item.sellPrice,
                    sellPriceStr = item.sellPriceStr,
                    timestamp = now
                )
            )
            rowId.also {
                syncManager?.pushRateItem(updated)
            }
        }
    }

    suspend fun updateRateItem(item: RateItemEntity) {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val prevItem = dao.getRateItemById(item.id)
            val priceChanged = prevItem == null ||
                kotlin.math.abs(prevItem.buyRate - item.buyRate) >= 0.005 ||
                kotlin.math.abs(prevItem.sellPrice - item.sellPrice) >= 0.005 ||
                prevItem.sellPriceStr.trim() != item.sellPriceStr.trim()
            val (minP, maxP) = RateItemEntity.computeMinMax(item.sellPrice, item.sellPriceStr)
            val historyJson = if (priceChanged) {
                appendPriceHistoryJson(item.priceHistoryJson, item.buyRate, item.sellPrice, item.sellPriceStr, now)
            } else {
                item.priceHistoryJson
            }
            val updated = item.copy(
                minSellPrice = minP,
                maxSellPrice = maxP,
                priceHistoryJson = historyJson,
                lastUpdated = now
            )
            dao.updateRateItem(updated)
            if (priceChanged) {
                dao.insertPriceHistory(
                    PriceHistoryEntity(
                        itemId = item.id,
                        buyRate = item.buyRate,
                        sellPrice = item.sellPrice,
                        sellPriceStr = item.sellPriceStr,
                        timestamp = now
                    )
                )
            }
            syncManager?.pushRateItem(updated)
        }
    }

    suspend fun deleteRateItem(item: RateItemEntity) {
        withContext(Dispatchers.IO) {
            dao.deletePriceHistoryForItem(item.id)
            dao.deleteRateItem(item)
            syncManager?.deleteRateItem(item.id)
        }
    }

    suspend fun deleteRateItems(items: List<RateItemEntity>) {
        withContext(Dispatchers.IO) {
            items.forEach { deleteRateItem(it) }
        }
    }

    suspend fun bulkDeleteRateItems(ids: List<String>) {
        withContext(Dispatchers.IO) {
            ids.forEach { id ->
                dao.deletePriceHistoryForItem(id)
                syncManager?.deleteRateItem(id)
            }
            dao.deleteRateItemsByIds(ids)
        }
    }

    suspend fun bulkAssignCategory(ids: List<String>, category: String) {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            ids.forEach { id ->
                val item = dao.getRateItemById(id)
                if (item != null) {
                    val updated = item.copy(category = category, lastUpdated = now)
                    dao.updateRateItem(updated)
                    syncManager?.pushRateItem(updated)
                }
            }
        }
    }

    suspend fun renameCategory(oldCategory: String, newCategory: String) {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            dao.renameCategoryForItems(oldCategory, newCategory, now)
            val affectedItems = dao.getAllRateItemsList().filter { it.category.equals(newCategory, ignoreCase = true) }
            affectedItems.forEach { item ->
                syncManager?.pushRateItem(item)
            }
        }
    }

    suspend fun bulkAdjustPrice(ids: List<String>, percentDelta: Double) {
        withContext(Dispatchers.IO) {
            ids.forEach { id ->
                val item = dao.getRateItemById(id)
                if (item != null) {
                    val multiplier = 1.0 + (percentDelta / 100.0)
                    val newBuy = (item.buyRate * multiplier).coerceAtLeast(0.0)
                    val newSell = (item.sellPrice * multiplier).coerceAtLeast(0.0)
                    val newSellStr = if (newSell % 1.0 == 0.0) newSell.toLong().toString() else String.format(Locale.US, "%.2f", newSell)
                    val (minP, maxP) = RateItemEntity.computeMinMax(newSell, newSellStr)

                    val updated = item.copy(
                        buyRate = newBuy,
                        sellPrice = newSell,
                        sellPriceStr = newSellStr,
                        minSellPrice = minP,
                        maxSellPrice = maxP,
                        lastUpdated = System.currentTimeMillis()
                    )
                    dao.updateRateItem(updated)
                    dao.insertPriceHistory(
                        PriceHistoryEntity(
                            itemId = item.id,
                            buyRate = newBuy,
                            sellPrice = newSell,
                            sellPriceStr = newSellStr,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    // --- AUDIT & RESTORE ---
    suspend fun deleteAuditLog(id: String) {
        withContext(Dispatchers.IO) {
            dao.deleteAuditLogById(id)
        }
    }

    suspend fun clearAuditLogs() {
        withContext(Dispatchers.IO) {
            dao.clearAllAuditLogs()
        }
    }

    suspend fun restoreAuditLog(log: AuditLogEntity): Boolean {
        if (log.isRestored) return false
        return withContext(Dispatchers.IO) {
            try {
                val obj = JSONObject(log.detailsJson)
                val type = obj.optString("type")

                when (type) {
                    "CUSTOMER" -> {
                        val cObj = obj.getJSONObject("customer")
                        dao.insertCustomer(
                            CustomerEntity(
                                id = cObj.optString("id", UUID.randomUUID().toString()),
                                name = cObj.getString("name"),
                                phone = cObj.optString("phone", ""),
                                businessName = cObj.optString("businessName", ""),
                                partyType = cObj.optString("partyType", CustomerEntity.PARTY_TYPE_CUSTOMER),
                                notes = cObj.optString("notes", ""),
                                createdAt = cObj.optLong("createdAt", System.currentTimeMillis())
                            )
                        )
                    }
                    "TRANSACTION" -> {
                        dao.insertTransaction(
                            TransactionEntity(
                                id = obj.optString("id", UUID.randomUUID().toString()),
                                customerId = obj.getString("customerId"),
                                amount = obj.getDouble("amount"),
                                type = obj.getString("txnType"),
                                description = obj.optString("description", "") + " (Restored)",
                                date = obj.optLong("date", System.currentTimeMillis()),
                                invoiceRef = obj.optString("invoiceRef", ""),
                                paymentMethod = obj.optString("paymentMethod", "CASH")
                            )
                        )
                    }
                    "INVOICE" -> {
                        dao.insertInvoice(
                            InvoiceEntity(
                                id = obj.optString("id", UUID.randomUUID().toString()),
                                invoiceNumber = obj.getString("invoiceNumber") + "-R",
                                customerId = obj.getString("customerId"),
                                customerName = obj.getString("customerName"),
                                issueDate = obj.optLong("issueDate", System.currentTimeMillis()),
                                dueDate = obj.optLong("dueDate", System.currentTimeMillis() + 864000000),
                                itemsJson = obj.optString("itemsJson", "[]"),
                                subtotal = obj.optDouble("subtotal", 0.0),
                                taxAmount = obj.optDouble("taxAmount", 0.0),
                                discountAmount = obj.optDouble("discountAmount", 0.0),
                                totalAmount = obj.optDouble("totalAmount", 0.0),
                                paidAmount = obj.optDouble("paidAmount", 0.0),
                                status = obj.optString("status", InvoiceEntity.STATUS_UNPAID),
                                notes = obj.optString("notes", "") + " (Restored)"
                            )
                        )
                    }
                }

                dao.updateAuditLog(log.copy(isRestored = true))
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    suspend fun importDataFromCsv(csvStr: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val backup = com.example.utils.ExportUtils.parseCompleteDatabaseCsv(csvStr)
                if (backup.customers.isNotEmpty() || backup.transactions.isNotEmpty() || backup.invoices.isNotEmpty() || backup.rateItems.isNotEmpty()) {
                    val now = System.currentTimeMillis()

                    // 1. Deduplicate & import Customers
                    val existingCustomers = dao.getAllCustomersList()
                    val custNameMap = existingCustomers.associateBy { it.name.trim().lowercase(java.util.Locale.getDefault()) }
                    val custIdMap = existingCustomers.associateBy { it.id }
                    backup.customers.forEach { incoming ->
                        val existing = custIdMap[incoming.id] ?: custNameMap[incoming.name.trim().lowercase(java.util.Locale.getDefault())]
                        if (existing != null) {
                            dao.updateCustomer(incoming.copy(id = existing.id))
                        } else {
                            dao.insertCustomer(incoming)
                        }
                    }

                    // 2. Deduplicate & import Transactions
                    val existingTxns = dao.getAllTransactionsList()
                    val existingTxnIds = existingTxns.map { it.id }.toSet()
                    backup.transactions.forEach { t ->
                        if (!existingTxnIds.contains(t.id)) {
                            dao.insertTransaction(t)
                        }
                    }

                    // 3. Deduplicate & import Invoices
                    val existingInvoices = dao.getAllInvoicesList()
                    val invNumMap = existingInvoices.associateBy { it.invoiceNumber.trim() }
                    val invIdMap = existingInvoices.associateBy { it.id }
                    backup.invoices.forEach { incoming ->
                        val existing = invIdMap[incoming.id] ?: invNumMap[incoming.invoiceNumber.trim()]
                        if (existing != null) {
                            dao.updateInvoice(incoming.copy(id = existing.id))
                        } else {
                            dao.insertInvoice(incoming)
                        }
                    }

                    // 4. Deduplicate & import Rate Items with Price Revisions
                    val existingRates = dao.getAllRateItemsList()
                    val rateNameMap = existingRates.associateBy { it.name.trim().lowercase(java.util.Locale.getDefault()) }
                    val rateIdMap = existingRates.associateBy { it.id }

                    backup.rateItems.forEach { incoming ->
                        val existing = rateIdMap[incoming.id] ?: rateNameMap[incoming.name.trim().lowercase(java.util.Locale.getDefault())]
                        val (minP, maxP) = RateItemEntity.computeMinMax(incoming.sellPrice, incoming.sellPriceStr)

                        if (existing != null) {
                            val updatedHistoryJson = appendPriceHistoryJson(
                                existing.priceHistoryJson,
                                incoming.buyRate,
                                incoming.sellPrice,
                                incoming.sellPriceStr,
                                now
                            )
                            val mergedItem = incoming.copy(
                                id = existing.id,
                                minSellPrice = minP,
                                maxSellPrice = maxP,
                                priceHistoryJson = updatedHistoryJson,
                                lastUpdated = now
                            )
                            dao.updateRateItem(mergedItem)
                            dao.insertPriceHistory(
                                PriceHistoryEntity(
                                    itemId = existing.id,
                                    buyRate = incoming.buyRate,
                                    sellPrice = incoming.sellPrice,
                                    sellPriceStr = incoming.sellPriceStr,
                                    timestamp = now
                                )
                            )
                        } else {
                            val initialHistoryJson = appendPriceHistoryJson(
                                incoming.priceHistoryJson,
                                incoming.buyRate,
                                incoming.sellPrice,
                                incoming.sellPriceStr,
                                now
                            )
                            val newItem = incoming.copy(
                                minSellPrice = minP,
                                maxSellPrice = maxP,
                                priceHistoryJson = initialHistoryJson,
                                lastUpdated = now
                            )
                            dao.insertRateItem(newItem)
                            dao.insertPriceHistory(
                                PriceHistoryEntity(
                                    itemId = incoming.id,
                                    buyRate = incoming.buyRate,
                                    sellPrice = incoming.sellPrice,
                                    sellPriceStr = incoming.sellPriceStr,
                                    timestamp = now
                                )
                            )
                        }
                    }

                    true
                } else {
                    importDataFromJson(csvStr)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                importDataFromJson(csvStr)
            }
        }
    }

    suspend fun exportDataToJson(
        customers: List<CustomerEntity>,
        transactions: List<TransactionEntity>,
        invoices: List<InvoiceEntity>,
        rateItems: List<RateItemEntity> = emptyList()
    ): String {
        return withContext(Dispatchers.Default) {
            val root = JSONObject()

            val custArray = JSONArray()
            customers.forEach { c ->
                val obj = JSONObject()
                obj.put("id", c.id)
                obj.put("name", c.name)
                obj.put("phone", c.phone)
                obj.put("businessName", c.businessName)
                obj.put("partyType", c.partyType)
                obj.put("notes", c.notes)
                obj.put("createdAt", c.createdAt)
                custArray.put(obj)
            }
            root.put("customers", custArray)

            val txnArray = JSONArray()
            transactions.forEach { t ->
                val obj = JSONObject()
                obj.put("id", t.id)
                obj.put("customerId", t.customerId)
                obj.put("amount", t.amount)
                obj.put("type", t.type)
                obj.put("description", t.description)
                obj.put("date", t.date)
                obj.put("invoiceRef", t.invoiceRef)
                obj.put("paymentMethod", t.paymentMethod)
                txnArray.put(obj)
            }
            root.put("transactions", txnArray)

            val invArray = JSONArray()
            invoices.forEach { inv ->
                val obj = JSONObject()
                obj.put("id", inv.id)
                obj.put("invoiceNumber", inv.invoiceNumber)
                obj.put("customerId", inv.customerId)
                obj.put("customerName", inv.customerName)
                obj.put("issueDate", inv.issueDate)
                obj.put("dueDate", inv.dueDate)
                obj.put("itemsJson", inv.itemsJson)
                obj.put("subtotal", inv.subtotal)
                obj.put("taxAmount", inv.taxAmount)
                obj.put("discountAmount", inv.discountAmount)
                obj.put("totalAmount", inv.totalAmount)
                obj.put("paidAmount", inv.paidAmount)
                obj.put("status", inv.status)
                obj.put("notes", inv.notes)
                obj.put("imagePath", inv.imagePath)
                invArray.put(obj)
            }
            root.put("invoices", invArray)

            val rateArray = JSONArray()
            rateItems.forEach { r ->
                val obj = JSONObject()
                obj.put("id", r.id)
                obj.put("name", r.name)
                obj.put("category", r.category)
                obj.put("unit", r.unit)
                obj.put("qty", r.qty)
                obj.put("buyRate", r.buyRate)
                obj.put("sellPrice", r.sellPrice)
                obj.put("sellPriceStr", r.sellPriceStr)
                obj.put("priceHistoryJson", r.priceHistoryJson)
                obj.put("lastUpdated", r.lastUpdated)
                rateArray.put(obj)
            }
            root.put("rateItems", rateArray)

            root.toString(2)
        }
    }

    suspend fun importDataFromJson(jsonStr: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val root = JSONObject(jsonStr)
                val now = System.currentTimeMillis()

                if (root.has("customers")) {
                    val existingCustomers = dao.getAllCustomersList()
                    val custNameMap = existingCustomers.associateBy { it.name.trim().lowercase(java.util.Locale.getDefault()) }
                    val custIdMap = existingCustomers.associateBy { it.id }

                    val custArray = root.getJSONArray("customers")
                    for (i in 0 until custArray.length()) {
                        val obj = custArray.getJSONObject(i)
                        val id = if (obj.has("id") && obj.getString("id").isNotBlank()) obj.getString("id") else UUID.randomUUID().toString()
                        val name = obj.getString("name")
                        val incoming = CustomerEntity(
                            id = id,
                            name = name,
                            phone = obj.optString("phone", ""),
                            businessName = obj.optString("businessName", ""),
                            partyType = obj.optString("partyType", CustomerEntity.PARTY_TYPE_CUSTOMER),
                            notes = obj.optString("notes", ""),
                            createdAt = obj.optLong("createdAt", now)
                        )
                        val existing = custIdMap[id] ?: custNameMap[name.trim().lowercase(java.util.Locale.getDefault())]
                        if (existing != null) {
                            dao.updateCustomer(incoming.copy(id = existing.id))
                        } else {
                            dao.insertCustomer(incoming)
                        }
                    }
                }

                if (root.has("transactions")) {
                    val existingTxns = dao.getAllTransactionsList()
                    val existingTxnIds = existingTxns.map { it.id }.toSet()

                    val txnArray = root.getJSONArray("transactions")
                    for (i in 0 until txnArray.length()) {
                        val obj = txnArray.getJSONObject(i)
                        val id = if (obj.has("id") && obj.getString("id").isNotBlank()) obj.getString("id") else UUID.randomUUID().toString()
                        if (!existingTxnIds.contains(id)) {
                            dao.insertTransaction(
                                TransactionEntity(
                                    id = id,
                                    customerId = obj.getString("customerId"),
                                    amount = obj.getDouble("amount"),
                                    type = obj.getString("type"),
                                    description = obj.optString("description", ""),
                                    date = obj.optLong("date", now),
                                    invoiceRef = obj.optString("invoiceRef", ""),
                                    paymentMethod = obj.optString("paymentMethod", "CASH")
                                )
                            )
                        }
                    }
                }

                if (root.has("invoices")) {
                    val existingInvoices = dao.getAllInvoicesList()
                    val invNumMap = existingInvoices.associateBy { it.invoiceNumber.trim() }
                    val invIdMap = existingInvoices.associateBy { it.id }

                    val invArray = root.getJSONArray("invoices")
                    for (i in 0 until invArray.length()) {
                        val obj = invArray.getJSONObject(i)
                        val id = if (obj.has("id") && obj.getString("id").isNotBlank()) obj.getString("id") else UUID.randomUUID().toString()
                        val invoiceNum = obj.getString("invoiceNumber")
                        val incoming = InvoiceEntity(
                            id = id,
                            invoiceNumber = invoiceNum,
                            customerId = obj.getString("customerId"),
                            customerName = obj.getString("customerName"),
                            issueDate = obj.optLong("issueDate", now),
                            dueDate = obj.optLong("dueDate", now + 864000000),
                            itemsJson = obj.optString("itemsJson", "[]"),
                            subtotal = obj.optDouble("subtotal", 0.0),
                            taxAmount = obj.optDouble("taxAmount", 0.0),
                            discountAmount = obj.optDouble("discountAmount", 0.0),
                            totalAmount = obj.optDouble("totalAmount", 0.0),
                            paidAmount = obj.optDouble("paidAmount", 0.0),
                            status = obj.optString("status", InvoiceEntity.STATUS_UNPAID),
                            notes = obj.optString("notes", ""),
                            imagePath = obj.optString("imagePath", "")
                        )
                        val existing = invIdMap[id] ?: invNumMap[invoiceNum.trim()]
                        if (existing != null) {
                            dao.updateInvoice(incoming.copy(id = existing.id))
                        } else {
                            dao.insertInvoice(incoming)
                        }
                    }
                }

                if (root.has("rateItems")) {
                    val existingRates = dao.getAllRateItemsList()
                    val rateNameMap = existingRates.associateBy { it.name.trim().lowercase(java.util.Locale.getDefault()) }
                    val rateIdMap = existingRates.associateBy { it.id }

                    val rateArray = root.getJSONArray("rateItems")
                    for (i in 0 until rateArray.length()) {
                        val obj = rateArray.getJSONObject(i)
                        val id = if (obj.has("id") && obj.getString("id").isNotBlank()) obj.getString("id") else UUID.randomUUID().toString()
                        val name = obj.getString("name")
                        val sellPrice = obj.optDouble("sellPrice", 0.0)
                        val sellPriceStr = obj.optString("sellPriceStr", "")
                        val buyRate = obj.optDouble("buyRate", 0.0)
                        val (minP, maxP) = RateItemEntity.computeMinMax(sellPrice, sellPriceStr)
                        val existing = rateIdMap[id] ?: rateNameMap[name.trim().lowercase(java.util.Locale.getDefault())]

                        if (existing != null) {
                            val updatedHistoryJson = appendPriceHistoryJson(
                                existing.priceHistoryJson,
                                buyRate,
                                sellPrice,
                                sellPriceStr,
                                now
                            )
                            val mergedItem = RateItemEntity(
                                id = existing.id,
                                name = name,
                                category = obj.optString("category", existing.category),
                                unit = obj.optString("unit", existing.unit),
                                qty = obj.optDouble("qty", existing.qty),
                                buyRate = buyRate,
                                sellPrice = sellPrice,
                                sellPriceStr = sellPriceStr,
                                minSellPrice = minP,
                                maxSellPrice = maxP,
                                priceHistoryJson = updatedHistoryJson,
                                lastUpdated = now
                            )
                            dao.updateRateItem(mergedItem)
                            dao.insertPriceHistory(
                                PriceHistoryEntity(
                                    itemId = existing.id,
                                    buyRate = buyRate,
                                    sellPrice = sellPrice,
                                    sellPriceStr = sellPriceStr,
                                    timestamp = now
                                )
                            )
                        } else {
                            val initialHistoryJson = appendPriceHistoryJson(
                                obj.optString("priceHistoryJson", ""),
                                buyRate,
                                sellPrice,
                                sellPriceStr,
                                now
                            )
                            val newItem = RateItemEntity(
                                id = id,
                                name = name,
                                category = obj.optString("category", "General"),
                                unit = obj.optString("unit", "Kg"),
                                qty = obj.optDouble("qty", 1.0),
                                buyRate = buyRate,
                                sellPrice = sellPrice,
                                sellPriceStr = sellPriceStr,
                                minSellPrice = minP,
                                maxSellPrice = maxP,
                                priceHistoryJson = initialHistoryJson,
                                lastUpdated = now
                            )
                            dao.insertRateItem(newItem)
                            dao.insertPriceHistory(
                                PriceHistoryEntity(
                                    itemId = id,
                                    buyRate = buyRate,
                                    sellPrice = sellPrice,
                                    sellPriceStr = sellPriceStr,
                                    timestamp = now
                                )
                            )
                        }
                    }
                }

                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    suspend fun getDailyClosingSummary(
        businessName: String,
        currencySymbol: String
    ): DailyClosingSummary = withContext(Dispatchers.IO) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis

        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val endOfDay = cal.timeInMillis

        val txns = dao.getTransactionsForDateRange(startOfDay, endOfDay)
        val invoices = dao.getInvoicesForDateRange(startOfDay, endOfDay)

        var totalQrUpi = 0.0
        var totalCash = 0.0
        var totalOther = 0.0
        var totalDue = 0.0

        txns.forEach { txn ->
            val isCreditOrSale = txn.type == TransactionType.GAVE_CREDIT.name || txn.type == TransactionType.INVOICE_BILL.name
            val method = txn.paymentMethod.trim().uppercase()

            if (isCreditOrSale && txn.status != "PAID") {
                totalDue += txn.amount
            } else {
                when (method) {
                    "UPI" -> totalQrUpi += txn.amount
                    "CASH" -> totalCash += txn.amount
                    else -> totalOther += txn.amount
                }
            }
        }

        invoices.forEach { inv ->
            if (inv.status == InvoiceEntity.STATUS_UNPAID || inv.status == InvoiceEntity.STATUS_OVERDUE) {
                val dueAmt = inv.totalAmount - inv.paidAmount
                if (dueAmt > 0 && txns.none { it.invoiceRef == inv.invoiceNumber }) {
                    totalDue += dueAmt
                }
            }
        }

        val totalCollections = totalQrUpi + totalCash + totalOther
        val dateFormatted = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())

        val shareText = buildString {
            appendLine("📊 *DAILY CLOSING SUMMARY*")
            appendLine("🏢 $businessName")
            appendLine("📅 Date: $dateFormatted")
            appendLine("─────────────────────────")
            appendLine("💳 Total QR / UPI: $currencySymbol${String.format(Locale.US, "%.2f", totalQrUpi)}")
            appendLine("💵 Total Cash:     $currencySymbol${String.format(Locale.US, "%.2f", totalCash)}")
            if (totalOther > 0) {
                appendLine("🏦 Bank/Other:     $currencySymbol${String.format(Locale.US, "%.2f", totalOther)}")
            }
            appendLine("─────────────────────────")
            appendLine("💰 *Total Collections:* $currencySymbol${String.format(Locale.US, "%.2f", totalCollections)}")
            appendLine("⏳ *Total Due Today:*    $currencySymbol${String.format(Locale.US, "%.2f", totalDue)}")
            appendLine("🧾 Transactions Count: ${txns.size}")
            appendLine("─────────────────────────")
            appendLine("Generated via Smart Ledger POS")
        }

        DailyClosingSummary(
            dateStr = dateFormatted,
            totalQrUpi = totalQrUpi,
            totalCash = totalCash,
            totalOther = totalOther,
            totalCollections = totalCollections,
            totalDueIssued = totalDue,
            totalTransactionsCount = txns.size,
            formattedShareText = shareText
        )
    }
}
