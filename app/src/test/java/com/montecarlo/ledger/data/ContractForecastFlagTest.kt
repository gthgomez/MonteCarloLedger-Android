package com.montecarlo.ledger.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * MC-06b follow-up: the contract-adoption flag is a persisted setting, not a process-wide var.
 * It must default to ON for installs that never stored it and round-trip through Room.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContractForecastFlagTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: LedgerRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = LedgerRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun flag_defaultsToOnWhenNeverStored() = runBlocking {
        assertTrue(repo.getContractForecastEnabled())
        assertTrue(repo.contractForecastEnabled.first())
    }

    @Test
    fun flag_roundTripsThroughSettings() = runBlocking {
        repo.setContractForecastEnabled(false)
        assertFalse(repo.getContractForecastEnabled())
        assertFalse(repo.contractForecastEnabled.first())

        repo.setContractForecastEnabled(true)
        assertTrue(repo.getContractForecastEnabled())
        assertTrue(repo.contractForecastEnabled.first())
    }

    @Test
    fun flag_absentSettingKeyIsNotTreatedAsOff() = runBlocking {
        // A fresh install has no row at all; absence must mean ON, not false.
        assertTrue(db.settingsDao().getValue("contract_forecast_enabled") == null)
        assertTrue(repo.getContractForecastEnabled())
    }
}
