package com.example.mojerozliczenia

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
            _uiState.value = _uiState.value.copy(error = "Wypełnij wszystkie pola")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val hashedPassword = SecurityUtils.hashPassword(password)

            if (_uiState.value.isLoginMode) {
                val user = dao.getUserByName(username)
                if (user != null && user.passwordHash == hashedPassword) {
                    if (rememberMe) {
                        sessionManager.saveUserSession(user.userId)
                    }
                    onSuccess(user.userId)
                } else {
                    _uiState.value = _uiState.value.copy(error = "Błędny login lub hasło", isLoading = false)
                }
            } else {
                val existingUser = dao.getUserByName(username)
                if (existingUser != null) {
                    _uiState.value = _uiState.value.copy(error = "Użytkownik już istnieje", isLoading = false)
                } else {
                    val newUser = User(username = username, passwordHash = hashedPassword)
                    val newId = dao.insertUser(newUser)
                    if (rememberMe) {
                        sessionManager.saveUserSession(newId)
                    }
                    onSuccess(newId)
                }
            }
        }
    }

    // --- TO JEST FUNKCJA, KTÓREJ BRAKOWAŁO ---
    fun loginWithBiometrics(onSuccess: (Long) -> Unit) {
        val username = _uiState.value.username.trim()
        if (username.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Wpisz login, aby użyć biometrii")
            return
        }

        viewModelScope.launch {
            val user = dao.getUserByName(username)
            if (user != null) {
                // Sukces! Logujemy usera (można też zapisać sesję)
                sessionManager.saveUserSession(user.userId)
                onSuccess(user.userId)
            } else {
                _uiState.value = _uiState.value.copy(error = "Nie znaleziono użytkownika o takim loginie")
            }
        }
    }
}