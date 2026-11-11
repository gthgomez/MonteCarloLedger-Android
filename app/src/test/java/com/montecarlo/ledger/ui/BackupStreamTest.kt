package com.montecarlo.ledger.ui

import org.junit.Assert.assertThrows
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupStreamTest {
    @Test
    fun writeBackupBytes_nullStream_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            writeBackupBytes(null, byteArrayOf(1, 2, 3))
        }
    }

    @Test
    fun writeBackupBytes_writesPayload() {
        val out = ByteArrayOutputStream()
        writeBackupBytes(out, "ledger".toByteArray())
        assertEquals("ledger", out.toString())
    }
}
