package com.twocircle.bike.feature.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twocircle.bike.feature.auth.data.AuthRepository
import com.twocircle.bike.feature.auth.data.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RegisterUiState {
    data object Idle : RegisterUiState
    data object Loading : RegisterUiState
    data class Success(val user: User) : RegisterUiState
    data class Error(val error: AuthError) : RegisterUiState
}

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<RegisterUiState>(RegisterUiState.Idle)
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun register(name: String, email: String, password: String, confirmPass: String, termsAccepted: Boolean) {
        if (name.isBlank()) {
            _uiState.value = RegisterUiState.Error(AuthError.EmptyName)
            return
        }
        if (!email.contains("@")) {
            _uiState.value = RegisterUiState.Error(AuthError.InvalidEmail)
            return
        }
        if (password.length < 8) {
            _uiState.value = RegisterUiState.Error(AuthError.WeakPassword)
            return
        }
        if (password != confirmPass) {
            _uiState.value = RegisterUiState.Error(AuthError.PasswordMismatch)
            return
        }
        if (!termsAccepted) {
            _uiState.value = RegisterUiState.Error(AuthError.TermsNotAccepted)
            return
        }

        viewModelScope.launch {
            _uiState.value = RegisterUiState.Loading
            authRepository.register(email, password, name)
                .onSuccess { user -> _uiState.value = RegisterUiState.Success(user) }
                .onFailure { _uiState.value = RegisterUiState.Error(AuthError.NetworkError) }
        }
    }

    fun resetState() {
        _uiState.value = RegisterUiState.Idle
    }
}
