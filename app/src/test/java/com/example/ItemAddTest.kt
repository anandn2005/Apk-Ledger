package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.LedgerDao
import com.example.data.LedgerDatabase
import com.example.data.RateItemEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ItemAddTest {

    private lateinit var db: LedgerDatabase
    private lateinit var dao: LedgerDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.ledgerDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testInsertRateItemAndRetrieve() = runBlocking {
        val safeId = UUID.randomUUID().toString()
        val item = RateItemEntity(
            id = safeId,
            name = "Test Basmati Rice",
            category = "Rice",
            unit = "Kg",
            qty = 1.0,
            buyRate = 80.0,
            sellPrice = 100.0,
            sellPriceStr = "100",
            minSellPrice = 100.0,
            maxSellPrice = 100.0,
            lastUpdated = System.currentTimeMillis()
        )

        val rowId = dao.insertRateItem(item)
        assertTrue(rowId > 0)

        val allItems = dao.getAllRateItems().first()
        assertEquals(1, allItems.size)
        assertEquals("Test Basmati Rice", allItems[0].name)
    }
}
