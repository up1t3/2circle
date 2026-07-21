package com.twocircle.bike.feature.social.ui.profile

import androidx.lifecycle.ViewModel
import com.twocircle.bike.feature.auth.data.AuthRepository
import com.twocircle.bike.feature.auth.data.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val user: StateFlow<User?> = authRepository.user

    fun logout() {
        authRepository.logout()
    }
}
