package com.twocircle.server

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.mindrot.jbcrypt.BCrypt
import java.util.Date

/**
 * JWT token issuance + verification + password hashing.
 *
 * Access tokens: short-lived (1 hour), carry userId + email.
 * Refresh tokens: long-lived (30 days), only carry userId.
 */

private const val ACCESS_TOKEN_TTL_MS = 3600_000L * 1L // 1 hour
private const val REFRESH_TOKEN_TTL_MS = 86_400_000L * 30L // 30 days

// Secret key — in production use env var, not hardcoded.
private val jwtSecret = System.getenv("JWT_SECRET") ?: "twocircle-dev-secret-change-in-production"
private val algorithm = Algorithm.HMAC256(jwtSecret)
private val verifier = JWT.require(algorithm).build()

object JwtService {

    fun issueAccessToken(userId: Long, email: String): String =
        JWT.create()
            .withSubject(userId.toString())
            .withClaim("email", email)
            .withClaim("type", "access")
            .withIssuedAt(Date())
            .withExpiresAt(Date(System.currentTimeMillis() + ACCESS_TOKEN_TTL_MS))
            .sign(algorithm)

    fun issueRefreshToken(userId: Long): String =
        JWT.create()
            .withSubject(userId.toString())
            .withClaim("type", "refresh")
            .withIssuedAt(Date())
            .withExpiresAt(Date(System.currentTimeMillis() + REFRESH_TOKEN_TTL_MS))
            .sign(algorithm)

    fun verify(token: String): Long? = try {
        val decoded = verifier.verify(token)
        decoded.subject.toLongOrNull()
    } catch (e: Exception) {
        null
    }

    val expiresInSeconds: Long = ACCESS_TOKEN_TTL_MS / 1000
}

object PasswordHasher {
    fun hash(password: String): String = BCrypt.hashpw(password, BCrypt.gensalt(12))
    fun verify(password: String, hash: String): Boolean = BCrypt.checkpw(password, hash)
}
