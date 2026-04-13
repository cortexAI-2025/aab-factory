package com.aabfactory.app.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aabfactory.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthState(
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val email: String = "",
    val password: String = "",
    val isRegisterMode: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _authState = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        // Observe Firebase auth state changes
        authRepository.authStateFlow
            .onEach { user ->
                _authState.value = _authState.value.copy(isLoggedIn = user != null)
            }
            .launchIn(viewModelScope)
    }

    fun onEmailChange(email: String) {
        _authState.value = _authState.value.copy(email = email, error = null)
    }

    fun onPasswordChange(password: String) {
        _authState.value = _authState.value.copy(password = password, error = null)
    }

    fun toggleAuthMode() {
        _authState.value = _authState.value.copy(
            isRegisterMode = !_authState.value.isRegisterMode,
            error = null
        )
    }

    fun signIn() {
        val state = _authState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _authState.value = state.copy(error = "Please fill in all fields")
            return
        }
        viewModelScope.launch {
            _authState.value = state.copy(isLoading = true, error = null)
            val result = authRepository.signInWithEmail(state.email, state.password)
            _authState.value = _authState.value.copy(
                isLoading = false,
                error = result.exceptionOrNull()?.message
            )
        }
    }

    fun register() {
        val state = _authState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _authState.value = state.copy(error = "Please fill in all fields")
            return
        }
        if (state.password.length < 8) {
            _authState.value = state.copy(error = "Password must be at least 8 characters")
            return
        }
        viewModelScope.launch {
            _authState.value = state.copy(isLoading = true, error = null)
            val result = authRepository.registerWithEmail(state.email, state.password)
            _authState.value = _authState.value.copy(
                isLoading = false,
                error = result.exceptionOrNull()?.message
            )
        }
    }

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _authState.value = _authState.value.copy(isLoading = true, error = null)
            val result = authRepository.signInWithGoogle(idToken)
            _authState.value = _authState.value.copy(
                isLoading = false,
                error = result.exceptionOrNull()?.message
            )
        }
    }

    fun clearError() {
        _authState.value = _authState.value.copy(error = null)
    }
}
