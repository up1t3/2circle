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

/**
 * Типизированные коды ошибок вместо захардкоженных строк.
 * UI разрешает код в локализованную строку через stringResource().
 */
sealed interface AuthError {
    data object InvalidEmail : AuthError
    data object WeakPassword : AuthError
    data object EmptyName : AuthError
    data object PasswordMismatch : AuthError
    data object TermsNotAccepted : AuthError
    data object NetworkError : AuthError
    data object GenericError : AuthError
}

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data class Success(val user: User) : LoginUiState
    data class Error(val error: AuthError) : LoginUiState
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun login(email: String, password: String) {
        if (!email.contains("@")) {
            _uiState.value = LoginUiState.Error(AuthError.InvalidEmail)
            return
        }
        if (password.length < 8) {
            _uiState.value = LoginUiState.Error(AuthError.WeakPassword)
            return
        }

        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            authRepository.login(email, password)
                .onSuccess { user -> _uiState.value = LoginUiState.Success(user) }
                .onFailure { _uiState.value = LoginUiState.Error(AuthError.NetworkError) }
        }
    }

    fun resetState() {
        _uiState.value = LoginUiState.Idle
    }
}
