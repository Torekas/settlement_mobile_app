package com.example.mojerozliczenia

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mojerozliczenia.sync.AuthRequest
import com.example.mojerozliczenia.sync.SyncClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


data class AuthUiState(
    val username: String = "",
    val password: String = "",
    val isLoginMode: Boolean = true,
    val isLoading: Boolean = false,
    val error: String? = null
)

class AuthViewModel(private val dao: AppDao, private val sessionManager: SessionManager) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState = _uiState.asStateFlow()

    fun onUsernameChange(newValue: String) {
        _uiState.value = _uiState.value.copy(username = newValue, error = null)
    }

    fun onPasswordChange(newValue: String) {
        _uiState.value = _uiState.value.copy(password = newValue, error = null)
    }

    fun toggleMode() {
        val currentMode = _uiState.value.isLoginMode
        _uiState.value = _uiState.value.copy(
            isLoginMode = !currentMode,
            error = null,
            username = "",
            password = ""
        )
    }

    fun authenticate(rememberMe: Boolean, onSuccess: (Long) -> Unit) {
        val username = _uiState.value.username.trim()
        val password = _uiState.value.password.trim()

        if (username.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Wypelnij wszystkie pola")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val hashedPassword = SecurityUtils.hashPassword(password)

            if (SyncClient.isConfigured()) {
                val api = SyncClient.createAuthApi()
                if (_uiState.value.isLoginMode) {
                    try {
                        val response = api.login(AuthRequest(username = username, password = password))
                        val userId = upsertLocalUser(username, hashedPassword, response.userSyncId)
                        storeSession(rememberMe, userId, response.userSyncId, response.token)
                        _uiState.value = _uiState.value.copy(isLoading = false, error = null)
                        onSuccess(userId)
                        return@launch
                    } catch (_: Exception) {
                        // fallback to local when offline
                        val local = dao.getUserByName(username)
                        if (local != null && local.passwordHash == hashedPassword) {
                            if (rememberMe) {
                                sessionManager.saveUserSession(local.userId, local.syncId)
                            }
                            _uiState.value = _uiState.value.copy(isLoading = false, error = null)
                            onSuccess(local.userId)
                            return@launch
                        }
                        _uiState.value = _uiState.value.copy(error = "Bledny login lub haslo", isLoading = false)
                        return@launch
                    }
                } else {
                    try {
                        val response = api.register(AuthRequest(username = username, password = password))
                        val userId = upsertLocalUser(username, hashedPassword, response.userSyncId)
                        storeSession(rememberMe, userId, response.userSyncId, response.token)
                        _uiState.value = _uiState.value.copy(isLoading = false, error = null)
                        onSuccess(userId)
                        return@launch
                    } catch (_: Exception) {
                        // If user exists already, try login
                        try {
                            val response = api.login(AuthRequest(username = username, password = password))
                            val userId = upsertLocalUser(username, hashedPassword, response.userSyncId)
                            storeSession(rememberMe, userId, response.userSyncId, response.token)
                            _uiState.value = _uiState.value.copy(isLoading = false, error = null)
                            onSuccess(userId)
                            return@launch
                        } catch (_: Exception) {
                            _uiState.value = _uiState.value.copy(error = "Uzytkownik juz istnieje", isLoading = false)
                            return@launch
                        }
                    }
                }
            }

            // Local-only auth fallback
            if (_uiState.value.isLoginMode) {
                val user = dao.getUserByName(username)
                if (user != null && user.passwordHash == hashedPassword) {
                    if (rememberMe) {
                        sessionManager.saveUserSession(user.userId, user.syncId)
                    }
                    _uiState.value = _uiState.value.copy(isLoading = false, error = null)
                    onSuccess(user.userId)
                } else {
                    _uiState.value = _uiState.value.copy(error = "Bledny login lub haslo", isLoading = false)
                }
            } else {
                val existingUser = dao.getUserByName(username)
                if (existingUser != null) {
                    _uiState.value = _uiState.value.copy(error = "Uzytkownik juz istnieje", isLoading = false)
                } else {
                    val now = System.currentTimeMillis()
                    val newUser = User(
                        username = username,
                        passwordHash = hashedPassword,
                        updatedAt = now,
                        syncState = SyncState.PENDING_CREATE
                    )
                    val newId = dao.insertUser(newUser)
                    if (rememberMe) {
                        sessionManager.saveUserSession(newId, newUser.syncId)
                    }
                    _uiState.value = _uiState.value.copy(isLoading = false, error = null)
                    onSuccess(newId)
                }
            }
        }
    }

    private suspend fun upsertLocalUser(username: String, hashedPassword: String, userSyncId: String): Long {
        val now = System.currentTimeMillis()
        val existingBySync = dao.getUserBySyncId(userSyncId)
        if (existingBySync != null) {
            val updated = existingBySync.copy(
                username = username,
                passwordHash = hashedPassword,
                updatedAt = now,
                syncState = SyncState.SYNCED
            )
            dao.updateUser(updated)
            return existingBySync.userId
        }

        val existingByName = dao.getUserByName(username)
        if (existingByName != null) {
            val updated = existingByName.copy(
                syncId = userSyncId,
                passwordHash = hashedPassword,
                updatedAt = now,
                syncState = SyncState.SYNCED
            )
            dao.updateUser(updated)
            return existingByName.userId
        }

        val newId = dao.insertUser(
            User(
                syncId = userSyncId,
                username = username,
                passwordHash = hashedPassword,
                updatedAt = now,
                syncState = SyncState.SYNCED
            )
        )
        return newId
    }

    private fun storeSession(rememberMe: Boolean, userId: Long, userSyncId: String, token: String) {
        if (rememberMe) {
            sessionManager.saveUserSession(userId, userSyncId, token)
        } else {
            sessionManager.saveAuthToken(token)
            sessionManager.saveUserSyncId(userSyncId)
        }
    }

    fun loginWithBiometrics(onSuccess: (Long) -> Unit) {
        val username = _uiState.value.username.trim()
        if (username.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Wpisz login, aby uzyc biometrii")
            return
        }

        viewModelScope.launch {
            val user = dao.getUserByName(username)
            if (user != null) {
                sessionManager.saveUserSession(user.userId, user.syncId)
                onSuccess(user.userId)
            } else {
                _uiState.value = _uiState.value.copy(error = "Nie znaleziono uzytkownika o takim loginie")
            }
        }
    }
}
