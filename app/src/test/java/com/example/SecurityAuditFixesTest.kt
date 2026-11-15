package com.example

import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppPreferencesManager
import com.example.data.LedgerDao
import com.example.data.LedgerDatabase
import com.example.data.LedgerRepository
import com.example.notes.NoteBootReceiver
import com.example.utils.ExportUtils
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecurityAuditFixesTest {

    private lateinit var context: Context
    private lateinit var db: LedgerDatabase
    private lateinit var dao: LedgerDao
    private lateinit var repository: LedgerRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("ledgermate_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.ledgerDao()
        repository = LedgerRepository(dao, syncManager = null)
    }

    @After
    fun teardown() {
        db.close()
        context.getSharedPreferences("ledgermate_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun passcodeHashingRejectsPlaintextAndEnforcesPersistentLockout() {
        val prefs = AppPreferencesManager(context)

        // Legacy plaintext stored directly in SharedPreferences must never verify
        prefs.passcodePin = "1234"
        assertFalse(prefs.verifyPasscodePin("1234"))

        // Salted SHA-256 hash verifies exact PIN and rejects wrong PIN
        prefs.setHashedPasscode("4829")
        assertTrue(prefs.passcodePin.startsWith("sha256:"))
        assertTrue(prefs.verifyPasscodePin("4829"))
        assertFalse(prefs.verifyPasscodePin("1234"))

        // 5 failed attempts trigger persistent lockout readable by a new AppPreferencesManager instance
        val baseTime = 1_700_000_000_000L
        repeat(4) { idx ->
            val (attempts, lockoutSec) = prefs.recordFailedPinAttempt(nowMs = baseTime + idx)
            assertEquals(idx + 1, attempts)
            assertEquals(0L, lockoutSec)
        }
        val (attempts5, lockoutSec5) = prefs.recordFailedPinAttempt(nowMs = baseTime + 10)
        assertEquals(5, attempts5)
        assertEquals(30L, lockoutSec5)

        val reloadedPrefs = AppPreferencesManager(context)
        assertTrue(reloadedPrefs.isPinLockedOut(nowMs = baseTime + 5_000L))
        assertTrue(reloadedPrefs.remainingPinLockoutSeconds(nowMs = baseTime + 5_000L) >= 25L)

        // After expiry window, lockout clears automatically
        assertFalse(reloadedPrefs.isPinLockedOut(nowMs = baseTime + 35_000L))
        assertEquals(0L, reloadedPrefs.remainingPinLockoutSeconds(nowMs = baseTime + 35_000L))

        // Explicit reset also clears lockout
        reloadedPrefs.recordFailedPinAttempt(nowMs = baseTime + 40_000L)
        reloadedPrefs.resetPinLockout()
        assertEquals(0, reloadedPrefs.failedPinAttempts)
        assertEquals(0L, reloadedPrefs.pinLockoutUntil)
    }

    @Test
    fun escapeCsvNeutralizesSpreadsheetFormulaPrefixes() {
        assertEquals("\"'=SUM(A1:A5)\"", ExportUtils.escapeCsv("=SUM(A1:A5)"))
        assertEquals("\"'+cmd|' /C calc'!A0\"", ExportUtils.escapeCsv("+cmd|' /C calc'!A0"))
        assertEquals("\"'-10+20\"", ExportUtils.escapeCsv("-10+20"))
        assertEquals("\"'@SUM(1,1)\"", ExportUtils.escapeCsv("@SUM(1,1)"))
        assertEquals("\"'\tLeadingTab\"", ExportUtils.escapeCsv("\tLeadingTab"))
        assertEquals("Normal Store Name", ExportUtils.escapeCsv("Normal Store Name"))
        assertEquals("\"Store, Inc.\"", ExportUtils.escapeCsv("Store, Inc."))
    }

    @Test
    fun importDataFromJsonFiltersMalformedAndNegativeTransactions() = runBlocking {
        val payload = """
            {
              "customers": [
                { "id": "c1", "name": "Valid Trader", "partyType": "CUSTOMER" },
                { "id": "c2", "name": "   ", "partyType": "CUSTOMER" }
              ],
              "transactions": [
                { "id": "t_ok", "customerId": "c1", "amount": 250.0, "type": "GAVE_CREDIT", "description": "Valid" },
                { "id": "t_neg", "customerId": "c1", "amount": -500.0, "type": "GAVE_CREDIT", "description": "Negative" },
                { "id": "t_zero", "customerId": "c1", "amount": 0.0, "type": "GOT_PAYMENT", "description": "Zero" },
                { "id": "t_bad_type", "customerId": "c1", "amount": 100.0, "type": "ADMIN_HACK", "description": "Bad type" },
                { "id": "t_blank_cust", "customerId": "", "amount": 100.0, "type": "GAVE_CREDIT", "description": "No cust" }
              ]
            }
        """.trimIndent()

        val result = repository.importDataFromJson(payload)
        assertTrue(result)

        val customers = dao.getAllCustomersList()
        assertEquals(1, customers.size)
        assertEquals("Valid Trader", customers.first().name)

        val txns = dao.getAllTransactionsList()
        assertEquals(1, txns.size)
        assertEquals("t_ok", txns.first().id)
        assertEquals(250.0, txns.first().amount, 0.001)
    }

    @Test
    fun noteBootReceiverIgnoresUnexpectedIntentAction() {
        val receiver = NoteBootReceiver()
        receiver.onReceive(context, null)
        receiver.onReceive(context, Intent("com.example.UNTRUSTED_CUSTOM_ACTION"))
    }
}
