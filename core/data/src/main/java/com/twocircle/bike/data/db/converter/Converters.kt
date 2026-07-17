package com.twocircle.bike.data.db.converter

import androidx.room.TypeConverter
import com.twocircle.bike.data.db.entity.RegionInstallState
import com.twocircle.bike.data.db.entity.TrackSource
import com.twocircle.bike.data.db.entity.TrackStatus

/**
 * Room type converters for our enums.
 *
 * Stored by name (not ordinal) — ordinal values are fragile across schema evolution
 * (inserting a new enum constant shifts ordinals and silently corrupts stored data).
 * Name-based storage survives reordering and is debuggable in a SQLite browser.
 */
class Converters {

    @TypeConverter fun trackStatusToString(s: TrackStatus): String = s.name
    @TypeConverter fun stringToTrackStatus(s: String): TrackStatus =
        runCatching { TrackStatus.valueOf(s) }.getOrElse {
            // Unknown enum value from a future schema → treat as Interrupted so the rider
            // is offered recovery rather than losing the track.
            TrackStatus.Interrupted
        }

    @TypeConverter fun trackSourceToString(s: TrackSource): String = s.name
    @TypeConverter fun stringToTrackSource(s: String): TrackSource =
        runCatching { TrackSource.valueOf(s) }.getOrDefault(TrackSource.Live)

    @TypeConverter fun regionStateToString(s: RegionInstallState): String = s.name
    @TypeConverter fun stringToRegionState(s: String): RegionInstallState =
        runCatching { RegionInstallState.valueOf(s) }.getOrDefault(RegionInstallState.NotInstalled)
}
