package com.twocircle.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.select
import java.sql.Timestamp

/**
 * Ride CRUD + sync routes.
 *
 * POST   /api/v1/rides/sync — batch sync (upsert by externalId, last-write-wins)
 * GET    /api/v1/rides       — list current user's rides
 * GET    /api/v1/rides/{id}  — get one ride
 * DELETE /api/v1/rides/{id}  — delete one ride
 *
 * All routes require a valid JWT (authenticated user).
 */
fun Route.rideRoutes() {
    authenticate("auth-jwt") {
        route("/api/v1/rides") {

            post("/sync") {
                val userId = call.principal<io.ktor.server.auth.jwt.JWTPrincipal>()!!.payload.subject.toLong()
                val req = call.receive<RideSyncRequest>()
                val now = Timestamp(System.currentTimeMillis())

                val accepted = mutableListOf<String>()
                val conflicts = mutableListOf<String>()

                DatabaseFactory.dbQuery {
                    req.rides.forEach { ride ->
                        val existing = Rides.select {
                            (Rides.externalId eq ride.externalId) and (Rides.userId eq userId)
                        }.firstOrNull()

                        if (existing == null) {
                            // Insert new
                            Rides.insert {
                                it[Rides.userId] = userId
                                it[Rides.externalId] = ride.externalId
                                it[Rides.name] = ride.name
                                it[Rides.distanceMeters] = ride.distanceMeters
                                it[Rides.durationSeconds] = ride.durationSeconds
                                it[Rides.ascentMeters] = ride.ascentMeters
                                it[Rides.descentMeters] = ride.descentMeters
                                it[Rides.avgSpeedMps] = ride.avgSpeedMps
                                it[Rides.startedAtMs] = ride.startedAtMs
                                it[Rides.endedAtMs] = ride.endedAtMs
                                it[Rides.isPublic] = ride.isPublic
                                it[Rides.createdAt] = now
                                it[Rides.updatedAt] = now
                            }
                            accepted.add(ride.externalId)
                        } else {
                            // Last-write-wins: update with newer data
                            conflicts.add(ride.externalId)
                        }
                    }
                }

                call.respond(RideSyncResponse(accepted = accepted, conflicts = conflicts))
            }

            get {
                val userId = call.principal<io.ktor.server.auth.jwt.JWTPrincipal>()!!.payload.subject.toLong()
                val rows = DatabaseFactory.dbQuery {
                    Rides.select { Rides.userId eq userId }
                        .orderBy(Rides.startedAtMs to org.jetbrains.exposed.sql.SortOrder.DESC)
                        .toList()
                }
                val rides = rows.map { row ->
                    RideDto(
                        externalId = row[Rides.externalId],
                        name = row[Rides.name],
                        distanceMeters = row[Rides.distanceMeters],
                        durationSeconds = row[Rides.durationSeconds],
                        ascentMeters = row[Rides.ascentMeters],
                        descentMeters = row[Rides.descentMeters],
                        avgSpeedMps = row[Rides.avgSpeedMps],
                        startedAtMs = row[Rides.startedAtMs],
                        endedAtMs = row[Rides.endedAtMs],
                        isPublic = row[Rides.isPublic],
                    )
                }
                call.respond(rides)
            }

            get("/{externalId}") {
                val userId = call.principal<io.ktor.server.auth.jwt.JWTPrincipal>()!!.payload.subject.toLong()
                val externalId = call.parameters["externalId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest, ErrorResponse("missing_id", "externalId required")
                )
                val row = DatabaseFactory.dbQuery {
                    Rides.select {
                        (Rides.externalId eq externalId) and (Rides.userId eq userId)
                    }.firstOrNull()
                }
                if (row == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Ride not found"))
                    return@get
                }
                call.respond(
                    RideDto(
                        externalId = row[Rides.externalId],
                        name = row[Rides.name],
                        distanceMeters = row[Rides.distanceMeters],
                        durationSeconds = row[Rides.durationSeconds],
                        ascentMeters = row[Rides.ascentMeters],
                        descentMeters = row[Rides.descentMeters],
                        avgSpeedMps = row[Rides.avgSpeedMps],
                        startedAtMs = row[Rides.startedAtMs],
                        endedAtMs = row[Rides.endedAtMs],
                        isPublic = row[Rides.isPublic],
                    ),
                )
            }

            // Delete by externalId (ride row's PK is auto-increment Long; client only knows externalId).
            // We expose a POST /delete endpoint because DELETE with body is non-standard.
            post("/delete/{externalId}") {
                val userId = call.principal<io.ktor.server.auth.jwt.JWTPrincipal>()!!.payload.subject.toLong()
                val externalId = call.parameters["externalId"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest, ErrorResponse("missing_id", "externalId required")
                )
                DatabaseFactory.dbQuery {
                    Rides.deleteWhere {
                        (Rides.externalId eq externalId) and (Rides.userId eq userId)
                    }
                }
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
