package com.example.mojerozliczenia

object SyncState {
    const val SYNCED = 0
    const val PENDING_CREATE = 1
    const val PENDING_UPDATE = 2
    const val PENDING_DELETE = 3

    fun forUpdate(currentState: Int): Int {
        return if (currentState == PENDING_CREATE) PENDING_CREATE else PENDING_UPDATE
    }
}
