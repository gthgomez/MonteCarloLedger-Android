package com.montecarlo.ledger.sync

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VaultHttpClientSecureUrlTest {
    @Test
    fun allowsHttps() {
        assertEquals(
            "https://montecarlo-ledger.vercel.app",
            VaultHttpClient.requireSecureBaseUrl("https://montecarlo-ledger.vercel.app/"),
        )
    }

    @Test
    fun allowsLocalhostHttpOnly() {
        assertEquals(
            "http://127.0.0.1:3000",
            VaultHttpClient.requireSecureBaseUrl("http://127.0.0.1:3000"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCleartextRemote() {
        VaultHttpClient.requireSecureBaseUrl("http://example.com/vault")
    }
}
