package com.twocircle.bike.data.repository

import com.twocircle.bike.common.outcome.Failure
import com.twocircle.bike.common.outcome.Outcome
import com.twocircle.bike.data.db.dao.RegionDao
import com.twocircle.bike.data.db.entity.RegionEntity
import com.twocircle.bike.data.db.entity.RegionInstallState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Region catalog + offline-coverage queries. Wraps RegionDao with typed [Outcome] so the
 * UI surfaces storage errors as [Failure.Storage] rather than crashing.
 */
@Singleton
class RegionsRepository @Inject constructor(
    private val regionDao: RegionDao,
) {

    fun allFlow(): Flow<List<RegionEntity>> = regionDao.all()

    /**
     * One-shot snapshot of the first installed region, or null if none.
     *
     * Common pattern across feature modules ("which region is active right now?").
     * Centralised here so the extension-form `firstOrNull()` call lives in one place —
     * the function-form `kotlinx.coroutines.flow.firstOrNull(flow)` is a frequent typo
     * that the compiler accepts syntactically but resolves incorrectly.
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
