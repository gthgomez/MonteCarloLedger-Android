package com.montecarlo.ledger.ui

import com.montecarlo.ledger.sync.VaultLocalChangesException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Helper-only: messaging strings (not Compose). */
class VaultDestructivePullMessagingTest {

    @Test
    fun destructiveOverwriteDialogBody_listsAndroidOnlyEntities() {
        val body = VaultDestructivePullMessaging.destructiveOverwriteDialogBody()
        assertTrue(body.contains("Goals", ignoreCase = true))
        assertTrue(body.contains("assets", ignoreCase = true))
        assertTrue(body.contains("transaction rules", ignoreCase = true))
        assertTrue(body.contains("category budgets", ignoreCase = true))
        assertTrue(body.contains("accounts", ignoreCase = true))
        assertTrue(body.contains("local settings", ignoreCase = true))
        assertTrue(body.contains("not stored in the vault format"))
        assertTrue(body.contains("Cancel leaves the local ledger unchanged"))
    }

    @Test
    fun localChangesBlockedStatus_preservesExceptionDetail() {
        val detail = VaultLocalChangesException().message
        assertEquals(detail, VaultDestructivePullMessaging.localChangesBlockedStatus(detail))
    }

    @Test
    fun shouldOfferDestructiveOverwriteConfirm_onlyForLocalChangesWithoutForce() {
        assertTrue(
            VaultDestructivePullMessaging.shouldOfferDestructiveOverwriteConfirm(
                VaultLocalChangesException(),
                forceOverwrite = false,
            ),
        )
        assertFalse(
            VaultDestructivePullMessaging.shouldOfferDestructiveOverwriteConfirm(
                VaultLocalChangesException(),
                forceOverwrite = true,
            ),
        )
        assertFalse(
            VaultDestructivePullMessaging.shouldOfferDestructiveOverwriteConfirm(
                RuntimeException("other"),
                forceOverwrite = false,
            ),
        )
    }
}
