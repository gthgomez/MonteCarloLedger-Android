package com.montecarlo.ledger.ui

import com.montecarlo.ledger.AppUiState
import com.montecarlo.ledger.data.AccountEntity
import com.montecarlo.ledger.data.OnboardingProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppViewBackupPayloadTest {

    @Test
    fun defaultUiAccountsList_isEmpty_andOmitsAccountNames() {
        val json = buildAppViewBackupPayload(
            exportedAtIso = "2026-11-11T12:00:00",
            uiState = AppUiState(),
            incomes = emptyList(),
            payments = emptyList(),
            transactions = emptyList(),
            billOccurrences = emptyList(),
            accounts = emptyList(),
            onboardingProgress = OnboardingProgress(),
            settings = emptyList(),
            rules = emptyList(),
        )
        assertTrue(json.contains("\"accounts\":[]") || json.contains("\"accounts\": []"))
        assertFalse(json.contains("Savings"))
    }

    @Test
    fun wiring_includesAccountsFromViewModelCollection() {
        val json = buildAppViewBackupPayload(
            exportedAtIso = "2026-11-11T12:00:00",
            uiState = AppUiState(),
            incomes = emptyList(),
            payments = emptyList(),
            transactions = emptyList(),
            billOccurrences = emptyList(),
            accounts = listOf(
                AccountEntity(
                    id = 3,
                    name = "Savings",
                    type = "savings",
                    balanceCents = 99,
                    isDefault = false,
                    lastUpdated = "2026-11-11",
                ),
            ),
            onboardingProgress = OnboardingProgress(),
            settings = emptyList(),
            rules = emptyList(),
        )
        assertTrue(json.contains("Savings"))
        val snapshot = parseLedgerBackupJson(json)
        assertEquals(1, snapshot.accounts.size)
    }
}
