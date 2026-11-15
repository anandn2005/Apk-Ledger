package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.CustomerEntity
import com.example.data.LedgerDao
import com.example.data.LedgerDatabase
import com.example.data.LedgerRepository
import com.example.data.TransactionEntity
import com.example.data.TransactionMergeHelper
import com.example.data.TransactionType
import com.example.ui.Formatters
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MergeTransactionsTest {

    private lateinit var db: LedgerDatabase
    private lateinit var dao: LedgerDao
    private lateinit var repository: LedgerRepository
    private val customer = CustomerEntity(id = "cust_1", name = "Test Trader", phone = "9999999999")

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.ledgerDao()
        repository = LedgerRepository(dao, syncManager = null)
        runBlocking {
            dao.insertCustomer(customer)
        }
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun ts(year: Int, monthOneBased: Int, day: Int, hour: Int = 10, minute: Int = 0): Long {
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthOneBased - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun allDebitMergeCreatesSingleDueParentAndPreservesBalance() = runBlocking {
        val t1 = TransactionEntity(
            id = "d1", customerId = customer.id, amount = 4000.0,
            type = TransactionType.GAVE_CREDIT.name, description = "Rice bags",
            date = ts(2026, 11, 12, 9, 15)
        )
        val t2 = TransactionEntity(
            id = "d2", customerId = customer.id, amount = 5000.0,
            type = TransactionType.GAVE_CREDIT.name, description = "Sugar",
            date = ts(2026, 11, 14, 14, 0)
        )
        val t3 = TransactionEntity(
            id = "d3", customerId = customer.id, amount = 3900.0,
            type = TransactionType.GAVE_CREDIT.name, description = "Oil",
            date = ts(2026, 11, 15, 18, 30)
        )
        val sources = listOf(t1, t2, t3)
        sources.forEach { dao.insertTransaction(it) }

        val initialBalance = TransactionMergeHelper.computeReceivableBalance(
            dao.getTransactionsForCustomer(customer.id).first()
        )
        assertEquals(12900.0, initialBalance, 0.001)
        assertEquals("₹12,900", Formatters.formatCurrency(12900.0))
        assertEquals("₹1,25,000", Formatters.formatCurrency(125000.0))

        val parent = TransactionMergeHelper.buildMergedParent(customer.id, sources)
        assertNotNull(parent)
        parent!!

        repository.mergeTransactions(customer.id, sources, listOf(parent))

        val stored = dao.getTransactionsForCustomer(customer.id).first()
        assertEquals(1, stored.size)
        val storedParent = stored.first()
        assertTrue(storedParent.isMerged)
        assertEquals("MERGED", storedParent.paymentMethod)
        assertEquals(TransactionType.GAVE_CREDIT.name, storedParent.type)
        assertEquals("DUE", storedParent.status)
        assertEquals(12900.0, storedParent.amount, 0.001)
        assertEquals("12 Nov \u2192 15 Nov 2026", storedParent.description)
        assertEquals(t3.date, storedParent.date)
        assertEquals(3, storedParent.mergedChildren.size)
        assertEquals(listOf("d1", "d2", "d3"), storedParent.mergedChildren.map { it.id })

        val afterBalance = TransactionMergeHelper.computeReceivableBalance(stored)
        assertEquals(initialBalance, afterBalance, 0.001)
    }

    @Test
    fun mixedMergeCreatesSingleNetParentNeverTwoAndPreservesBalance() = runBlocking {
        // 2 debits (5000 + 3000) + 1 credit (2500) -> net debit 5500 (credits - debits = -5500)
        val d1 = TransactionEntity(
            id = "md1", customerId = customer.id, amount = 5000.0,
            type = TransactionType.GAVE_CREDIT.name, description = "Order 1",
            date = ts(2026, 11, 10, 10, 0)
        )
        val d2 = TransactionEntity(
            id = "md2", customerId = customer.id, amount = 3000.0,
            type = TransactionType.GAVE_CREDIT.name, description = "Order 2",
            date = ts(2026, 11, 12, 11, 0)
        )
        val c1 = TransactionEntity(
            id = "mc1", customerId = customer.id, amount = 2500.0,
            type = TransactionType.GOT_PAYMENT.name, description = "UPI Part Pay",
            date = ts(2026, 11, 13, 16, 45)
        )
        val sources = listOf(d1, d2, c1)
        sources.forEach { dao.insertTransaction(it) }

        val initialBalance = TransactionMergeHelper.computeReceivableBalance(
            dao.getTransactionsForCustomer(customer.id).first()
        )
        assertEquals(5500.0, initialBalance, 0.001)

        val parent = TransactionMergeHelper.buildMergedParent(customer.id, sources)!!
        repository.mergeTransactions(customer.id, sources, listOf(parent))

        val afterMerge = dao.getTransactionsForCustomer(customer.id).first()
        assertEquals(1, afterMerge.size) // Strictly ONE parent, never two!
        assertEquals(TransactionType.GAVE_CREDIT.name, afterMerge.first().type)
        assertEquals("DUE", afterMerge.first().status)
        assertEquals(5500.0, afterMerge.first().amount, 0.001)
        assertEquals(3, afterMerge.first().mergedChildren.size)
        assertEquals(initialBalance, TransactionMergeHelper.computeReceivableBalance(afterMerge), 0.001)

        // Also test net > 0 (net credit / Payment received)
        val extraCredit = TransactionEntity(
            id = "mc2", customerId = customer.id, amount = 9000.0,
            type = TransactionType.GOT_PAYMENT.name, description = "Advance",
            date = ts(2026, 11, 15, 12, 0)
        )
        dao.insertTransaction(extraCredit)
        val preSecondBalance = TransactionMergeHelper.computeReceivableBalance(
            dao.getTransactionsForCustomer(customer.id).first()
        )
        assertEquals(-3500.0, preSecondBalance, 0.001)

        val currentRows = dao.getTransactionsForCustomer(customer.id).first()
        val netCreditParent = TransactionMergeHelper.buildMergedParent(customer.id, currentRows)!!
        repository.mergeTransactions(customer.id, currentRows, listOf(netCreditParent))

        val finalRows = dao.getTransactionsForCustomer(customer.id).first()
        assertEquals(1, finalRows.size)
        assertEquals(TransactionType.GOT_PAYMENT.name, finalRows.first().type)
        assertEquals("COMPLETED", finalRows.first().status)
        assertEquals(3500.0, finalRows.first().amount, 0.001)
        assertEquals(preSecondBalance, TransactionMergeHelper.computeReceivableBalance(finalRows), 0.001)
    }

    @Test
    fun netZeroMergeCreatesSettledParentAndPreservesBalance() = runBlocking {
        val d = TransactionEntity(
            id = "zd1", customerId = customer.id, amount = 1800.0,
            type = TransactionType.GAVE_CREDIT.name, description = "Bill",
            date = ts(2026, 11, 15, 9, 0)
        )
        val c = TransactionEntity(
            id = "zc1", customerId = customer.id, amount = 1800.0,
            type = TransactionType.GOT_PAYMENT.name, description = "Cash settled",
            date = ts(2026, 11, 15, 17, 0)
        )
        val sources = listOf(d, c)
        sources.forEach { dao.insertTransaction(it) }

        val initialBalance = TransactionMergeHelper.computeReceivableBalance(sources)
        assertEquals(0.0, initialBalance, 0.001)

        val parent = TransactionMergeHelper.buildMergedParent(customer.id, sources)!!
        repository.mergeTransactions(customer.id, sources, listOf(parent))

        val afterMerge = dao.getTransactionsForCustomer(customer.id).first()
        assertEquals(1, afterMerge.size)
        val row = afterMerge.first()
        assertEquals(0.0, row.amount, 0.001)
        assertEquals("PAID", row.status)
        assertEquals("15 Nov 2026", row.description)
        assertEquals(2, row.mergedChildren.size)
        assertEquals(initialBalance, TransactionMergeHelper.computeReceivableBalance(afterMerge), 0.001)
    }

    @Test
    fun dateRangeFormattingHandlesSameDaySameYearAndCrossYear() {
        val nov15Morning = ts(2026, 11, 15, 8, 0)
        val nov15Evening = ts(2026, 11, 15, 20, 30)
        assertEquals("15 Nov 2026", Formatters.formatMergedDateRange(nov15Morning, nov15Evening))

        val nov12 = ts(2026, 11, 12, 10, 0)
        assertEquals("12 Nov \u2192 15 Nov 2026", Formatters.formatMergedDateRange(nov12, nov15Evening))

        val dec28 = ts(2025, 12, 28, 11, 0)
        val jan03 = ts(2026, 1, 3, 15, 0)
        assertEquals("28 Dec 2025 \u2192 03 Jan 2026", Formatters.formatMergedDateRange(dec28, jan03))
    }

    @Test
    fun reMergingMergedEntryFlattensChildrenNeverNestedAndPreservesBalance() = runBlocking {
        val dec28 = TransactionEntity(
            id = "f1", customerId = customer.id, amount = 1000.0,
            type = TransactionType.GAVE_CREDIT.name, description = "Item A",
            date = ts(2025, 12, 28, 10, 0)
        )
        val dec30 = TransactionEntity(
            id = "f2", customerId = customer.id, amount = 500.0,
            type = TransactionType.GAVE_CREDIT.name, description = "Item B",
            date = ts(2025, 12, 30, 14, 0)
        )
        dao.insertTransactions(listOf(dec28, dec30))

        val firstParent = TransactionMergeHelper.buildMergedParent(customer.id, listOf(dec28, dec30))!!
        repository.mergeTransactions(customer.id, listOf(dec28, dec30), listOf(firstParent))

        val jan03Debit = TransactionEntity(
            id = "f3", customerId = customer.id, amount = 700.0,
            type = TransactionType.GAVE_CREDIT.name, description = "Item C",
            date = ts(2026, 1, 3, 12, 0)
        )
        val jan04Credit = TransactionEntity(
            id = "f4", customerId = customer.id, amount = 400.0,
            type = TransactionType.GOT_PAYMENT.name, description = "Part Paid",
            date = ts(2026, 1, 4, 16, 0)
        )
        dao.insertTransactions(listOf(jan03Debit, jan04Credit))

        val currentRows = dao.getTransactionsForCustomer(customer.id).first()
        assertEquals(3, currentRows.size) // firstParent + f3 + f4
        val balanceBeforeSecondMerge = TransactionMergeHelper.computeReceivableBalance(currentRows)
        assertEquals(1800.0, balanceBeforeSecondMerge, 0.001) // 1000 + 500 + 700 - 400

        val flattenedParent = TransactionMergeHelper.buildMergedParent(customer.id, currentRows)!!
        repository.mergeTransactions(customer.id, currentRows, listOf(flattenedParent))

        val finalRows = dao.getTransactionsForCustomer(customer.id).first()
        assertEquals(1, finalRows.size)
        val result = finalRows.first()
        assertEquals(1800.0, result.amount, 0.001)
        assertEquals(TransactionType.GAVE_CREDIT.name, result.type)
        assertEquals("28 Dec 2025 \u2192 04 Jan 2026", result.description)
        assertEquals(jan04Credit.date, result.date)

        val children = result.mergedChildren
        assertEquals(4, children.size)
        assertEquals(listOf("f1", "f2", "f3", "f4"), children.map { it.id })
        assertTrue(children.none { it.isMerged || it.mergedChildren.isNotEmpty() })
        assertEquals(balanceBeforeSecondMerge, TransactionMergeHelper.computeReceivableBalance(finalRows), 0.001)
    }

    @Test
    fun unmergeRestoresChildrenAtomicallyAndPreservesBalance() = runBlocking {
        val d1 = TransactionEntity(
            id = "u1", customerId = customer.id, amount = 2200.0,
            type = TransactionType.GAVE_CREDIT.name, description = "Cement",
            date = ts(2026, 11, 1, 9, 0)
        )
        val c1 = TransactionEntity(
            id = "u2", customerId = customer.id, amount = 800.0,
            type = TransactionType.GOT_PAYMENT.name, description = "Cash",
            date = ts(2026, 11, 2, 15, 0)
        )
        val d2 = TransactionEntity(
            id = "u3", customerId = customer.id, amount = 600.0,
            type = TransactionType.GAVE_CREDIT.name, description = "Sand",
            date = ts(2026, 11, 4, 18, 0)
        )
        val sources = listOf(d1, c1, d2)
        dao.insertTransactions(sources)

        val parent = TransactionMergeHelper.buildMergedParent(customer.id, sources)!!
        repository.mergeTransactions(customer.id, sources, listOf(parent))
        assertEquals(1, dao.getTransactionsForCustomer(customer.id).first().size)

        val restored = repository.unmergeTransaction(parent)
        assertEquals(3, restored.size)

        val afterUnmerge = dao.getTransactionsForCustomer(customer.id).first()
        assertEquals(3, afterUnmerge.size)
        assertTrue(afterUnmerge.none { it.isMerged })
        assertTrue(afterUnmerge.none { it.paymentMethod == "MERGED" })
        assertEquals(2000.0, TransactionMergeHelper.computeReceivableBalance(afterUnmerge), 0.001)

        val logs = dao.getAllAuditLogs().first()
        assertTrue(logs.any { it.actionType == "UNMERGE_TRANSACTION" })
    }

    @Test
    fun legacyMergedEntryFallbackAndMigration11To12WorkCleanly() {
        val legacy = TransactionEntity(
            id = "legacy_1",
            customerId = customer.id,
            amount = 750.0,
            type = TransactionType.GAVE_CREDIT.name,
            description = "Merged 2 debits",
            date = ts(2026, 10, 1),
            paymentMethod = "MERGED",
            isMerged = false,
            mergedChildrenJson = "[]"
        )
        assertTrue(legacy.isMergedParent())
        assertTrue(legacy.mergedChildren.isEmpty())

        // Verify MIGRATION_11_12 safely upgrades an existing v11 transactions table
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_11_12_test.db"
        context.deleteDatabase(dbName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(11) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `transactions` (
                                `id` TEXT NOT NULL,
                                `customerId` TEXT NOT NULL,
                                `amount` REAL NOT NULL,
                                `type` TEXT NOT NULL,
                                `description` TEXT NOT NULL,
                                `date` INTEGER NOT NULL,
                                `invoiceRef` TEXT NOT NULL,
                                `paymentMethod` TEXT NOT NULL,
                                `status` TEXT NOT NULL,
                                `lastUpdated` INTEGER NOT NULL,
                                `quantity` REAL NOT NULL,
                                `rate` REAL NOT NULL,
                                `subtotal` REAL NOT NULL,
                                `dueDate` INTEGER NOT NULL,
                                PRIMARY KEY(`id`)
                            )
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )
        val sqlite = helper.writableDatabase
        sqlite.execSQL(
            """
            INSERT INTO transactions (id, customerId, amount, type, description, date, invoiceRef, paymentMethod, status, lastUpdated, quantity, rate, subtotal, dueDate)
            VALUES ('old_1', 'cust_1', 500.0, 'GAVE_CREDIT', 'Legacy Row', 1700000000000, '', 'MERGED', 'COMPLETED', 1700000000000, 1.0, 500.0, 500.0, 0)
            """.trimIndent()
        )
        LedgerDatabase.MIGRATION_11_12.migrate(sqlite)

        sqlite.query("SELECT id, isMerged, mergedChildrenJson FROM transactions WHERE id = 'old_1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("old_1", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
            assertEquals("[]", cursor.getString(2))
        }
        helper.close()
        context.deleteDatabase(dbName)
        assertFalse(legacy.mergedChildren.isNotEmpty())
    }
}
