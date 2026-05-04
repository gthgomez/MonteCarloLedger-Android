package com.example.app.data

data class ReminderPreferences(
    val enabled: Boolean = false,
    val weeklyCheckInEnabled: Boolean = false,
    val billRemindersEnabled: Boolean = false,
    val billReminderDaysBefore: Int = 3,
)
