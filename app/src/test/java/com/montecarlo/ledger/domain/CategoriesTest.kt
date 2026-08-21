package com.montecarlo.ledger.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoriesTest {

    @Test
    fun normalizeTrimsLowercasesAndCollapsesWhitespace() {
        assertEquals("groceries run", Categories.normalize("  Groceries   RUN "))
        assertEquals("", Categories.normalize("   "))
    }

    @Test
    fun uncategorizedDetectionCoversBlankAndCaseVariants() {
        assertTrue(Categories.isUncategorized("Uncategorized"))
        assertTrue(Categories.isUncategorized(" UNCATEGORIZED "))
        assertTrue(Categories.isUncategorized(""))
        assertFalse(Categories.isUncategorized("food"))
    }

    @Test
    fun normalizeOrUncategorizedMapsBlankToCanonicalValue() {
        assertEquals("uncategorized", Categories.normalizeOrUncategorized(""))
        assertEquals("uncategorized", Categories.normalizeOrUncategorized("  "))
        assertEquals("food", Categories.normalizeOrUncategorized(" FOOD "))
    }

    @Test
    fun billCategoryMembershipIsNormalizationAgnostic() {
        assertTrue(Categories.isBillCategory("Rent"))
        assertTrue(Categories.isBillCategory(" utilities "))
        assertFalse(Categories.isBillCategory("groceries"))
    }
}
