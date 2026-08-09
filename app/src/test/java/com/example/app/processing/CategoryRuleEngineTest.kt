package com.example.app.processing

import com.example.app.data.TransactionRuleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryRuleEngineTest {

    @Test
    fun categorize_blankDescription_returnsFallback() {
        val result = CategoryRuleEngine.categorize("")
        assertEquals("uncategorized", result.category)
        assertEquals(RuleSource.FALLBACK, result.source)
        assertNull(result.matchedRule)
    }

    @Test
    fun categorize_tier1_userExactMatchTakesPrecedence() {
        val userRules = listOf(
            TransactionRuleEntity(id = 1, match_text = "STARBUCKS #1024", category = "Coffee Special", priority = 10),
            TransactionRuleEntity(id = 2, match_text = "starbucks", category = "Dining", priority = 1),
        )

        val result = CategoryRuleEngine.categorize("Starbucks #1024", userRules)
        assertEquals("Coffee Special", result.category)
        assertEquals(RuleSource.USER_EXACT, result.source)
        assertNotNull(result.matchedRule)
        assertEquals(1, result.matchedRule?.id)
    }

    @Test
    fun categorize_tier2_userKeywordMatchTakesPrecedenceOverPresets() {
        val userRules = listOf(
            TransactionRuleEntity(id = 1, match_text = "walmart", category = "General Merchandise", priority = 5),
        )

        // "walmart" in presets resolves to "groceries", but user rule overrides it!
        val result = CategoryRuleEngine.categorize("WALMART SUPERCENTER #552", userRules)
        assertEquals("General Merchandise", result.category)
        assertEquals(RuleSource.USER_KEYWORD, result.source)
        assertEquals(1, result.matchedRule?.id)
    }

    @Test
    fun categorize_tier3_presetDefaultKeywordMatch() {
        // No user rules provided
        val result = CategoryRuleEngine.categorize("NETFLIX.COM DIG SUBSCR")
        assertEquals("subscriptions", result.category)
        assertEquals(RuleSource.PRESET_DEFAULT, result.source)
    }

    @Test
    fun categorize_tier4_unrecognizedMerchantReturnsFallback() {
        val result = CategoryRuleEngine.categorize("UNKNOWN_VENDOR_XYZ_987")
        assertEquals("uncategorized", result.category)
        assertEquals(RuleSource.FALLBACK, result.source)
    }

    @Test
    fun categorize_ignoresInactiveUserRules() {
        val userRules = listOf(
            TransactionRuleEntity(id = 1, match_text = "starbucks", category = "Coffee", is_active = 0),
        )

        val result = CategoryRuleEngine.categorize("STARBUCKS STORE 12", userRules)
        // Inactive rule is skipped, preset matches "dining"
        assertEquals("dining", result.category)
        assertEquals(RuleSource.PRESET_DEFAULT, result.source)
    }
}
