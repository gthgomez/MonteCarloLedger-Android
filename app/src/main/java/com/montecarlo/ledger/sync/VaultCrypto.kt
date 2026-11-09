package com.montecarlo.ledger.sync

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** PBKDF2 + AES-GCM matching Python/Web vault (`monte_carlo_ledger/sync.py`, `web/src/lib/crypto.ts`). */
object VaultCrypto {
    private const val ITERATIONS = 100_000
    private const val GCM_TAG_BITS = 128

    data class DerivedKeys(
        val authToken: String,
        val encKey: ByteArray,
    )

    fun deriveKeys(passphrase: String, salt: ByteArray): DerivedKeys {
        require(salt.isNotEmpty()) { "salt must not be empty" }
        val derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, 512))
            .encoded
        val authToken = Base64.encodeToString(derived.copyOfRange(0, 32), Base64.NO_WRAP)
        val encKey = derived.copyOfRange(32, 64)
        return DerivedKeys(authToken, encKey)
    }

    fun encrypt(plaintext: String, encKey: ByteArray): EncryptedPayload {
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(encKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return EncryptedPayload(
            ciphertextB64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            nonceB64 = Base64.encodeToString(nonce, Base64.NO_WRAP),
        )
    }

    fun decrypt(ciphertextB64: String, nonceB64: String, encKey: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(encKey, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, Base64.decode(nonceB64, Base64.NO_WRAP)),
        )
        val plain = cipher.doFinal(Base64.decode(ciphertextB64, Base64.NO_WRAP))
        return String(plain, Charsets.UTF_8)
    }

    data class EncryptedPayload(
        val ciphertextB64: String,
        val nonceB64: String,
    )
}
