package com.montecarlo.ledger.sync

import android.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VaultCryptoTest {
    @Test
    fun deriveKeys_matchesPbkdf2Parameters() {
        val salt = ByteArray(16) { 7 }
        val derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec("passphrase".toCharArray(), salt, 100_000, 512))
            .encoded
        val expectedAuth = Base64.encodeToString(derived.copyOfRange(0, 32), Base64.NO_WRAP)
        val keys = VaultCrypto.deriveKeys("passphrase", salt)
        assertEquals(expectedAuth, keys.authToken)
        assertEquals(derived.copyOfRange(32, 64).toList(), keys.encKey.toList())
    }

    @Test
    fun encryptDecrypt_roundTrip() {
        val salt = ByteArray(16) { 1 }
        val keys = VaultCrypto.deriveKeys("secret", salt)
        val payload = VaultCrypto.encrypt("""{"version":"2.1"}""", keys.encKey)
        val plain = VaultCrypto.decrypt(payload.ciphertextB64, payload.nonceB64, keys.encKey)
        assertEquals("""{"version":"2.1"}""", plain)
    }
}
