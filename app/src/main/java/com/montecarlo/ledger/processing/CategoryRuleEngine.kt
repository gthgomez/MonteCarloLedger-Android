package com.montecarlo.ledger.processing

import com.montecarlo.ledger.data.CategoryRulePresets
import com.montecarlo.ledger.data.TransactionRuleEntity
import java.util.Locale

enum class RuleSource {
    USER_EXACT,
    USER_KEYWORD,
    PRESET_DEFAULT,
    FALLBACK
}

data class CategorizationResult(
    val category: String,
    val source: RuleSource,
    val matchedRule: TransactionRuleEntity? = null,
)

object CategoryRuleEngine {

    fun categorize(
        description: String,
        userRules: List<TransactionRuleEntity> = emptyList(),
    ): CategorizationResult {
        val normalizedDescription = normalize(description)
        if (normalizedDescription.isBlank()) {
            return CategorizationResult("uncategorized", RuleSource.FALLBACK)
        }

        val activeRules = userRules.filter { it.is_active != 0 }

        // Tier 1: User Exact Match
        val exactMatch = activeRules.firstOrNull { rule ->
            normalize(rule.match_text) == normalizedDescription
        }
        if (exactMatch != null) {
            return CategorizationResult(
                category = exactMatch.category,
                source = RuleSource.USER_EXACT,
                matchedRule = exactMatch,
            )
        }

        // Tier 2: User Pattern / Keyword Match
        val patternMatch = activeRules.firstOrNull { rule ->
            val ruleNormalized = normalize(rule.match_text)
            ruleNormalized.isNotBlank() && normalizedDescription.contains(ruleNormalized)
        }
        if (patternMatch != null) {
            return CategorizationResult(
                category = patternMatch.category,
                source = RuleSource.USER_KEYWORD,
                matchedRule = patternMatch,
            )
        }

        // Tier 3: Default Preset Keyword Match
        val presetCategory = CategoryRulePresets.inferCategory(description)
        if (presetCategory != "uncategorized") {
            return CategorizationResult(
                category = presetCategory,
                source = RuleSource.PRESET_DEFAULT,
            )
        }

        // Tier 4: Fallback
        return CategorizationResult("uncategorized", RuleSource.FALLBACK)
    }

    internal fun normalize(value: String): String =
        value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
}
