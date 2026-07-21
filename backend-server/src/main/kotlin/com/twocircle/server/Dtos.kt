package com.twocircle.server

import kotlinx.serialization.Serializable

/**
 * API request/response DTOs (kotlinx.serialization).
 * These are the wire types exchanged between the Android client and this backend.
 */

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val displayName: String,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
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
)

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String,
)

// Ride DTOs

@Serializable
data class RideDto(
    val externalId: String,
    val name: String,
    val distanceMeters: Double,
    val durationSeconds: Long,
    val ascentMeters: Double,
    val descentMeters: Double,
    val avgSpeedMps: Double,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val isPublic: Boolean = false,
)

@Serializable
data class RideSyncRequest(
    val rides: List<RideDto>,
)

@Serializable
data class RideSyncResponse(
    val accepted: List<String>, // external IDs successfully synced
    val conflicts: List<String>, // external IDs that had conflicts (last-write-wins applied)
)
