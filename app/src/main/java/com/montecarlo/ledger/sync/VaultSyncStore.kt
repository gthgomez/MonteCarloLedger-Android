package com.montecarlo.ledger.sync

import android.content.Context

/** Persists vault metadata only — never the passphrase. */
class VaultSyncStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var vaultId: String?
        get() = prefs.getString(KEY_VAULT_ID, null)
        set(value) = prefs.edit().putString(KEY_VAULT_ID, value).apply()

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER, DEFAULT_SERVER) ?: DEFAULT_SERVER
        set(value) = prefs.edit().putString(KEY_SERVER, value.trimEnd('/')).apply()

    var revision: Int
        get() = prefs.getInt(KEY_REVISION, 0)
        set(value) = prefs.edit().putInt(KEY_REVISION, value).apply()

    var ancestryEstablished: Boolean
        get() = prefs.getBoolean(KEY_ANCESTRY, false)
        set(value) = prefs.edit().putBoolean(KEY_ANCESTRY, value).apply()

    var syncedFingerprint: String?
        get() = prefs.getString(KEY_FINGERPRINT, null)
        set(value) = prefs.edit().putString(KEY_FINGERPRINT, value).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val DEFAULT_SERVER = "https://montecarlo-ledger.vercel.app"
        private const val PREFS = "montecarlo_vault_sync"
        private const val KEY_VAULT_ID = "vault_id"
        private const val KEY_SERVER = "server_url"
        private const val KEY_REVISION = "revision"
        private const val KEY_ANCESTRY = "ancestry"
        private const val KEY_FINGERPRINT = "fingerprint"
    }
}
