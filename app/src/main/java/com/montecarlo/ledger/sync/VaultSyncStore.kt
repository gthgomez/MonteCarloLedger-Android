package com.montecarlo.ledger.sync

import android.content.Context

/** Persists vault metadata only — never the passphrase. */
class VaultSyncStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var vaultId: String?
        get() = prefs.getString(KEY_VAULT_ID, null)
        private set(value) = Unit

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER, DEFAULT_SERVER) ?: DEFAULT_SERVER
        private set(value) = Unit

    var revision: Int
        get() = prefs.getInt(KEY_REVISION, 0)
        private set(value) = Unit

    var ancestryEstablished: Boolean
        get() = prefs.getBoolean(KEY_ANCESTRY, false)
        private set(value) = Unit

    var syncedFingerprint: String?
        get() = prefs.getString(KEY_FINGERPRINT, null)
        private set(value) = Unit

    var syncedAndroidPreservationFingerprint: String?
        get() = prefs.getString(KEY_ANDROID_PRESERVATION, null)
        private set(value) = Unit

    /** Single durable write for post-sync state (Room may already reflect the vault). */
    fun commitSyncState(
        vaultId: String,
        serverUrl: String,
        revision: Int,
        syncedFingerprint: String,
        androidPreservationFingerprint: String,
        ancestryEstablished: Boolean,
    ): Boolean = prefs.edit()
        .putString(KEY_VAULT_ID, vaultId)
        .putString(KEY_SERVER, serverUrl.trimEnd('/'))
        .putInt(KEY_REVISION, revision)
        .putString(KEY_FINGERPRINT, syncedFingerprint)
        .putString(KEY_ANDROID_PRESERVATION, androidPreservationFingerprint)
        .putBoolean(KEY_ANCESTRY, ancestryEstablished)
        .commit()

    fun clear() {
        prefs.edit().clear().commit()
    }

    companion object {
        const val DEFAULT_SERVER = "https://montecarlo-ledger.vercel.app"
        private const val PREFS = "montecarlo_vault_sync"
        private const val KEY_VAULT_ID = "vault_id"
        private const val KEY_SERVER = "server_url"
        private const val KEY_REVISION = "revision"
        private const val KEY_ANCESTRY = "ancestry"
        private const val KEY_FINGERPRINT = "fingerprint"
        private const val KEY_ANDROID_PRESERVATION = "android_preservation_fingerprint"
    }
}
