package com.example.app.data

data class AppLockPreferences(
    val enabled: Boolean = false,
    val hasPin: Boolean = false,
    val failedAttempts: Int = 0,
    val lockoutUntilEpochMs: Long = 0L,
)
