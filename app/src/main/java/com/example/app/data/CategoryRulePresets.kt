package com.example.app.data

import java.util.Locale

data class CategoryRulePreset(
    val category: String,
    val keywords: List<String>,
)

object CategoryRulePresets {
    val presets: List<CategoryRulePreset> = listOf(
        CategoryRulePreset("housing", listOf("rent", "mortgage", "apartment", "landlord", "property management")),
        CategoryRulePreset("groceries", listOf("grocery", "supermarket", "kroger", "aldi", "safeway", "publix", "whole foods", "trader joe", "walmart grocery")),
        CategoryRulePreset("dining", listOf("restaurant", "coffee", "starbucks", "doordash", "uber eats", "grubhub", "chipotle", "mcdonald", "taco bell")),
        CategoryRulePreset("utilities", listOf("electric", "water", "gas bill", "natural gas", "internet", "comcast", "xfinity", "spectrum", "verizon", "at&t", "phone bill")),
        CategoryRulePreset("transportation", listOf("gas station", "fuel", "shell", "chevron", "exxon", "bp", "uber", "lyft", "parking", "toll")),
        CategoryRulePreset("subscriptions", listOf("netflix", "spotify", "hulu", "disney", "max", "youtube", "prime video", "subscription", "patreon")),
        CategoryRulePreset("health", listOf("pharmacy", "doctor", "medical", "dental", "cvs", "walgreens", "clinic", "hospital", "fitness", "gym")),
        CategoryRulePreset("shopping", listOf("amazon", "target", "costco", "ebay", "etsy", "clothing", "store")),
        CategoryRulePreset("insurance", listOf("insurance", "geico", "progressive", "state farm", "allstate")),
        CategoryRulePreset("debt", listOf("loan", "credit card", "card payment", "student loan")),
        CategoryRulePreset("income", listOf("payroll", "paycheck", "salary", "direct deposit")),
    )

    val totalKeywordCount: Int = presets.sumOf { it.keywords.size }

    fun inferCategory(description: String): String {
        val normalized = normalize(description)
        if (normalized.isBlank()) return "uncategorized"

        return presets.firstOrNull { preset ->
            preset.keywords.any { keyword -> normalized.contains(normalize(keyword)) }
        }?.category ?: "uncategorized"
    }

    fun rulePairs(): List<Pair<String, String>> =
        presets.flatMap { preset ->
            preset.keywords.map { keyword -> keyword to preset.category }
        }

    private fun normalize(value: String): String =
        value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
}
