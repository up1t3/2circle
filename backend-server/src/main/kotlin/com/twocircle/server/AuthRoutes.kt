package com.twocircle.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.sql.Timestamp

/**
 * Authentication routes: /register, /login, /refresh.
 *
 * All return AuthResponse (access + refresh token + user DTO).
 * Passwords are bcrypt-hashed before storage; the plaintext never leaves the request.
 */
fun Route.authRoutes() {
    route("/api/v1/auth") {

        post("/register") {
            val req = call.receive<RegisterRequest>()

            // Validate input
            if (req.email.isBlank() || !req.email.contains("@")) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_email", "Valid email required"))
                return@post
            }
            if (req.password.length < 8) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("weak_password", "Password must be at least 8 characters"))
                return@post
            }
            if (req.displayName.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_name", "Display name required"))
                return@post
            }

            // Check if email already exists
            val existing = DatabaseFactory.dbQuery {
                Users.selectAll().where { Users.email eq req.email }.firstOrNull()
            }
            if (existing != null) {
                call.respond(HttpStatusCode.Conflict, ErrorResponse("email_taken", "Email already registered"))
                return@post
            }

            // Create user
            val now = java.time.Instant.now()
            val hash = PasswordHasher.hash(req.password)
            val userId = DatabaseFactory.dbQuery {
                Users.insert {
                    it[Users.email] = req.email
                    it[Users.passwordHash] = hash
                    it[Users.displayName] = req.displayName
                    it[Users.createdAt] = now
                    it[Users.updatedAt] = now
                } get Users.id
            }

            val accessToken = JwtService.issueAccessToken(userId.value, req.email)
            val refreshToken = JwtService.issueRefreshToken(userId.value)

            call.respond(
                AuthResponse(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresIn = JwtService.expiresInSeconds,
                    user = UserDto(
                        id = userId.value,
                        email = req.email,
                        displayName = req.displayName,
                    ),
                ),
            )
        }

        post("/login") {
            val req = call.receive<LoginRequest>()

            val row = DatabaseFactory.dbQuery {
                Users.selectAll().where { Users.email eq req.email }.firstOrNull()
            }
            if (row == null) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("invalid_credentials", "Wrong email or password"))
                return@post
            }

            val hash = row[Users.passwordHash]
            if (!PasswordHasher.verify(req.password, hash)) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("invalid_credentials", "Wrong email or password"))
                return@post
            }

            val userId = row[Users.id].value
            val email = row[Users.email]
            val accessToken = JwtService.issueAccessToken(userId, email)
            val refreshToken = JwtService.issueRefreshToken(userId)

            call.respond(
                AuthResponse(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresIn = JwtService.expiresInSeconds,
                    user = row.toUserDto(),
                ),
            )
        }

        post("/refresh") {
            val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")
                ?: run {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("missing_token", "Authorization header required"))
                    return@post
                }

            val userId = JwtService.verify(token)
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("invalid_token", "Token expired or invalid"))
                return@post
            }

            val row = DatabaseFactory.dbQuery {
                Users.selectAll().where { Users.id eq userId }.firstOrNull()
            }
            if (row == null) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("user_not_found", "User no longer exists"))
                return@post
            }

            val accessToken = JwtService.issueAccessToken(userId, row[Users.email])
            val refreshToken = JwtService.issueRefreshToken(userId)

            call.respond(
                AuthResponse(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresIn = JwtService.expiresInSeconds,
                    user = row.toUserDto(),
                ),
            )
        }
    }
}

private fun ResultRow.toUserDto() = UserDto(
    id = this[Users.id].value,
    email = this[Users.email],
    displayName = this[Users.displayName],
    avatarUrl = this[Users.avatarUrl],
    bio = this[Users.bio],
    city = this[Users.city],
)
