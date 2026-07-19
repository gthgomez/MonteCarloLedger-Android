package com.example.app.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class SecretHash(
    val saltBase64: String,
    val hashBase64: String,
    val iterations: Int,
)

sealed class BackupIntegrityResult {
    data class Valid(val plaintext: String) : BackupIntegrityResult()
    data class LegacyNoIntegrity(val plaintext: String) : BackupIntegrityResult()
    data class IntegrityFailure(val message: String) : BackupIntegrityResult()
}

object SecurityUtils {
    private const val BACKUP_PREFIX = "MCL1"
    private const val LEGACY_ITERATIONS = 10000
    private const val BACKUP_ITERATIONS = 310000
    private const val APP_LOCK_ITERATIONS = 210000
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val HMAC_KEY_INFO = "MCL1-HMAC-KEY"

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

    /**
     * Encrypts [plaintext] with AES-GCM and prepends an HMAC-SHA256 integrity signature.
     *
     * The HMAC covers the original plaintext (without the integrity field).
     * The integrity field is inserted into the JSON before encryption.
     * The HMAC signing key is derived from the same PBKDF2 master key via HKDF-Expand
     * with info "[HMAC_KEY_INFO]", so it is independent of the AES key.
     */
    fun encryptWithHmac(plaintext: String, password: CharArray): String {
        val salt = ByteArray(SALT_LENGTH).apply { SecureRandom().nextBytes(this) }
        val iv = ByteArray(IV_LENGTH).apply { SecureRandom().nextBytes(this) }

        // Derive master key via PBKDF2
        val masterKey = deriveBytes(password, salt, BACKUP_ITERATIONS)
        val aesKey = SecretKeySpec(masterKey, "AES")
        val hmacKey = deriveHmacKey(masterKey)

        // Compute HMAC of plaintext (without integrity field)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(hmacKey)
        val hmacBytes = mac.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val hmacBase64 = Base64.encodeToString(hmacBytes, Base64.NO_WRAP)

        // Insert integrity field into JSON
        val plaintextWithIntegrity = insertIntegrityField(plaintext, hmacBase64)

        // Encrypt
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        val encryptedBytes = cipher.doFinal(plaintextWithIntegrity.toByteArray(Charsets.UTF_8))

        val result = salt + iv + encryptedBytes
        return "$BACKUP_PREFIX:$BACKUP_ITERATIONS:${Base64.encodeToString(result, Base64.NO_WRAP)}"
    }

    /**
     * Verifies the HMAC integrity signature on a decrypted backup [plaintext].
     *
     * Returns:
     * - [BackupIntegrityResult.Valid] when the integrity HMAC is present and matches.
     * - [BackupIntegrityResult.LegacyNoIntegrity] when no integrity field is found (legacy backup).
     * - [BackupIntegrityResult.IntegrityFailure] when the integrity HMAC is present but wrong.
     *
     * The HMAC signing key is re-derived from the salt stored in [encryptedBase64].
     */
    fun verifyIntegrity(encryptedBase64: String, plaintext: String, password: CharArray): BackupIntegrityResult {
        // Check for integrity field in the plaintext JSON
        val integrityHmac = extractIntegrityField(plaintext)

        if (integrityHmac == null) {
            return BackupIntegrityResult.LegacyNoIntegrity(plaintext)
        }

        // Extract salt from the encrypted envelope
        val (salt, iterations) = parseEnvelope(encryptedBase64)

        // Strip integrity field to get original plaintext for HMAC computation
        val plaintextWithoutIntegrity = stripIntegrityField(plaintext)

        // Re-derive HMAC key
        val masterKey = deriveBytes(password, salt, iterations)
        val hmacKey = deriveHmacKey(masterKey)

        // Verify HMAC
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(hmacKey)
        val expectedHmac = Base64.decode(integrityHmac, Base64.NO_WRAP)
        val actualHmac = mac.doFinal(plaintextWithoutIntegrity.toByteArray(Charsets.UTF_8))

        return if (MessageDigest.isEqual(expectedHmac, actualHmac)) {
            BackupIntegrityResult.Valid(plaintextWithoutIntegrity)
        } else {
            BackupIntegrityResult.IntegrityFailure(
                "Backup integrity check failed. The file may have been tampered with."
            )
        }
    }

    // ── internal helpers (exposed for testing) ──────────────────────

    internal fun insertIntegrityField(jsonText: String, hmacBase64: String): String {
        val lastBrace = jsonText.lastIndexOf("}")
        check(lastBrace >= 0) { "Invalid JSON: missing closing brace" }
        val before = jsonText.substring(0, lastBrace)
        val needsComma = !before.trimEnd().endsWith(",")
        val comma = if (needsComma) "," else ""
        return before + "${comma}\n  \"integrity\": \"${jsonEscape(hmacBase64)}\"\n}"
    }

    internal fun stripIntegrityField(jsonText: String): String {
        // Remove the integrity field and its preceding comma + newline.
        // Format is always: ",\n  \"integrity\": \"<base64>\"\n" inserted before "}".
        return jsonText.replace(
            Regex(""",\n\s*"integrity"\s*:\s*"[^"]*"\n"""), ""
        )
    }

    internal fun extractIntegrityField(jsonText: String): String? {
        return try {
            val root = org.json.JSONObject(jsonText)
            root.optString("integrity", "").takeIf { it.isNotBlank() }
        } catch (_: org.json.JSONException) {
            null // Malformed JSON — treat as no integrity field
        }
    }

    // ── private internals ───────────────────────────────────────────

    private data class EnvelopeParts(val salt: ByteArray, val iterations: Int)

    private fun parseEnvelope(encryptedBase64: String): EnvelopeParts {
        val trimmed = encryptedBase64.trim()
        val parts = trimmed.split(":", limit = 3)
        val iterations = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 }
            ?: LEGACY_ITERATIONS
        val payload = if (parts.size == 3 && parts[0] == BACKUP_PREFIX) parts[2] else trimmed
        val combined = Base64.decode(payload, Base64.NO_WRAP)
        val salt = combined.sliceArray(0 until SALT_LENGTH)
        return EnvelopeParts(salt, iterations)
    }

    private fun deriveHmacKey(masterKey: ByteArray): SecretKeySpec {
        // HKDF-Expand(PRK=masterKey, info="MCL1-HMAC-KEY" || 0x01, L=32)
        val hkdf = Mac.getInstance("HmacSHA256")
        hkdf.init(SecretKeySpec(masterKey, "HmacSHA256"))
        val info = HMAC_KEY_INFO.toByteArray(Charsets.UTF_8) + 0x01.toByte()
        val hmacKeyBytes = hkdf.doFinal(info)
        return SecretKeySpec(hmacKeyBytes, "HmacSHA256")
    }

    private fun deriveAesKey(secret: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec =
        SecretKeySpec(deriveBytes(secret, salt, iterations), "AES")

    private fun deriveBytes(secret: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(secret, salt, iterations, KEY_LENGTH)
        return factory.generateSecret(spec).encoded
    }

    private fun jsonEscape(value: String): String {
        val out = StringBuilder(value.length + 8)
        value.forEach { ch ->
            when (ch) {
                '\\' -> out.append("\\\\")
                '"' -> out.append("\\\"")
                '\b' -> out.append("\\b")
                '' -> out.append("\\f")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> {
                    if (ch < ' ') {
                        out.append("\\u")
                        out.append(ch.code.toString(16).padStart(4, '0'))
                    } else {
                        out.append(ch)
                    }
                }
            }
        }
        return out.toString()
    }
}
