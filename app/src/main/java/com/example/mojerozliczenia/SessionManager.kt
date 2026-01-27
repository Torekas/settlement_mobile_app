package com.example.mojerozliczenia

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_SYNC_ID = "user_sync_id"
        private const val KEY_AUTH_TOKEN = "auth_token"
    }

    // Zapisz ID uzytkownika (Zaloguj)
    fun saveUserSession(userId: Long, userSyncId: String? = null, authToken: String? = null) {
        val editor = prefs.edit()
        editor.putLong(KEY_USER_ID, userId)
        if (!userSyncId.isNullOrBlank()) {
            editor.putString(KEY_USER_SYNC_ID, userSyncId)
        }
        if (!authToken.isNullOrBlank()) {
            editor.putString(KEY_AUTH_TOKEN, authToken)
        }
        editor.apply()
    }

    fun saveAuthToken(token: String?) {
        val editor = prefs.edit()
        if (token.isNullOrBlank()) {
            editor.remove(KEY_AUTH_TOKEN)
        } else {
            editor.putString(KEY_AUTH_TOKEN, token)
        }
        editor.apply()
    }

    fun saveUserSyncId(syncId: String?) {
        val editor = prefs.edit()
        if (syncId.isNullOrBlank()) {
            editor.remove(KEY_USER_SYNC_ID)
        } else {
            editor.putString(KEY_USER_SYNC_ID, syncId)
        }
        editor.apply()
    }

    // Pobierz ID (Sprawdz czy zalogowany) - zwraca -1 jesli brak
    fun fetchUserId(): Long {
        return prefs.getLong(KEY_USER_ID, -1)
    }

    fun fetchUserSyncId(): String? {
        return prefs.getString(KEY_USER_SYNC_ID, null)
    }

    fun fetchAuthToken(): String? {
        return prefs.getString(KEY_AUTH_TOKEN, null)
    }

    // Wyczysc dane (Wyloguj)
    fun clearSession() {
        val editor = prefs.edit()
        editor.clear()
        editor.apply()
    }
}
