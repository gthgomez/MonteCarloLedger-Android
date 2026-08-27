package com.montecarlo.ledger.data

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
        AccountEntity::class,
        TransactionEntity::class,
        BillOccurrenceEntity::class,
        SettingsEntity::class,
        TransactionRuleEntity::class,
        AssetEntity::class,
        GoalEntity::class,
        CategoryBudgetEntity::class,
        DebtEntity::class,
    ],
    version = 16,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun incomeDao(): IncomeDao
    abstract fun paymentDao(): PaymentDao
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun billOccurrenceDao(): BillOccurrenceDao
    abstract fun settingsDao(): SettingsDao
    abstract fun transactionRuleDao(): TransactionRuleDao
    abstract fun assetDao(): AssetDao
    abstract fun goalDao(): GoalDao
    abstract fun categoryBudgetDao(): CategoryBudgetDao
    abstract fun debtDao(): DebtDao

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
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                        MIGRATION_13_14,
                        MIGRATION_14_15,
                        MIGRATION_15_16,
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

        @VisibleForTesting
        val MIGRATION_1_2_FOR_TEST: Migration
            get() = MIGRATION_1_2
        @VisibleForTesting
        val MIGRATION_2_3_FOR_TEST: Migration
            get() = MIGRATION_2_3
        @VisibleForTesting
        val MIGRATION_3_4_FOR_TEST: Migration
            get() = MIGRATION_3_4
        @VisibleForTesting
        val MIGRATION_4_5_FOR_TEST: Migration
            get() = MIGRATION_4_5
        @VisibleForTesting
        val MIGRATION_5_6_FOR_TEST: Migration
            get() = MIGRATION_5_6
        @VisibleForTesting
        val MIGRATION_6_7_FOR_TEST: Migration
            get() = MIGRATION_6_7
        @VisibleForTesting
        val MIGRATION_7_8_FOR_TEST: Migration
            get() = MIGRATION_7_8
        @VisibleForTesting
        val MIGRATION_8_9_FOR_TEST: Migration
            get() = MIGRATION_8_9

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

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // SQLite INTEGER natively supports 64-bit Long storage.
                // Migration 10->11 updates Room metadata & entities to Long cents.
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS debts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        balanceCents INTEGER NOT NULL,
                        aprBasisPoints INTEGER NOT NULL,
                        minimumPaymentCents INTEGER NOT NULL,
                        dueDayOfMonth INTEGER NOT NULL,
                        linkedPaymentId INTEGER,
                        isActive INTEGER NOT NULL,
                        FOREIGN KEY(linkedPaymentId) REFERENCES payments(id) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_debts_linkedPaymentId ON debts(linkedPaymentId)")
            }
        }

        @VisibleForTesting
        val MIGRATION_9_10_FOR_TEST: Migration
            get() = MIGRATION_9_10

        @VisibleForTesting
        val MIGRATION_11_12_FOR_TEST: Migration
            get() = MIGRATION_11_12

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Rebuild so both paths match Room v13: 9→10 used UNIQUE on the column,
                // while DBs created at 10–12 had no uniqueness at all.
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `category_budgets_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `category` TEXT NOT NULL,
                        `limitCents` INTEGER NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `createdAt` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `category_budgets_new` (`id`, `category`, `limitCents`, `enabled`, `createdAt`)
                    SELECT `id`, `category`, `limitCents`, `enabled`, `createdAt`
                    FROM `category_budgets`
                    WHERE `id` IN (
                        SELECT MIN(`id`) FROM `category_budgets` GROUP BY lower(`category`)
                    )
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `category_budgets`")
                db.execSQL("ALTER TABLE `category_budgets_new` RENAME TO `category_budgets`")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_category_budgets_category` ON `category_budgets` (`category`)"
                )
            }
        }

        @VisibleForTesting
        val MIGRATION_10_11_FOR_TEST: Migration
            get() = MIGRATION_10_11

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `accounts` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `balanceCents` INTEGER NOT NULL,
                        `isDefault` INTEGER NOT NULL DEFAULT 0,
                        `lastUpdated` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                // Seed one default account mirroring the current primary bank balance
                // so per-account adoption starts from real state instead of zero.
                db.execSQL(
                    """
                    INSERT INTO accounts (name, type, balanceCents, isDefault, lastUpdated)
                    SELECT 'Primary account', 'checking',
                           CAST(COALESCE((SELECT value FROM settings WHERE key = 'bank_balance_cents'), '0') AS INTEGER),
                           1,
                           strftime('%Y-%m-%d', 'now')
                    """.trimIndent()
                )
            }
        }
        @VisibleForTesting
        val MIGRATION_12_13_FOR_TEST: Migration
            get() = MIGRATION_12_13

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `accounts` ADD COLUMN `isReconciled` INTEGER NOT NULL DEFAULT 0"
                )
                // Adopt the reconciled flag from the legacy settings mirror.
                db.execSQL(
                    """
                    UPDATE accounts
                    SET isReconciled = CASE WHEN (
                        SELECT value FROM settings WHERE key = 'bank_balance_reconciled'
                    ) IN ('1', 'true', 'TRUE', 'True', 'yes') THEN 1 ELSE 0 END
                    WHERE isDefault = 1
                    """.trimIndent()
                )
            }
        }
        @VisibleForTesting
        val MIGRATION_13_14_FOR_TEST: Migration
            get() = MIGRATION_13_14

        @VisibleForTesting
        val MIGRATION_14_15_FOR_TEST: Migration
            get() = MIGRATION_14_15

        /**
         * Product-depth migration: transaction clearing states, optional account tagging,
         * and revolving-debt fields (kind, statement day, percent minimums).
         * Debts is rebuilt because SQLite cannot ALTER TABLE to add foreign-key columns.
         */
        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `transactions` ADD COLUMN `clearing_status` TEXT NOT NULL DEFAULT 'posted'"
                )
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `account_id` INTEGER")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `debts_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `balanceCents` INTEGER NOT NULL,
                        `aprBasisPoints` INTEGER NOT NULL,
                        `minimumPaymentCents` INTEGER NOT NULL,
                        `dueDayOfMonth` INTEGER NOT NULL,
                        `linkedPaymentId` INTEGER,
                        `isActive` INTEGER NOT NULL,
                        `kind` TEXT NOT NULL,
                        `statementDayOfMonth` INTEGER,
                        `minPaymentPercentBps` INTEGER NOT NULL,
                        `minPaymentFloorCents` INTEGER NOT NULL,
                        `linkedAccountId` INTEGER,
                        FOREIGN KEY(`linkedPaymentId`) REFERENCES `payments`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                        FOREIGN KEY(`linkedAccountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `debts_new` (
                        `id`, `name`, `balanceCents`, `aprBasisPoints`, `minimumPaymentCents`,
                        `dueDayOfMonth`, `linkedPaymentId`, `isActive`,
                        `kind`, `statementDayOfMonth`, `minPaymentPercentBps`, `minPaymentFloorCents`,
                        `linkedAccountId`
                    )
                    SELECT
                        `id`, `name`, `balanceCents`, `aprBasisPoints`, `minimumPaymentCents`,
                        `dueDayOfMonth`, `linkedPaymentId`, `isActive`,
                        'installment', NULL, 0, 0, NULL
                    FROM `debts`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `debts`")
                db.execSQL("ALTER TABLE `debts_new` RENAME TO `debts`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_debts_linkedPaymentId` ON `debts` (`linkedPaymentId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_debts_linkedAccountId` ON `debts` (`linkedAccountId`)"
                )
            }
        }
        @VisibleForTesting
        val MIGRATION_15_16_FOR_TEST: Migration
            get() = MIGRATION_15_16
    }
}
