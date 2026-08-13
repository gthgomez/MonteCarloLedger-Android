package com.montecarlo.ledger.data

internal const val APP_LOCK_SETTING_PREFIX = "app_lock_"

internal fun isAppLockSettingKey(key: String): Boolean =
    key.startsWith(APP_LOCK_SETTING_PREFIX)
