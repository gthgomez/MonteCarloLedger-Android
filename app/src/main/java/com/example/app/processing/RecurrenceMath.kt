package com.example.app.processing

import java.time.LocalDate
import java.util.Locale

object RecurrenceMath {

    fun nextDate(date: LocalDate, frequency: String, dayOfMonth: Int? = null): LocalDate? {
        val freq = normalizeFrequency(frequency)
        return when (freq) {
            "weekly" -> date.plusWeeks(1)
            "biweekly" -> date.plusWeeks(2)
            "semimonthly" -> {
                if (date.dayOfMonth >= 15) {
                    date.plusMonths(1).withDayOfMonth(1)
                } else {
                    date.withDayOfMonth(15)
                }
            }
            "monthly" -> monthlyDate(date.plusMonths(1), dayOfMonth ?: date.dayOfMonth)
            "bimonthly" -> date.plusMonths(2).let { monthlyDate(it, dayOfMonth ?: date.dayOfMonth) }
            "quarterly" -> date.plusMonths(3).let { monthlyDate(it, dayOfMonth ?: date.dayOfMonth) }
            "annually", "yearly" -> date.plusYears(1)
            "onetime" -> null
            else -> null
        }
    }

    fun previousDate(date: LocalDate, frequency: String, dayOfMonth: Int? = null): LocalDate? {
        val freq = normalizeFrequency(frequency)
        return when (freq) {
            "weekly" -> date.minusWeeks(1)
            "biweekly" -> date.minusWeeks(2)
            "semimonthly" -> {
                if (date.dayOfMonth == 1) {
                    date.minusMonths(1).withDayOfMonth(15)
                } else {
                    date.withDayOfMonth(1)
                }
            }
            "monthly" -> monthlyDate(date.minusMonths(1), dayOfMonth ?: date.dayOfMonth)
            "bimonthly" -> date.minusMonths(2).let { monthlyDate(it, dayOfMonth ?: date.dayOfMonth) }
            "quarterly" -> date.minusMonths(3).let { monthlyDate(it, dayOfMonth ?: date.dayOfMonth) }
            "annually", "yearly" -> date.minusYears(1)
            "onetime" -> null
            else -> null
        }
    }

    internal fun normalizeFrequency(frequency: String): String {
        return frequency.lowercase(Locale.ROOT).replace(" ", "").replace("-", "")
    }

    private fun monthlyDate(baseDate: LocalDate, desiredDay: Int): LocalDate {
        val targetDay = desiredDay.coerceIn(1, baseDate.lengthOfMonth())
        return baseDate.withDayOfMonth(targetDay)
    }
}
