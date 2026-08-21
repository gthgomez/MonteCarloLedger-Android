package com.montecarlo.ledger.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CsvImportTest {

    @Test
    fun parseTransactionCsv_parsesCommonBankColumns() {
        val csv = """
            Date,Description,Amount
            2026-04-01,Groceries,-34.25
            04/02/2026,Salary,1200.00
        """.trimIndent()

        val preview = parseTransactionCsv(csvText = csv, sourceName = "bank.csv")

        assertEquals("bank.csv", preview.sourceName)
        assertEquals(2, preview.importedTransactions.size)
        assertEquals(-3_425, preview.importedTransactions[0].amount_cents)
        assertEquals("expense", preview.importedTransactions[0].type)
        assertEquals(LocalDate.of(2026, 4, 1).toString(), preview.importedTransactions[0].date)
        assertEquals(120_000, preview.importedTransactions[1].amount_cents)
        assertEquals("income", preview.importedTransactions[1].type)
        assertEquals(LocalDate.of(2026, 4, 2).toString(), preview.importedTransactions[1].date)
        assertTrue(preview.skippedRows == 0)
    }

    @Test
    fun parseTransactionCsv_supportsDebitAndCreditColumns() {
        val csv = """
            Transaction Date,Details,Debit,Credit
            2026-04-03,Coffee,4.75,
            2026-04-04,Refund,,12.00
        """.trimIndent()

        val preview = parseTransactionCsv(csvText = csv)

        assertEquals(2, preview.importedTransactions.size)
        assertEquals(-475, preview.importedTransactions[0].amount_cents)
        assertEquals("expense", preview.importedTransactions[0].type)
        assertEquals(1_200, preview.importedTransactions[1].amount_cents)
        assertEquals("income", preview.importedTransactions[1].type)
    }

    @Test
    fun parseTransactionCsv_allowsManualColumnMapping() {
        val csv = """
            Merchant,Value,Posted
            Groceries,-34.25,2026-04-01
            Salary,1200.00,2026-04-02
        """.trimIndent()

        val preview = parseTransactionCsv(
            csvText = csv,
            mapping = TransactionCsvColumnMapping(
                dateIndex = 2,
                descriptionIndex = 0,
                amountIndex = 1,
            )
        )

        assertEquals(2, preview.importedTransactions.size)
        assertEquals("Groceries", preview.importedTransactions[0].description)
        assertEquals(-3_425, preview.importedTransactions[0].amount_cents)
        assertEquals("Salary", preview.importedTransactions[1].description)
        assertEquals(120_000, preview.importedTransactions[1].amount_cents)
    }

    @Test
    fun parseTransactionCsv_keepsIdenticalSameDayCharges() {
        val csv = """
            Date,Description,Amount
            2026-04-01,Groceries,-34.25
            2026-04-01,Groceries,-34.25
        """.trimIndent()

        val preview = parseTransactionCsv(csvText = csv)

        // Two identical same-day charges are real distinct transactions; both must
        // survive parsing. Re-import protection lives in LedgerRepository.
        assertEquals(2, preview.importedTransactions.size)
        assertEquals(0, preview.skippedRows)
    }

    @Test
    fun parseBillCsv_parsesCommonBillColumns() {
        val csv = """
            Name,Amount,Frequency,Due Date,Auto Pay
            Rent,1500,Monthly,2026-04-30,yes
            Streaming,12.99,Monthly,04/15/2026,no
        """.trimIndent()

        val preview = parseBillCsv(csvText = csv, sourceName = "bills.csv", today = LocalDate.of(2026, 4, 1))

        assertEquals("bills.csv", preview.sourceName)
        assertEquals(2, preview.importedPayments.size)
        assertEquals("Rent", preview.importedPayments[0].name)
        assertEquals(150_000, preview.importedPayments[0].amount_cents)
        assertEquals("Monthly", preview.importedPayments[0].frequency)
        assertEquals(LocalDate.of(2026, 4, 30).toString(), preview.importedPayments[0].next_date)
        assertEquals(true, preview.importedPayments[0].isAutoWithdraw)
        assertEquals("Streaming", preview.importedPayments[1].name)
        assertEquals(1_299, preview.importedPayments[1].amount_cents)
        assertEquals(false, preview.importedPayments[1].isAutoWithdraw)
    }

    @Test
    fun parseBillCsv_allowsManualColumnMapping() {
        val csv = """
            Bill Name,Charge,Cadence,Next Due,Due Day,Auto Draft
            Rent,1500,Monthly,2026-04-30,30,yes
            Streaming,12.99,Monthly,04/15/2026,15,no
        """.trimIndent()

        val preview = parseBillCsv(
            csvText = csv,
            mapping = BillCsvColumnMapping(
                nameIndex = 0,
                amountIndex = 1,
                frequencyIndex = 2,
                dueDateIndex = 3,
                dueDayIndex = 4,
                autoIndex = 5,
            ),
            today = LocalDate.of(2026, 4, 1),
        )

        assertEquals(2, preview.importedPayments.size)
        assertEquals("Rent", preview.importedPayments[0].name)
        assertEquals(150_000, preview.importedPayments[0].amount_cents)
        assertEquals(true, preview.importedPayments[0].isAutoWithdraw)
        assertEquals("Streaming", preview.importedPayments[1].name)
        assertEquals(1_299, preview.importedPayments[1].amount_cents)
        assertEquals(false, preview.importedPayments[1].isAutoWithdraw)
    }

    @Test
    fun parseTransactionCsv_unsignedSingleAmountColumnDefaultsToExpense() {
        val csv = """
            Date,Details,Amount
            2026-04-03,Coffee,4.75
            2026-04-05,Salary deposit,1200.00
        """.trimIndent()

        val preview = parseTransactionCsv(csvText = csv)

        // No sign and no direction columns: spending is assumed unless the
        // description looks like income ("deposit" hint here).
        assertEquals(-475, preview.importedTransactions[0].amount_cents)
        assertEquals("expense", preview.importedTransactions[0].type)
        assertEquals(120_000, preview.importedTransactions[1].amount_cents)
        assertEquals("income", preview.importedTransactions[1].type)
    }

    @Test
    fun parseTransactionCsv_handlesEuropeanDecimalComma() {
        // EU-style exports quote amounts containing commas.
        val csv = "Date,Description,Amount\n" +
            "2026-04-03,Groceries,\"-1.234,56\"\n" +
            "2026-04-04,Cafe,\"12,50\"\n"

        val preview = parseTransactionCsv(csvText = csv)

        assertEquals(-123_456L, preview.importedTransactions[0].amount_cents)
        assertEquals(-1_250L, preview.importedTransactions[1].amount_cents)
    }

    @Test
    fun parseTransactionCsv_quotedFieldWithEmbeddedNewlineStaysOneRow() {
        val csv = "Date,Description,Amount\n" +
            "2026-04-03,\"Paid in two parts:\nsecond part followed\",10.00\n" +
            "2026-04-04,Other,20.00\n"

        val preview = parseTransactionCsv(csvText = csv)

        // The multiline memo must not split into a ghost row or truncate others.
        assertEquals(2, preview.importedTransactions.size)
        assertTrue(preview.importedTransactions[0].description.contains("second part"))
        assertEquals(0, preview.skippedRows)
    }
}
