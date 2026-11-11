package com.montecarlo.ledger.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.montecarlo.ledger.data.AccountEntity
import com.montecarlo.ledger.data.AppDatabase
import com.montecarlo.ledger.data.AssetEntity
import com.montecarlo.ledger.data.CategoryBudgetEntity
import com.montecarlo.ledger.data.GoalEntity
import com.montecarlo.ledger.data.LedgerRepository
import com.montecarlo.ledger.data.TransactionRuleEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalLedgerPreservationTest {

    private fun openRepo(): Pair<LedgerRepository, AppDatabase> {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        return LedgerRepository(db) to db
    }

    @Test
    fun preservationSnapshotJson_includesMeaningfulAndroidOnlyFields(): Unit = runBlocking {
        val (repo, db) = openRepo()
        try {
            db.goalDao().insertGoal(
                GoalEntity(
                    name = "G",
                    targetAmountCents = 1,
                    currentAmountCents = 0,
                    deadline = "2026-01-01",
                    createdAt = "2025-01-01",
                ),
            )
            db.assetDao().insertAsset(
                AssetEntity(name = "Cash", type = "Cash", balanceCents = 100, lastUpdated = "2026-01-01"),
            )
            db.transactionRuleDao().upsert(
                TransactionRuleEntity(match_text = "coffee", category = "Food", is_active = 0, priority = 2),
            )
            db.categoryBudgetDao().insert(
                CategoryBudgetEntity(category = "Food", limitCents = 50, enabled = 0, createdAt = "2026-01-02"),
            )
            db.accountDao().insert(
                AccountEntity(
                    name = "Main",
                    type = "checking",
                    balanceCents = 10,
                    isReconciled = true,
                    isDefault = true,
                    lastUpdated = "2026-11-10",
                ),
            )
            val json = JSONObject(LocalLedgerPreservation.preservationSnapshotJson(repo))
            val goal = json.getJSONArray("goals").getJSONObject(0)
            assertEquals("2026-01-01", goal.getString("deadline"))
            assertEquals("2025-01-01", goal.getString("createdAt"))
            val asset = json.getJSONArray("assets").getJSONObject(0)
            assertEquals("2026-01-01", asset.getString("lastUpdated"))
            val rule = json.getJSONArray("rules").getJSONObject(0)
            assertEquals(0, rule.getInt("is_active"))
            val budget = json.getJSONArray("categoryBudgets").getJSONObject(0)
            assertEquals(0, budget.getInt("enabled"))
            val account = json.getJSONArray("accounts").getJSONObject(0)
            assertEquals(true, account.getBoolean("isReconciled"))
            assertEquals("2026-11-10", account.getString("lastUpdated"))
        } finally {
            db.close()
        }
    }

    @Test
    fun fingerprint_changesWhenRuleActiveFlagToggles(): Unit = runBlocking {
        val (repo, db) = openRepo()
        try {
            db.transactionRuleDao().upsert(TransactionRuleEntity(match_text = "x", category = "Misc", is_active = 1))
            val before = LocalLedgerPreservation.fingerprint(repo)
            val rules = db.transactionRuleDao().getAll().first()
            db.transactionRuleDao().upsert(rules.single().copy(is_active = 0))
            val after = LocalLedgerPreservation.fingerprint(repo)
            assertNotEquals(before, after)
        } finally {
            db.close()
        }
    }
}
