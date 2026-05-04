package com.example.app.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class SecretHash(
    val saltBase64: String,
    val hashBase64: String,
    val iterations: Int,
)

object SecurityUtils {
    private const val BACKUP_PREFIX = "MCL1"
    private const val LEGACY_ITERATIONS = 10000
    private const val BACKUP_ITERATIONS = 310000
    private const val APP_LOCK_ITERATIONS = 210000
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12

    fun encrypt(data: String, password: CharArray): String {
        val salt = ByteArray(SALT_LENGTH).apply { SecureRandom().nextBytes(this) }
        val iv = ByteArray(IV_LENGTH).apply { SecureRandom().nextBytes(this) }
        val key = deriveAesKey(password, salt, BACKUP_ITERATIONS)
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        
        val encryptedBytes = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        
        val result = salt + iv + encryptedBytes
        return "$BACKUP_PREFIX:$BACKUP_ITERATIONS:${Base64.encodeToString(result, Base64.NO_WRAP)}"
    }

    fun decrypt(encryptedBase64: String, password: CharArray): String {
        val trimmed = encryptedBase64.trim()
        val parts = trimmed.split(":", limit = 3)
        val iterations: Int
        val payload: String

        if (parts.size == 3 && parts[0] == BACKUP_PREFIX) {
            iterations = parts[1].toIntOrNull()?.takeIf { it > 0 }
                ?: throw IllegalArgumentException("Unsupported encrypted backup header.")
            payload = parts[2]
        } else {
            iterations = LEGACY_ITERATIONS
            payload = trimmed
        }

        val combined = Base64.decode(payload, Base64.NO_WRAP)
        
        val salt = combined.sliceArray(0 until SALT_LENGTH)
        val iv = combined.sliceArray(SALT_LENGTH until SALT_LENGTH + IV_LENGTH)
        val encryptedData = combined.sliceArray(SALT_LENGTH + IV_LENGTH until combined.size)
        val key = deriveAesKey(password, salt, iterations)
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        
        return String(cipher.doFinal(encryptedData), Charsets.UTF_8)
    }

    fun hashAppLockSecret(secret: CharArray): SecretHash {
        val salt = ByteArray(SALT_LENGTH).apply { SecureRandom().nextBytes(this) }
        val hash = deriveBytes(secret, salt, APP_LOCK_ITERATIONS)
        return SecretHash(
            saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
            hashBase64 = Base64.encodeToString(hash, Base64.NO_WRAP),
            iterations = APP_LOCK_ITERATIONS,
        )
    }

    fun verifyAppLockSecret(secret: CharArray, expected: SecretHash): Boolean {
        val salt = Base64.decode(expected.saltBase64, Base64.NO_WRAP)
        val expectedHash = Base64.decode(expected.hashBase64, Base64.NO_WRAP)
        val actualHash = deriveBytes(secret, salt, expected.iterations)
        return MessageDigest.isEqual(expectedHash, actualHash)
    }

    private fun deriveAesKey(secret: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec =
        SecretKeySpec(deriveBytes(secret, salt, iterations), "AES")

    private fun deriveBytes(secret: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(secret, salt, iterations, KEY_LENGTH)
        return factory.generateSecret(spec).encoded
    }
}
