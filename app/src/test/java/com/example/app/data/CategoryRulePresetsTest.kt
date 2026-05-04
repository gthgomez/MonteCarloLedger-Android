package com.example.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryRulePresetsTest {

    @Test
    fun inferCategory_mapsCommonMerchantKeywords() {
        assertEquals("dining", CategoryRulePresets.inferCategory("STARBUCKS STORE 123"))
        assertEquals("subscriptions", CategoryRulePresets.inferCategory("Netflix.com monthly"))
        assertEquals("utilities", CategoryRulePresets.inferCategory("Xfinity internet autopay"))
        assertEquals("uncategorized", CategoryRulePresets.inferCategory("Unknown merchant"))
    }

    @Test
    fun presetsProvideEnoughStarterKeywordsForOneTapSetup() {
        assertTrue(CategoryRulePresets.totalKeywordCount >= 50)
    }
}
