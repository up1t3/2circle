package com.twocircle.bike.feature.auth.data

import android.content.Context
import com.twocircle.bike.common.di.ManifestUrl
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Authentication repository — REAL API integration with Ktor backend.
 *
 * Connects to the backend server at the IP configured in BuildConfig.
 * JWT tokens are stored in SharedPreferences for session persistence.
 */
@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
) {

    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    // Backend URL — the API server
    private val baseUrl = "http://72.56.238.106:8080"

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    init {
        // Восстановление сессии при старте
        val token = prefs.getString("access_token", null)
        if (token != null) {
            _user.value = User(
                id = prefs.getString("user_id", "") ?: "",
                email = prefs.getString("email", "") ?: "",
                displayName = prefs.getString("display_name", "") ?: "",
                city = prefs.getString("city", null),
            )
        }
    }

    suspend fun login(email: String, password: String): Result<User> {
        return try {
            val requestBody = json.encodeToString(
                LoginRequest.serializer(),
                LoginRequest(email, password),
            )
            val response = apiCall("$baseUrl/api/v1/auth/login", requestBody)
            val authResponse = json.decodeFromString(AuthResponse.serializer(), response)

            saveSession(authResponse)
            Result.success(authResponse.user.toDomain())
        } catch (e: Exception) {
            Timber.e(e, "Login failed")
            Result.failure(e)
        }
    }

    suspend fun register(email: String, password: String, displayName: String): Result<User> {
        return try {
            val requestBody = json.encodeToString(
                RegisterRequest.serializer(),
                RegisterRequest(email, password, displayName),
            )
            val response = apiCall("$baseUrl/api/v1/auth/register", requestBody)
            val authResponse = json.decodeFromString(AuthResponse.serializer(), response)

            saveSession(authResponse)
            Result.success(authResponse.user.toDomain())
        } catch (e: Exception) {
            Timber.e(e, "Register failed")
            Result.failure(e)
        }
    }

    fun logout() {
        prefs.edit().clear().apply()
        _user.value = null
    }

    val isLoggedIn: Boolean get() = prefs.getString("access_token", null) != null

    val accessToken: String? get() = prefs.getString("access_token", null)

    private fun saveSession(auth: AuthResponse) {
        val user = auth.user
        prefs.edit()
            .putString("access_token", auth.accessToken)
            .putString("refresh_token", auth.refreshToken)
            .putString("user_id", user.id.toString())
            .putString("email", user.email)
            .putString("display_name", user.displayName)
            .apply()

        _user.value = User(
            id = user.id.toString(),
            email = user.email,
            displayName = user.displayName,
            avatarUrl = user.avatarUrl,
            bio = user.bio,
            city = user.city,
        )
    }

    private suspend fun apiCall(url: String, jsonBody: String): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errorMsg = if (bodyString.contains("email_taken")) {
                    "email_taken"
                } else if (bodyString.contains("invalid_credentials")) {
                    "invalid_credentials"
                } else {
                    "HTTP ${response.code}: $bodyString"
                }
                throw Exception(errorMsg)
            }
            return@use if (bodyString.isNotEmpty()) bodyString else throw Exception("Empty response body")
        }
    }
}

// DTOs matching backend (Dtos.kt in server)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val displayName: String,
)

@Serializable
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val user: UserDto,
)

@Serializable
data class UserDto(
    val id: Long,
    val email: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val city: String? = null,
) {
    fun toDomain() = User(
        id = id.toString(),
        email = email,
        displayName = displayName,
        avatarUrl = avatarUrl,
        bio = bio,
        city = city,
    )
}
