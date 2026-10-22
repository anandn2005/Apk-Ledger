package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [
        CustomerEntity::class,
        TransactionEntity::class,
        InvoiceEntity::class,
        AuditLogEntity::class,
        RateItemEntity::class,
        PriceHistoryEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class LedgerDatabase : RoomDatabase() {

    abstract fun ledgerDao(): LedgerDao

    companion object {
        @Volatile
        private var INSTANCE: LedgerDatabase? = null

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE rate_items ADD COLUMN hasMultipleVariants INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE rate_items ADD COLUMN variantsJson TEXT NOT NULL DEFAULT '[]'")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE transactions ADD COLUMN quantity REAL NOT NULL DEFAULT 1.0")
                    db.execSQL("ALTER TABLE transactions ADD COLUMN rate REAL NOT NULL DEFAULT 0.0")
                    db.execSQL("ALTER TABLE transactions ADD COLUMN subtotal REAL NOT NULL DEFAULT 0.0")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE customers ADD COLUMN lastUpdated INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("UPDATE customers SET lastUpdated = createdAt WHERE lastUpdated = 0")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                try {
                    db.execSQL("ALTER TABLE transactions ADD COLUMN lastUpdated INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("UPDATE transactions SET lastUpdated = date WHERE lastUpdated = 0")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                try {
                    db.execSQL("ALTER TABLE invoices ADD COLUMN lastUpdated INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("UPDATE invoices SET lastUpdated = issueDate WHERE lastUpdated = 0")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope = CoroutineScope(Dispatchers.IO)): LedgerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LedgerDatabase::class.java,
                    "ledgermate_database"
                )
                    .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
