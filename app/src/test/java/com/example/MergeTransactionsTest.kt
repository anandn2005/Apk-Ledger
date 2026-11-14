package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.CustomerEntity
import com.example.data.LedgerDao
import com.example.data.LedgerDatabase
import com.example.data.LedgerRepository
import com.example.data.RateItemEntity
import com.example.data.TransactionEntity
import com.example.data.TransactionType
import com.example.ui.components.computePriceTrend
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MergeTransactionsTest {

    private lateinit var db: LedgerDatabase
    private lateinit var dao: LedgerDao
    private lateinit var repository: LedgerRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.ledgerDao()
        repository = LedgerRepository(dao, syncManager = null)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun mergeAndUndoPreserveExactCustomerBalance() = runBlocking {
        val customer = CustomerEntity(id = "cust_1", name = "Test Trader", phone = "9999999999")
        dao.insertCustomer(customer)

        val now = System.currentTimeMillis()
        val oldTime = now - 40L * 24 * 60 * 60 * 1000
        val debits = (1..5).map { idx ->
            TransactionEntity(
                id = "debit_$idx",
                customerId = customer.id,
                amount = idx * 100.0, // 100+200+300+400+500 = 1500
                type = TransactionType.GAVE_CREDIT.name,
                description = "Bill #$idx",
                date = oldTime + idx * 1000L
            )
        }
        val credit = TransactionEntity(
            id = "credit_1",
            customerId = customer.id,
            amount = 350.0,
            type = TransactionType.GOT_PAYMENT.name,
            description = "Part payment",
            date = oldTime + 10_000L
        )
        val allSources = debits + credit
        allSources.forEach { dao.insertTransaction(it) }

        fun netBalance(txns: List<TransactionEntity>): Double {
            val d = txns.filter { it.type == TransactionType.GAVE_CREDIT.name }.sumOf { it.amount }
            val c = txns.filter { it.type == TransactionType.GOT_PAYMENT.name }.sumOf { it.amount }
            return d - c
        }

        val initialBalance = netBalance(dao.getTransactionsForCustomer(customer.id).first())
        assertEquals(1150.0, initialBalance, 0.001)

        // 1. Merge into single net entry (1500 debit - 350 credit = 1150 debit)
        val mergedNet = TransactionEntity(
            id = "merged_net_1",
            customerId = customer.id,
            amount = 1150.0,
            type = TransactionType.GAVE_CREDIT.name,
            description = "Merged 6 entries",
            date = now - 1000L
        )
        repository.mergeTransactions(
            customerId = customer.id,
            sourceTransactions = allSources,
            createdTransactions = listOf(mergedNet)
        )

        val afterMerge = dao.getTransactionsForCustomer(customer.id).first()
        assertEquals(1, afterMerge.size)
        assertEquals(1150.0, netBalance(afterMerge), 0.001)

        val logs = dao.getAllAuditLogs().first()
        assertTrue(logs.any { it.actionType == "MERGE_TRANSACTIONS" })

        // 2. Undo restores all 6 original entries and preserves balance
        repository.undoMergeTransactions(
            originalTransactions = allSources,
            createdTransactions = listOf(mergedNet)
        )

        val afterUndo = dao.getTransactionsForCustomer(customer.id).first()
        assertEquals(6, afterUndo.size)
        assertEquals(1150.0, netBalance(afterUndo), 0.001)
    }

    @Test
    fun computePriceTrendHandlesParentVariantsAndEdgeCases() {
        val noHistory = RateItemEntity(id = "1", name = "Sugar", sellPrice = 50.0)
        assertNull(computePriceTrend(noHistory))

        val singlePoint = RateItemEntity(
            id = "2",
            name = "Salt",
            sellPrice = 20.0,
            priceHistoryJson = """[{"date":1000,"sellPrice":20.0}]"""
        )
        assertNull(computePriceTrend(singlePoint))

        val upward = RateItemEntity(
            id = "3",
            name = "Rice",
            sellPrice = 115.0,
            priceHistoryJson = """[{"date":10000,"sellPrice":100.0},{"date":20000,"sellPrice":115.0}]"""
        )
        val upTrend = computePriceTrend(upward)
        assertNotNull(upTrend)
        assertTrue(upTrend!!.isUp)
        assertFalse(upTrend.isFlat)
        assertEquals(15.0, upTrend.delta, 0.001)

        val variantFallback = RateItemEntity(
            id = "4",
            name = "Oil",
            sellPrice = 140.0,
            hasMultipleVariants = true,
            variantsJson = """[{"id":"v1","label":"1L","priceHistoryJson":"[{\"date\":10000,\"sellPrice\":150.0},{\"date\":20000,\"sellPrice\":140.0}]"}]"""
        )
        val downTrend = computePriceTrend(variantFallback)
        assertNotNull(downTrend)
        assertFalse(downTrend!!.isUp)
        assertEquals(-10.0, downTrend.delta, 0.001)
    }
}
