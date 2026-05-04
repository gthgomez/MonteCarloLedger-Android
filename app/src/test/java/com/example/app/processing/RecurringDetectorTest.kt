package com.example.app.processing

import com.example.app.data.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecurringDetectorTest {
    @Test
    fun detect_findsMonthlyRecurringPatternsFromRepeatedTransactions() {
        val today = LocalDate.now()
        val transactions = listOf(
            TransactionEntity(description = "Netflix", amount_cents = -1_499, date = today.minusDays(60).toString(), type = "expense", category = "subscriptions"),
            TransactionEntity(description = "Netflix", amount_cents = -1_499, date = today.minusDays(30).toString(), type = "expense", category = "subscriptions"),
            TransactionEntity(description = "Netflix", amount_cents = -1_499, date = today.toString(), type = "expense", category = "uncategorized"),
        )

        val candidates = RecurringDetector.detect(transactions)

        assertEquals(1, candidates.size)
        assertTrue(candidates.single().pattern.contains("netflix"))
        assertEquals("subscriptions", candidates.single().category)
        assertEquals("Monthly", candidates.single().cadenceLabel)
    }
}
