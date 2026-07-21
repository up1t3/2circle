package com.twocircle.bike.feature.auth.data

/**
 * User domain entity for auth state.
 */
data class User(
    val id: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val city: String? = null,
)
