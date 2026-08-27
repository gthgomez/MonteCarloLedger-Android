package com.montecarlo.ledger.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration matrix torture tests.
 *
 * Every historically supported upgrade path must reach the current schema (v16)
 * without dropping, corrupting, or misrepresenting user financial state. We
 * exercise:
 *   - the schema-exported chain 9 -> 16 with representative data
 *   - the previously untested 14 -> 15 migration
 *   - hand-built legacy databases (v1 and v5) upgraded through the FULL chain
 *     and then OPENED through Room so entity mapping is exercised, not just SQL
 *   - adversarial inputs: duplicate case-only categories, missing settings,
 *     null legacy values, large row counts
 */
@RunWith(AndroidJUnit4::class)
class MigrationMatrixTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    private val fullChain: List<Migration> = listOf(
        AppDatabase.MIGRATION_1_2_FOR_TEST,
        AppDatabase.MIGRATION_2_3_FOR_TEST,
        AppDatabase.MIGRATION_3_4_FOR_TEST,
        AppDatabase.MIGRATION_4_5_FOR_TEST,
        AppDatabase.MIGRATION_5_6_FOR_TEST,
        AppDatabase.MIGRATION_6_7_FOR_TEST,
        AppDatabase.MIGRATION_7_8_FOR_TEST,
        AppDatabase.MIGRATION_8_9_FOR_TEST,
        AppDatabase.MIGRATION_9_10_FOR_TEST,
        AppDatabase.MIGRATION_10_11_FOR_TEST,
        AppDatabase.MIGRATION_11_12_FOR_TEST,
        AppDatabase.MIGRATION_12_13_FOR_TEST,
        AppDatabase.MIGRATION_13_14_FOR_TEST,
        AppDatabase.MIGRATION_14_15_FOR_TEST,
        AppDatabase.MIGRATION_15_16_FOR_TEST,
    )

    @After
    fun tearDown() {
        val names = listOf(
            "migration-fullchain", "migration-14-15", "migration-legacy-v1",
            "migration-legacy-v5", "migration-adversarial-budgets", "migration-large",
        )
        names.forEach { context.deleteDatabase(it) }
    }

    // ---------------------------------------------------------------------
    // Schema-exported chain: 9 -> 16
    // ---------------------------------------------------------------------

    @Test
    fun fullChain9To16_preservesRepresentativeLedgerData() {
        val name = "migration-fullchain"
        val db9 = helper.createDatabase(name, 9)

        db9.execSQL(
            "INSERT INTO income (id, name, amount_cents, frequency, day_of_month, next_date, expectedAmountCents, payType) " +
                "VALUES (1, 'Salary', 250000, 'MONTHLY', 15, '2026-09-15', 250000, 'FLAT')"
        )
        db9.execSQL(
            "INSERT INTO payments (id, name, amount_cents, frequency, day_of_month, next_date, is_active, isAutoWithdraw) " +
                "VALUES (1, 'Rent', 120000, 'Monthly', 1, '2026-09-01', 1, 0)"
        )
        db9.execSQL(
            "INSERT INTO transactions (id, description, amount_cents, date, type, category, source, review_status, reviewed_at) " +
                "VALUES (1, 'Groceries', -4500, '2026-08-10', 'expense', 'groceries', 'csv_import', 'pending', NULL)"
        )
        db9.execSQL(
            "INSERT INTO bill_occurrences (id, payment_id, due_date, amount_cents, is_paid, transaction_id, created_at, original_due_date, is_user_modified) " +
                "VALUES (1, 1, '2026-09-01', 120000, 0, NULL, '2026-08-20T10:00:00', '2026-09-01', 0)"
        )
        db9.execSQL("INSERT INTO settings (key, value) VALUES ('bank_balance_cents', '42500')")
        db9.execSQL("INSERT INTO settings (key, value) VALUES ('simulation_days', '90')")
        db9.execSQL(
            "INSERT INTO assets (id, name, type, balanceCents, lastUpdated) VALUES (1, 'Index fund', 'Stock', 300000, '2026-08-01')"
        )
        db9.execSQL(
            "INSERT INTO goals (id, name, targetAmountCents, currentAmountCents, deadline, createdAt) " +
                "VALUES (1, 'Emergency fund', 100000, 20000, NULL, '2026-08-01')"
        )
        db9.execSQL(
            "INSERT INTO transaction_rules (id, match_text, category, is_active, priority, created_at) " +
                "VALUES (1, 'netflix', 'subscriptions', 1, 0, '2026-08-01')"
        )
        db9.close()

        val migrated = helper.runMigrationsAndValidate(
            name, 16, true,
            *fullChain.drop(8).toTypedArray() // 9_10 .. 15_16
        )

        // Ledger rows survive.
        val txn = migrated.query("SELECT description, amount_cents, clearing_status FROM transactions WHERE id = 1")
        assertTrue(txn.moveToFirst())
        assertEquals("Groceries", txn.getString(0))
        assertEquals(-4500L, txn.getLong(1))
        assertEquals("posted", txn.getString(2))
        txn.close()

        val income = migrated.query("SELECT amount_cents, payType FROM income WHERE id = 1")
        assertTrue(income.moveToFirst())
        assertEquals(250000L, income.getLong(0))
        assertEquals("FLAT", income.getString(1))
        income.close()

        val settings = migrated.query("SELECT value FROM settings WHERE key = 'bank_balance_cents'")
        assertTrue(settings.moveToFirst())
        assertEquals("42500", settings.getString(0))
        settings.close()

        // Product-depth tables arrive.
        val accounts = migrated.query("SELECT name, balanceCents, isDefault, isReconciled FROM accounts")
        assertTrue("default account must be seeded from bank balance", accounts.moveToFirst())
        assertEquals("Primary account", accounts.getString(0))
        assertEquals(42500L, accounts.getLong(1))
        assertEquals(1, accounts.getInt(2))
        assertEquals(0, accounts.getInt(3))
        assertFalse(accounts.moveToNext())
        accounts.close()

        val debtsCount = migrated.query("SELECT COUNT(*) FROM debts")
        debtsCount.moveToFirst()
        assertEquals(0, debtsCount.getInt(0))
        debtsCount.close()

        migrated.close()
    }

    @Test
    fun migrate14To15_adoptsReconciledFlagFromLegacySettings() {
        val name = "migration-14-15"
        val db14 = helper.createDatabase(name, 14)
        db14.execSQL(
            "INSERT INTO accounts (id, name, type, balanceCents, isDefault, lastUpdated) " +
                "VALUES (1, 'Primary account', 'checking', 50000, 1, '2026-08-01')"
        )
        db14.execSQL("INSERT INTO settings (key, value) VALUES ('bank_balance_reconciled', 'true')")
        db14.close()

        val migrated = helper.runMigrationsAndValidate(name, 15, true, AppDatabase.MIGRATION_14_15_FOR_TEST)

        val account = migrated.query("SELECT isReconciled FROM accounts WHERE id = 1")
        assertTrue(account.moveToFirst())
        assertEquals("default account must adopt the legacy reconciled flag", 1, account.getInt(0))
        account.close()
        migrated.close()
    }

    // ---------------------------------------------------------------------
    // Hand-built legacy databases upgraded through the full chain
    // ---------------------------------------------------------------------

    @Test
    fun legacyV1To16_fullChain_opensThroughRoomAndPreservesLedger() = runBlocking {
        val name = "migration-legacy-v1"
        createLegacyDb(
            name,
            version = 1,
            LEGACY_V1_TABLES + listOf(
                "INSERT INTO income (name, amount_cents, frequency, day_of_month, next_date) " +
                    "VALUES ('Salary', 250000, 'MONTHLY', 15, '2026-09-15')",
                "INSERT INTO payments (name, amount_cents, frequency, day_of_month, next_date, is_active) " +
                    "VALUES ('Rent', 120000, 'Monthly', 1, '2026-09-01', 1)",
                "INSERT INTO transactions (description, amount_cents, date, type) " +
                    "VALUES ('Groceries', -4500, '2026-08-10', 'expense')",
                "INSERT INTO bill_occurrences (payment_id, due_date, amount_cents, is_paid) " +
                    "VALUES (1, '2026-09-01', 120000, 0)",
                "INSERT INTO settings (key, value) VALUES ('bank_balance_cents', '98765')",
            )
        )

        val room = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*fullChain.toTypedArray())
            .build()
        try {
            assertEquals("Room must run the whole chain on open", 16, room.openHelper.writableDatabase.version)

            val transactions = room.transactionDao().getAll().first()
            assertEquals(1, transactions.size)
            val txn = transactions.single()
            assertEquals("Groceries", txn.description)
            assertEquals(-4500L, txn.amount_cents)
            assertEquals("uncategorized", txn.category)
            assertEquals(ClearingStatus.POSTED, ClearingStatus.normalize(txn.clearing_status))
            assertNull("v1 transactions have no account tag", txn.account_id)

            val income = room.incomeDao().getAllIncomes().first().single()
            assertEquals("FLAT", income.payType)
            assertNull("legacy expectedAmountCents may be null", income.expectedAmountCents)

            val occurrences = room.billOccurrenceDao().getAll().first()
            assertEquals(1, occurrences.size)
            assertEquals(0, occurrences.single().is_user_modified)

            // Default account seeded from legacy settings.
            val defaultAccount = room.accountDao().getDefault()
            assertNotNull(defaultAccount)
            assertEquals(98765L, defaultAccount!!.balanceCents)
        } finally {
            room.close()
        }
    }

    @Test
    fun legacyV5To16_fullChain_opensThroughRoomAndPreservesLedger() = runBlocking {
        val name = "migration-legacy-v5"
        createLegacyDb(
            name,
            version = 5,
            LEGACY_V5_TABLES + listOf(
                "INSERT INTO income (name, amount_cents, frequency, day_of_month, next_date, expectedAmountCents) " +
                    "VALUES ('Freelance', 90000, 'WEEKLY', 1, '2026-09-02', 90000)",
                "INSERT INTO payments (name, amount_cents, frequency, day_of_month, next_date, is_active, isAutoWithdraw) " +
                    "VALUES ('Card bill', 15000, 'Monthly', 20, '2026-09-20', 1, 1)",
                "INSERT INTO transactions (description, amount_cents, date, type, category) " +
                    "VALUES ('Coffee', -475, '2026-08-20', 'expense', 'food')",
                "INSERT INTO assets (name, type, balanceCents, lastUpdated) VALUES ('Savings', 'Cash', 250000, '2026-08-01')",
                "INSERT INTO settings (key, value) VALUES ('bank_balance_cents', '150000')",
                "INSERT INTO settings (key, value) VALUES ('bank_balance_reconciled', 'true')",
            )
        )

        val room = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*fullChain.toTypedArray())
            .build()
        try {
            assertEquals(16, room.openHelper.writableDatabase.version)

            val txn = room.transactionDao().getAll().first().single()
            assertEquals("Coffee", txn.description)
            assertEquals("food", txn.category)
            assertEquals(ClearingStatus.POSTED, ClearingStatus.normalize(txn.clearing_status))

            val payment = room.paymentDao().getAll().first().single()
            assertTrue(payment.isAutoWithdraw)

            val asset = room.assetDao().getAllAssets().first().single()
            assertEquals(250000L, asset.balanceCents)

            // Reconciled legacy flag reaches the default account row.
            val defaultAccount = room.accountDao().getDefault()
            assertNotNull(defaultAccount)
            assertTrue(defaultAccount!!.isReconciled)
            assertEquals(150000L, defaultAccount.balanceCents)
        } finally {
            room.close()
        }
    }

    @Test
    fun legacyV1_largeLedger_migratesWithoutRowLoss() = runBlocking {
        val name = "migration-large"
        val inserts = buildList {
            addAll(LEGACY_V1_TABLES)
            add("INSERT INTO settings (key, value) VALUES ('bank_balance_cents', '0')")
            for (i in 0 until 2000) {
                add(
                    "INSERT INTO transactions (description, amount_cents, date, type) " +
                        "VALUES ('Txn $i', ${-(i + 1) * 10}, '2026-01-01', 'expense')"
                )
            }
        }
        createLegacyDb(name, version = 1, inserts)

        val room = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*fullChain.toTypedArray())
            .build()
        try {
            assertEquals(16, room.openHelper.writableDatabase.version)
            val count = room.transactionDao().getAll().first().size
            assertEquals("every legacy transaction must survive", 2000, count)
            val total = room.transactionDao().getTotalBalanceCents().first() ?: 0L
            assertEquals("sum of migrated amounts must be exact", -2000L * (2000 + 1) / 2 * 10, total)
        } finally {
            room.close()
        }
    }

    // ---------------------------------------------------------------------
    // Adversarial inputs
    // ---------------------------------------------------------------------

    @Test
    fun duplicateCaseOnlyCategoryBudgets_dedupeToSingleRowByV13() {
        val name = "migration-adversarial-budgets"
        val db9 = helper.createDatabase(name, 9)
        db9.close()

        val db12 = helper.runMigrationsAndValidate(
            name, 12, true,
            AppDatabase.MIGRATION_9_10_FOR_TEST,
            AppDatabase.MIGRATION_10_11_FOR_TEST,
            AppDatabase.MIGRATION_11_12_FOR_TEST,
        )
        db12.execSQL(
            "INSERT INTO category_budgets (id, category, limitCents, enabled, createdAt) " +
                "VALUES (1, 'dining', 10000, 1, '2026-01-01')"
        )
        db12.execSQL(
            "INSERT INTO category_budgets (id, category, limitCents, enabled, createdAt) " +
                "VALUES (2, 'Dining', 20000, 1, '2026-02-01')"
        )
        db12.close()

        val migrated = helper.runMigrationsAndValidate(
            name, 16, true,
            AppDatabase.MIGRATION_12_13_FOR_TEST,
            AppDatabase.MIGRATION_13_14_FOR_TEST,
            AppDatabase.MIGRATION_14_15_FOR_TEST,
            AppDatabase.MIGRATION_15_16_FOR_TEST,
        )

        val budgets = migrated.query("SELECT id, category, limitCents FROM category_budgets")
        var rows = 0
        var keptCents = -1L
        while (budgets.moveToNext()) {
            rows++
            keptCents = budgets.getLong(2)
        }
        budgets.close()
        assertEquals("case-only duplicates must collapse to a single budget row", 1, rows)
        assertEquals("the kept row must be the lowest id (first-seen)", 10000L, keptCents)

        // Unique index is enforced on the collapsed table.
        try {
            migrated.execSQL(
                "INSERT INTO category_budgets (category, limitCents, enabled, createdAt) " +
                    "VALUES ('dining', 1, 1, '2026-08-13')"
            )
            throw AssertionError("duplicate category must be rejected after dedupe")
        } catch (_: Exception) {
            // expected unique constraint violation
        }
        migrated.close()
    }

    // ---------------------------------------------------------------------
    // Legacy schema builders
    // ---------------------------------------------------------------------

    private fun createLegacyDb(name: String, version: Int, statements: List<String>) {
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(path, null)
        statements.forEach { db.execSQL(it) }
        db.version = version
        db.close()
    }

    private companion object {
        val LEGACY_V1_TABLES = listOf(
            "CREATE TABLE income (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "name TEXT NOT NULL, amount_cents INTEGER NOT NULL, " +
                "frequency TEXT NOT NULL, day_of_month INTEGER, next_date TEXT NOT NULL)",
            "CREATE TABLE payments (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "name TEXT NOT NULL, amount_cents INTEGER NOT NULL, " +
                "frequency TEXT NOT NULL, day_of_month INTEGER, next_date TEXT NOT NULL, " +
                "is_active INTEGER NOT NULL)",
            "CREATE TABLE transactions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "description TEXT NOT NULL, amount_cents INTEGER NOT NULL, " +
                "date TEXT NOT NULL, type TEXT NOT NULL)",
            "CREATE TABLE bill_occurrences (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "payment_id INTEGER NOT NULL, due_date TEXT NOT NULL, " +
                "amount_cents INTEGER NOT NULL, is_paid INTEGER NOT NULL, " +
                "FOREIGN KEY(payment_id) REFERENCES payments(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
            "CREATE INDEX index_bill_occurrences_payment_id ON bill_occurrences (payment_id)",
            "CREATE TABLE settings (key TEXT NOT NULL, value TEXT NOT NULL, PRIMARY KEY(key))",
        )

        val LEGACY_V5_TABLES = listOf(
            "CREATE TABLE income (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "name TEXT NOT NULL, amount_cents INTEGER NOT NULL, " +
                "frequency TEXT NOT NULL, day_of_month INTEGER, next_date TEXT NOT NULL, " +
                "expectedAmountCents INTEGER)",
            "CREATE TABLE payments (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "name TEXT NOT NULL, amount_cents INTEGER NOT NULL, " +
                "frequency TEXT NOT NULL, day_of_month INTEGER, next_date TEXT NOT NULL, " +
                "is_active INTEGER NOT NULL, isAutoWithdraw INTEGER NOT NULL)",
            "CREATE TABLE transactions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "description TEXT NOT NULL, amount_cents INTEGER NOT NULL, " +
                "date TEXT NOT NULL, type TEXT NOT NULL, category TEXT NOT NULL)",
            "CREATE TABLE bill_occurrences (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "payment_id INTEGER NOT NULL, due_date TEXT NOT NULL, " +
                "amount_cents INTEGER NOT NULL, is_paid INTEGER NOT NULL, " +
                "transaction_id INTEGER, created_at TEXT, " +
                "FOREIGN KEY(payment_id) REFERENCES payments(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
            "CREATE INDEX index_bill_occurrences_payment_id ON bill_occurrences (payment_id)",
            "CREATE TABLE settings (key TEXT NOT NULL, value TEXT NOT NULL, PRIMARY KEY(key))",
            "CREATE TABLE transaction_rules (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "match_text TEXT NOT NULL, category TEXT NOT NULL, " +
                "is_active INTEGER NOT NULL DEFAULT 1, priority INTEGER NOT NULL DEFAULT 0, " +
                "created_at TEXT NOT NULL DEFAULT '')",
            "CREATE TABLE assets (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "name TEXT NOT NULL, type TEXT NOT NULL, " +
                "balanceCents INTEGER NOT NULL, lastUpdated TEXT NOT NULL)",
        )
    }
}
