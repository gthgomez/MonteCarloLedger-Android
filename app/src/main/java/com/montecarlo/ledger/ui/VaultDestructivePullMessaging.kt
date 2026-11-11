package com.montecarlo.ledger.ui

import com.montecarlo.ledger.sync.VaultLocalChangesException

/** User-visible copy for destructive vault pull/join; unit-tested (not Compose). */
object VaultDestructivePullMessaging {
    const val OVERWRITE_DIALOG_TITLE = "Replace local ledger?"

    val VAULT_FORMAT_DOES_NOT_UPLOAD: String =
        "Goals, assets, transaction rules, category budgets, accounts, and local settings " +
            "are not stored in the vault format (only cross-platform core ledger data is encrypted and uploaded)."

    fun vaultDialogAdvisory(): String =
        "$VAULT_FORMAT_DOES_NOT_UPLOAD Create and push upload the core ledger only. " +
            "Pull or joining an existing vault replaces the core ledger on this device."

    fun localChangesBlockedStatus(detail: String? = null): String =
        detail?.takeIf { it.isNotBlank() } ?: VaultLocalChangesException().message!!

    fun destructiveOverwriteDialogBody(): String =
        "Pulling or joining an existing vault replaces this device’s cross-platform financial records " +
            "(incomes, bills, transactions, debts) with the vault copy.\n\n" +
            VAULT_FORMAT_DOES_NOT_UPLOAD +
            " If you choose Replace, those Android-only rows will be deleted or reset on this device " +
            "(the default Primary account is re-seeded from the vault balance).\n\n" +
            "Export a standard or encrypted backup first if you need to keep anything that exists only here. " +
            "Cancel leaves the local ledger unchanged."

    fun shouldOfferDestructiveOverwriteConfirm(error: Throwable, forceOverwrite: Boolean): Boolean =
        error is VaultLocalChangesException && !forceOverwrite
}
