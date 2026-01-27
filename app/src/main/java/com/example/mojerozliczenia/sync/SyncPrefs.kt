package com.example.mojerozliczenia.sync

import android.content.Context
import java.util.UUID

class SyncPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)

    fun getLastSyncAt(): Long {
        return prefs.getLong(KEY_LAST_SYNC_AT, 0L)
    }

    fun setLastSyncAt(value: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC_AT, value).apply()
    }

    fun getDeviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val newId = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, newId).apply()
        return newId
    }

    companion object {
        private const val KEY_LAST_SYNC_AT = "last_sync_at"
        private const val KEY_DEVICE_ID = "device_id"
    }
}
