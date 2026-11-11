package com.montecarlo.ledger.sync

open class VaultSyncException(message: String) : Exception(message)

class VaultAuthException : VaultSyncException("Wrong passphrase for this vault.")

class VaultConflictException(val code: String) : VaultSyncException(code)

class VaultLocalChangesException :
    VaultSyncException(
        "Local ledger has unsynchronized edits or Android-only records (goals, accounts, rules, budgets, " +
            "assets, local settings) that are not stored in the vault. Export a standard or encrypted backup " +
            "before overwriting.",
    )

class VaultAncestryException :
    VaultSyncException("No synchronized base for this vault. Pull before pushing.")
