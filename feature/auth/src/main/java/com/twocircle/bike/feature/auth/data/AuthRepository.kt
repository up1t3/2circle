package com.twocircle.bike.feature.auth.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Authentication repository using SharedPreferences for session persistence.
 * Ready for backend (Ktor / Retrofit API) integration.
 */
@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    init {
        // Restore session on app start
        val token = prefs.getString("access_token", null)
        if (token != null) {
            _user.value = User(
                id = prefs.getString("user_id", "") ?: "",
                email = prefs.getString("email", "") ?: "",
                displayName = prefs.getString("display_name", "") ?: "",
                city = prefs.getString("city", "Екатеринбург"),
            )
        }
    }

    suspend fun login(email: String, password: String): Result<User> {
        delay(1000) // emulate network request
        return if (email.contains("@") && password.length >= 8) {
            val user = User(
                id = "1",
                email = email,
                displayName = email.substringBefore("@").replaceFirstChar { it.uppercase() },
                city = "Екатеринбург"
            )
            prefs.edit()
                .putString("access_token", "demo-token")
                .putString("user_id", user.id)
                .putString("email", user.email)
                .putString("display_name", user.displayName)
                .putString("city", user.city)
                .apply()
            _user.value = user
            Result.success(user)
        } else {
            Result.failure(Exception("Неверный email или пароль (минимум 8 символов)"))
        }
    }

    suspend fun register(email: String, password: String, displayName: String): Result<User> {
        delay(1000)
        return if (email.contains("@") && password.length >= 8 && displayName.isNotBlank()) {
            val user = User(id = "1", email = email, displayName = displayName, city = "Екатеринбург")
            prefs.edit()
                .putString("access_token", "demo-token")
                .putString("user_id", user.id)
                .putString("email", user.email)
                .putString("display_name", user.displayName)
                .putString("city", user.city)
                .apply()
            _user.value = user
            Result.success(user)
        } else {
            Result.failure(Exception("Проверьте введённые данные"))
        }
    }

    fun logout() {
        prefs.edit().clear().apply()
        _user.value = null
    }
}
