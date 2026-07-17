package com.twocircle.bike.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A saved route plan: an ordered list of waypoints + the routing profile used to build it.
 *
 * The actual computed geometry lives in [SegmentEntity] rows (one per consecutive waypoint
 * pair) so a re-plan of a single segment doesn't touch the others. This is the unit of
 * "edit route without rebuilding it from scratch".
 *
 * [lastComputedAtMs] drives stale-detection: if the user edits waypoints we mark the plan
 * dirty and force a recompute before navigation.
 */
@Entity(
    tableName = "route_plans",
    indices = [Index("updatedAtMs")],
)
data class RoutePlanEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** Profile used for the most recent computation. */
    val profile: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    /** When the geometry was last (re)computed; null if never. */
    val lastComputedAtMs: Long? = null,
    val distanceMeters: Double = 0.0,
    val plannedSeconds: Long = 0L,
    val ascentMeters: Double = 0.0,
)

/**
 * One hop between consecutive waypoints in a route plan.
 *
 * Geometry stored as a normalised GeoJSON LineString string — compact for storage, and
 * MapLibre consumes GeoJSON directly when rendering. We avoid a per-point row here because
 * a single segment can hold thousands of points; a JSON blob is dramatically cheaper than
 * thousands of foreign-keyed rows for read-once-render-once access patterns.
 */
@Entity(
    tableName = "route_segments",
    foreignKeys = [
        ForeignKey(
            entity = RoutePlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["routePlanId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("routePlanId"),
        Index(value = ["routePlanId", "orderIdx"], unique = true),
    ],
)
data class SegmentEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0L,
    val routePlanId: String,
    /** 0-based position in the route. */
    val orderIdx: Int,
    val fromLat: Double,
    val fromLon: Double,
    val fromName: String?,
    val toLat: Double,
    val toLon: Double,
    val toName: String?,
    /** GeoJSON LineString coordinates: [[lon,lat,ele?],…]. */
    val geometryJson: String,
    val distanceMeters: Double,
    val plannedSeconds: Long,
    val ascentMeters: Double,
    val descentMeters: Double,
    val surface: String,
    val smoothness: String,
)
