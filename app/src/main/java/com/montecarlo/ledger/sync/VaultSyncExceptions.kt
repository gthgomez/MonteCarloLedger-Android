package com.montecarlo.ledger.sync

open class VaultSyncException(message: String) : Exception(message)

class VaultAuthException : VaultSyncException("Wrong passphrase for this vault.")

class VaultConflictException(val code: String) : VaultSyncException(code)

class VaultLocalChangesException :
    VaultSyncException("Local ledger has unsynchronized edits. Export a backup before overwriting.")

class VaultAncestryException :
    VaultSyncException("No synchronized base for this vault. Pull before pushing.")
