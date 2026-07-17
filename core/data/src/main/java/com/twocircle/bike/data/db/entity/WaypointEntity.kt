package com.twocircle.bike.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-defined point in a route plan: start, via, or end.
 *
 * Detached from [SegmentEntity] on purpose: segments are derived from waypoints, and
 * re-ordering a waypoint should cascade to segment recomputation, not to point edits.
 * [orderIdx] is the source of truth for route ordering; drag-and-drop renumbers this.
 *
 * [source] records how the waypoint was added (manual tap / search / GPS / GPX import),
 * which the UI uses to render matching affordances (POI icon for search, pin for manual).
 */
@Entity(
    tableName = "waypoints",
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
data class WaypointEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0L,
    val routePlanId: String,
    val orderIdx: Int,
    val lat: Double,
    val lon: Double,
    val name: String?,
    /** "Start", "Via", "End". */
    val role: String,
    /** "Manual", "Search", "Gps", "GpxImport". */
    val source: String,
)
