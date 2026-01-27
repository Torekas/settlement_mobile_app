package com.example.mojerozliczenia.sync

data class AuthRequest(
    val username: String,
    val password: String
)

data class AuthResponse(
    val token: String,
    val userSyncId: String
)
