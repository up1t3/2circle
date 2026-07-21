package com.twocircle.bike.data.repository

import android.content.Context
import com.twocircle.bike.common.outcome.Failure
import com.twocircle.bike.common.outcome.Outcome
import com.twocircle.bike.data.db.dao.RegionDao
import com.twocircle.bike.data.db.entity.RegionEntity
import com.twocircle.bike.data.db.entity.RegionInstallState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Region catalog + offline-coverage queries. Wraps RegionDao with typed [Outcome] so the
 * UI surfaces storage errors as [Failure.Storage] rather than crashing.
 *
 * **Multi-region support:** the active region is tracked via [activeRegionId]. When the
 * user has multiple installed regions, the map/search/routing modules use whichever
 * region is "active" — settable from the Regions screen. Defaults to the first installed
 * region if no explicit selection has been made.
 */
@Singleton
class RegionsRepository @Inject constructor(
    private val regionDao: RegionDao,
    @ApplicationContext private val context: Context,
) {

    private val prefs = context.getSharedPreferences("active_region", Context.MODE_PRIVATE)

    /** The user-selected active region ID, or null (→ auto-pick first installed). */
    private val _activeRegionId = MutableStateFlow(prefs.getString("region_id", null))
    val activeRegionId: StateFlow<String?> = _activeRegionId.asStateFlow()

    fun allFlow(): Flow<List<RegionEntity>> = regionDao.all()

    /**
     * Set the active region by ID. Persists across app restarts via SharedPreferences.
     */
    fun setActiveRegion(id: String) {
        prefs.edit().putString("region_id", id).apply()
        _activeRegionId.value = id
    }

    /**
     * Returns the currently active region, or the first installed one if no explicit
     * selection exists. This is the single source of truth for "which region does the
     * map/search/routing use right now?"
     */
    suspend fun activeRegionOrNull(): RegionEntity? {
        val explicit = _activeRegionId.value
        if (explicit != null) {
            val region = byId(explicit).let { (it as? Outcome.Success)?.value }
            if (region != null && region.installState == RegionInstallState.Installed) {
                return region
            }
        }
        // Fallback: first installed region.
        return firstInstalledOrNull()
    }

    /**
     * One-shot snapshot of the first installed region, or null if none.
     */
    suspend fun firstInstalledOrNull(): RegionEntity? =
        allFlow().firstOrNull()?.firstOrNull { it.installState == RegionInstallState.Installed }

    suspend fun byId(id: String): Outcome<RegionEntity?> = runCatching {
        regionDao.byId(id)
    }.fold(
        onSuccess = { Outcome.Success(it) },
        onFailure = { Outcome.Failure(Failure.Storage.Inaccessible(it as IOException)) },
    )

    suspend fun setState(id: String, state: RegionInstallState): Outcome<Unit> = runCatching {
        val installedAt = if (state == RegionInstallState.Installed) System.currentTimeMillis() else null
        regionDao.setState(id, state, installedAt)
    }.fold(
        onSuccess = { Outcome.Success(Unit) },
        onFailure = { Outcome.Failure(Failure.Storage.Inaccessible(it as IOException)) },
    )

    suspend fun upsert(region: RegionEntity): Outcome<Unit> = runCatching {
        regionDao.upsert(region)
    }.fold(
        onSuccess = { Outcome.Success(Unit) },
        onFailure = { Outcome.Failure(Failure.Storage.Inaccessible(it as IOException)) },
    )

    suspend fun delete(id: String): Outcome<Unit> = runCatching {
        regionDao.delete(id)
    }.fold(
        onSuccess = { Outcome.Success(Unit) },
        onFailure = { Outcome.Failure(Failure.Storage.Inaccessible(it as IOException)) },
    )

    /** null = no region covers this coord; else the regionId to use offline. */
    suspend fun regionContaining(lat: Double, lon: Double): Outcome<String?> = runCatching {
        regionDao.installedRegionContaining(lat, lon)?.id
    }.fold(
        onSuccess = { Outcome.Success(it) },
        onFailure = { Outcome.Failure(Failure.Storage.Inaccessible(it as IOException)) },
    )
}
