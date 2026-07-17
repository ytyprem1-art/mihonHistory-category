package tachiyomi.data.history

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.history.model.UpdateWatch
import tachiyomi.domain.history.repository.UpdateWatchRepository

class UpdateWatchRepositoryImpl(
    private val database: Database,
) : UpdateWatchRepository {

    override fun subscribeAll(): Flow<List<UpdateWatch>> {
        return database.update_watchQueries.getTrackedManga { mangaId, isPaused, enabled, interval ->
            UpdateWatch(mangaId, isPaused == 1L, enabled == 1L, interval.toInt())
        }.subscribeToList()
    }

    override suspend fun getAll(): List<UpdateWatch> {
        return database.update_watchQueries.getTrackedManga { mangaId, isPaused, enabled, interval ->
            UpdateWatch(mangaId, isPaused == 1L, enabled == 1L, interval.toInt())
        }.awaitAsList()
    }

    override suspend fun getById(mangaId: Long): UpdateWatch? {
        return database.update_watchQueries.getTrackingState(mangaId) { mId, isPaused, enabled, interval ->
            UpdateWatch(mId, isPaused == 1L, enabled == 1L, interval.toInt())
        }.awaitAsOneOrNull()
    }

    override suspend fun insert(updateWatch: UpdateWatch) {
        database.transaction {
            database.update_watchQueries.insert(
                updateWatch.mangaId,
                if (updateWatch.isPaused) 1L else 0L,
                if (updateWatch.backgroundRefreshEnabled) 1L else 0L,
                updateWatch.expectedIntervalDays.toLong()
            )
        }
    }

    override suspend fun delete(mangaId: Long) {
        database.transaction {
            database.update_watchQueries.delete(mangaId)
        }
    }

    override suspend fun updatePaused(mangaId: Long, isPaused: Boolean) {
        database.transaction {
            database.update_watchQueries.updatePaused(if (isPaused) 1L else 0L, mangaId)
        }
    }

    override suspend fun updateBackgroundRefresh(mangaId: Long, enabled: Boolean, interval: Int) {
        database.transaction {
            database.update_watchQueries.updateBackgroundRefresh(
                if (enabled) 1L else 0L,
                interval.toLong(),
                mangaId
            )
        }
    }
}
