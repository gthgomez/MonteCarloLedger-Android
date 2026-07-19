package com.example.app.data

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        IncomeEntity::class,
        PaymentEntity::class,
        TransactionEntity::class,
        BillOccurrenceEntity::class,
        SettingsEntity::class,
        TransactionRuleEntity::class,
        AssetEntity::class,
        GoalEntity::class,
        CategoryBudgetEntity::class
    ],
    version = 10,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun incomeDao(): IncomeDao
    abstract fun paymentDao(): PaymentDao
    abstract fun transactionDao(): TransactionDao
    abstract fun billOccurrenceDao(): BillOccurrenceDao
    abstract fun settingsDao(): SettingsDao
    abstract fun transactionRuleDao(): TransactionRuleDao
    abstract fun assetDao(): AssetDao
    abstract fun goalDao(): GoalDao
    abstract fun categoryBudgetDao(): CategoryBudgetDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ledger_database"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10
                    )
                    .build().also { INSTANCE = it }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE income ADD COLUMN expectedAmountCents INTEGER")
                db.execSQL("ALTER TABLE payments ADD COLUMN isAutoWithdraw INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE transactions ADD COLUMN category TEXT NOT NULL DEFAULT 'uncategorized'")
                // Ensure default settings exist
                db.execSQL("INSERT OR IGNORE INTO settings (key, value) VALUES ('bank_balance_cents', '0')")
                db.execSQL("INSERT OR IGNORE INTO settings (key, value) VALUES ('bank_balance_reconciled', '0')")
                db.execSQL("INSERT OR IGNORE INTO settings (key, value) VALUES ('current_balance', '0')")
                db.execSQL("INSERT OR IGNORE INTO settings (key, value) VALUES ('starting_balance', '0')")
                db.execSQL("INSERT OR IGNORE INTO settings (key, value) VALUES ('simulation_days', '90')")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bill_occurrences ADD COLUMN transaction_id INTEGER")
                db.execSQL("ALTER TABLE bill_occurrences ADD COLUMN created_at TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS transaction_rules (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        match_text TEXT NOT NULL,
                        category TEXT NOT NULL,
                        is_active INTEGER NOT NULL DEFAULT 1,
                        priority INTEGER NOT NULL DEFAULT 0,
                        created_at TEXT NOT NULL DEFAULT ''
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS assets (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        type TEXT NOT NULL,
                        balanceCents INTEGER NOT NULL,
                        lastUpdated TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS goals (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        targetAmountCents INTEGER NOT NULL,
                        currentAmountCents INTEGER NOT NULL,
                        deadline TEXT,
                        createdAt TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN source TEXT NOT NULL DEFAULT 'manual'")
                db.execSQL("ALTER TABLE transactions ADD COLUMN review_status TEXT NOT NULL DEFAULT 'approved'")
                db.execSQL("ALTER TABLE transactions ADD COLUMN reviewed_at TEXT")
                db.execSQL(
                    """
                    UPDATE transactions
                    SET review_status = 'pending'
                    WHERE type = 'expense' AND lower(category) = 'uncategorized'
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bill_occurrences ADD COLUMN original_due_date TEXT")
                db.execSQL("ALTER TABLE bill_occurrences ADD COLUMN is_user_modified INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE bill_occurrences SET original_due_date = due_date WHERE original_due_date IS NULL")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE income ADD COLUMN payType TEXT NOT NULL DEFAULT 'FLAT'")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS category_budgets (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        category TEXT NOT NULL UNIQUE,
                        limitCents INTEGER NOT NULL,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        createdAt TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        @VisibleForTesting
        val MIGRATION_9_10_FOR_TEST: Migration
            get() = MIGRATION_9_10
    }
}
