package com.example.mojerozliczenia

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_USER_ID = "user_id"
    }

    // Zapisz ID użytkownika (Zaloguj)
    fun saveUserSession(userId: Long) {
        val editor = prefs.edit()
        editor.putLong(KEY_USER_ID, userId)
        editor.apply()
    }

    // Pobierz ID (Sprawdź czy zalogowany) - zwraca -1 jeśli brak
    fun fetchUserId(): Long {
        return prefs.getLong(KEY_USER_ID, -1)
    }

    // Wyczyść dane (Wyloguj)
    fun clearSession() {
        val editor = prefs.edit()
        editor.clear()
        editor.apply()
    }
}