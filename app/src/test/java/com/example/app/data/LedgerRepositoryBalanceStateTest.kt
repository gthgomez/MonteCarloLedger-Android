package com.example.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LedgerRepositoryBalanceStateTest {

    @Test
    fun balanceState_reflectsTheSavedBankBalance() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = LedgerRepository(db)

            assertEquals(
                LedgerRepository.BalanceState(bankBalanceCents = 0, isReconciled = false),
                repo.balanceState.first()
            )

            repo.setBankBalance(12_345)

            assertEquals(
                LedgerRepository.BalanceState(bankBalanceCents = 12_345, isReconciled = true),
                repo.balanceState.first()
            )
        } finally {
            db.close()
        }
    }
}
