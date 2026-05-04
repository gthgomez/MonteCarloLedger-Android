package com.example.app.security

import android.util.Base64
import org.junit.Assert.assertFalse
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
