package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {

    // --- CUSTOMERS ---
    @Query("SELECT * FROM customers ORDER BY name COLLATE NOCASE ASC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getCustomerById(id: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE name LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%' OR businessName LIKE '%' || :query || '%' ORDER BY name COLLATE NOCASE ASC")
    fun searchCustomers(query: String): Flow<List<CustomerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomers(customers: List<CustomerEntity>)

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)

    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun deleteCustomerById(id: String)

    @Query("DELETE FROM customers WHERE id IN (:ids)")
    suspend fun deleteCustomersByIds(ids: List<String>)

    // --- TRANSACTIONS ---
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE customerId = :customerId ORDER BY date DESC")
    fun getTransactionsForCustomer(customerId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: String): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteTransactionsByIds(ids: List<String>)

    @Query("DELETE FROM transactions WHERE customerId = :customerId")
    suspend fun deleteTransactionsForCustomer(customerId: String)

    @Query("SELECT * FROM transactions WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC")
    suspend fun getTransactionsForDateRange(startDate: Long, endDate: Long): List<TransactionEntity>

    // --- INVOICES ---
    @Query("SELECT * FROM invoices ORDER BY issueDate DESC")
    fun getAllInvoices(): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices WHERE customerId = :customerId ORDER BY issueDate DESC")
    fun getInvoicesForCustomer(customerId: String): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices WHERE id = :id LIMIT 1")
    suspend fun getInvoiceById(id: String): InvoiceEntity?

    @Query("SELECT * FROM invoices WHERE invoiceNumber LIKE :prefix ORDER BY invoiceNumber DESC LIMIT 1")
    suspend fun getLatestInvoiceWithPrefix(prefix: String): InvoiceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: InvoiceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoices(invoices: List<InvoiceEntity>)

    @Update
    suspend fun updateInvoice(invoice: InvoiceEntity)

    @Delete
    suspend fun deleteInvoice(invoice: InvoiceEntity)

    @Query("DELETE FROM invoices WHERE id IN (:ids)")
    suspend fun deleteInvoicesByIds(ids: List<String>)

    @Query("SELECT * FROM invoices WHERE issueDate >= :startDate AND issueDate <= :endDate ORDER BY issueDate DESC")
    suspend fun getInvoicesForDateRange(startDate: Long, endDate: Long): List<InvoiceEntity>

    // --- AUDIT LOGS ---
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC")
    fun getAllAuditLogs(): Flow<List<AuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLogEntity): Long

    @Update
    suspend fun updateAuditLog(log: AuditLogEntity)

    @Query("DELETE FROM audit_logs WHERE id = :id")
    suspend fun deleteAuditLogById(id: String)

    @Query("DELETE FROM audit_logs")
    suspend fun clearAllAuditLogs()

    // --- RATE ITEMS ---
    @Query("SELECT * FROM rate_items ORDER BY name COLLATE NOCASE ASC")
    fun getAllRateItems(): Flow<List<RateItemEntity>>

    @Query("SELECT * FROM rate_items ORDER BY name COLLATE NOCASE ASC")
    fun getAllRateItemsDirect(): List<RateItemEntity>

    @Query("SELECT * FROM rate_items")
    suspend fun getAllRateItemsList(): List<RateItemEntity>

    @Query("SELECT * FROM customers")
    suspend fun getAllCustomersList(): List<CustomerEntity>

    @Query("SELECT * FROM invoices")
    suspend fun getAllInvoicesList(): List<InvoiceEntity>

    @Query("SELECT * FROM transactions")
    suspend fun getAllTransactionsList(): List<TransactionEntity>

    @Query("SELECT * FROM rate_items WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) LIMIT 1")
    suspend fun getRateItemByName(name: String): RateItemEntity?

    @Query("SELECT * FROM customers WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) LIMIT 1")
    suspend fun getCustomerByName(name: String): CustomerEntity?

    @Query("SELECT * FROM invoices WHERE invoiceNumber = :invoiceNumber LIMIT 1")
    suspend fun getInvoiceByNumber(invoiceNumber: String): InvoiceEntity?

    @Query("SELECT * FROM rate_items WHERE category = :category ORDER BY name COLLATE NOCASE ASC")
    fun getRateItemsByCategory(category: String): Flow<List<RateItemEntity>>

    @Query("SELECT * FROM rate_items WHERE id = :id LIMIT 1")
    suspend fun getRateItemById(id: String): RateItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRateItem(item: RateItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRateItems(items: List<RateItemEntity>)

    @Update
    suspend fun updateRateItem(item: RateItemEntity)

    @Delete
    suspend fun deleteRateItem(item: RateItemEntity)

    @Query("DELETE FROM rate_items WHERE id IN (:ids)")
    suspend fun deleteRateItemsByIds(ids: List<String>)

    @Query("UPDATE rate_items SET category = :newCategory, lastUpdated = :timestamp WHERE category = :oldCategory")
    suspend fun renameCategoryForItems(oldCategory: String, newCategory: String, timestamp: Long)

    // --- PRICE HISTORY ---
    @Query("SELECT * FROM price_history WHERE itemId = :itemId ORDER BY timestamp DESC")
    fun getPriceHistoryForItem(itemId: String): Flow<List<PriceHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPriceHistory(entry: PriceHistoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPriceHistories(entries: List<PriceHistoryEntity>)

    @Query("DELETE FROM price_history WHERE itemId = :itemId")
    suspend fun deletePriceHistoryForItem(itemId: String)
}
