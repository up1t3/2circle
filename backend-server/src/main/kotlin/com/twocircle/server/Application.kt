package com.twocircle.server

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

private val jwtSecret = System.getenv("JWT_SECRET") ?: "twocircle-dev-secret-change-in-production"
private val jwtAlgorithm = Algorithm.HMAC256(jwtSecret)

fun main() {
    embeddedServer(CIO, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(CallLogging) { level = Level.INFO }
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            prettyPrint = false
            encodeDefaults = true
        })
    }
    install(CORS) {
        anyHost()
        allowCredentials = true
        allowHeader("Authorization")
    }

    // JWT authentication — verifies access tokens on protected routes.
    authentication {
        jwt("auth-jwt") {
            verifier(
                JWT.require(jwtAlgorithm).build()
            )
            validate { credential ->
                if (credential.payload.subject?.toLongOrNull() != null) JWTPrincipal(credential.payload) else null
            }
        }
    }

    DatabaseFactory.init()

    routing {
        authRoutes()
        rideRoutes()
    }
}
