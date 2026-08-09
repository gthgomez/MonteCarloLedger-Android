package com.example.app.security

import android.util.Base64
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecurityUtilsTest {

    @Test
    fun encryptedBackup_usesVersionedEnvelopeAndRoundTrips() {
        val encrypted = SecurityUtils.encrypt("ledger payload", "correct horse".toCharArray())

        assertTrue(encrypted.startsWith("MCL1:310000:"))
        assertEquals("ledger payload", SecurityUtils.decrypt(encrypted, "correct horse".toCharArray()))
    }

    @Test
    fun encryptedBackup_stillReadsLegacyPayloads() {
        val legacy = legacyEncrypt("old ledger", "password".toCharArray())

        assertEquals("old ledger", SecurityUtils.decrypt(legacy, "password".toCharArray()))
    }

    @Test
    fun appLockSecret_verifiesOnlyTheCorrectPin() {
        val hash = SecurityUtils.hashAppLockSecret("1234".toCharArray())

        assertTrue(SecurityUtils.verifyAppLockSecret("1234".toCharArray(), hash))
        assertFalse(SecurityUtils.verifyAppLockSecret("9999".toCharArray(), hash))
    }

    @Test
    fun encryptWithHmac_roundTripsAndVerifiesIntegrity() {
        val json = """{"schemaVersion":2,"data":"test payload"}"""
        val password = "correct horse".toCharArray()

        val encrypted = SecurityUtils.encryptWithHmac(json, password)
        assertTrue(encrypted.startsWith("MCL1:310000:"))

        // Decrypt and verify
        val decrypted = SecurityUtils.decrypt(encrypted, password)
        val result = SecurityUtils.verifyIntegrity(encrypted, decrypted, password)

        assertTrue(result is BackupIntegrityResult.Valid)
        val valid = result as BackupIntegrityResult.Valid
        assertEquals(json, valid.plaintext)
    }

    @Test
    fun verifyIntegrity_detectsTamperedPlaintext() {
        val json = """{"schemaVersion":2,"data":"original"}"""
        val password = "secret123".toCharArray()

        val encrypted = SecurityUtils.encryptWithHmac(json, password)

        // Decrypt with correct password
        val decrypted = SecurityUtils.decrypt(encrypted, password)

        // Tamper with the encrypted envelope HMAC signature
        val tamperedEnvelope = encrypted.dropLast(4) + "AAAA"

        // Verify integrity should detect the mismatch
        val result = SecurityUtils.verifyIntegrity(tamperedEnvelope, decrypted, password)
        assertTrue(result is BackupIntegrityResult.IntegrityFailure)
        val failure = result as BackupIntegrityResult.IntegrityFailure
        assertTrue(failure.message.contains("integrity check failed"))
    }

    @Test
    fun verifyIntegrity_detectsWrongPasswordViaAesGcm() {
        val json = """{"schemaVersion":2,"data":"secret"}"""
        val encrypted = SecurityUtils.encryptWithHmac(json, "right".toCharArray())

        // Wrong password should fail at AES-GCM level
        val exception = try {
            SecurityUtils.decrypt(encrypted, "wrong".toCharArray())
            null
        } catch (e: Exception) {
            e
        }
        assertTrue(exception is javax.crypto.AEADBadTagException)
    }

    @Test
    fun verifyIntegrity_acceptsLegacyBackupWithoutIntegrity() {
        // Legacy backup = no integrity field in plaintext
        val legacy = legacyEncrypt("""{"schemaVersion":1,"data":"old"}""", "password".toCharArray())

        val decrypted = SecurityUtils.decrypt(legacy, "password".toCharArray())
        val result = SecurityUtils.verifyIntegrity(legacy, decrypted, "password".toCharArray())

        assertTrue(result is BackupIntegrityResult.LegacyNoIntegrity)
        val legacyResult = result as BackupIntegrityResult.LegacyNoIntegrity
        assertEquals("""{"schemaVersion":1,"data":"old"}""", legacyResult.plaintext)
    }

    @Test
    fun insertAndStripIntegrityField_roundTrips() {
        val json = """{
  "schemaVersion": 2,
  "summary": {
    "bankBalanceCents": 100
  },
  "incomes": [],
  "payments": []
}"""

        val hmac = "abc123def456+/="
        val withIntegrity = SecurityUtils.insertIntegrityField(json, hmac)

        // Should contain the integrity field
        assertTrue(withIntegrity.contains("\"integrity\""))
        assertTrue(withIntegrity.contains(hmac))

        // Should still be valid JSON
        val root = org.json.JSONObject(withIntegrity)
        assertEquals("abc123def456+/=", root.getString("integrity"))

        // Strip it back
        val stripped = SecurityUtils.stripIntegrityField(withIntegrity)
        assertFalse(stripped.contains("\"integrity\""))
        // After stripping, should parse to the same data (ignoring whitespace)
        val originalParsed = org.json.JSONObject(json)
        val strippedParsed = org.json.JSONObject(stripped)
        assertEquals(originalParsed.getInt("schemaVersion"), strippedParsed.getInt("schemaVersion"))
    }

    @Test
    fun extractIntegrityField_returnsNullWhenMissing() {
        val json = """{"schemaVersion":2,"data":"no integrity"}"""
        assertNull(SecurityUtils.extractIntegrityField(json))
    }

    @Test
    fun extractIntegrityField_returnsNullForMalformedJson() {
        assertNull(SecurityUtils.extractIntegrityField("not valid json {{{"))
    }

    private fun legacyEncrypt(data: String, password: CharArray): String {
        val salt = ByteArray(16).apply { SecureRandom().nextBytes(this) }
        val iv = ByteArray(12).apply { SecureRandom().nextBytes(this) }
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, 10000, 256)
        val key = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return Base64.encodeToString(salt + iv + cipher.doFinal(data.toByteArray()), Base64.NO_WRAP)
    }
}
