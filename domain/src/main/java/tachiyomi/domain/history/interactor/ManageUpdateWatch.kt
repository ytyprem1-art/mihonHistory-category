package tachiyomi.domain.history.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.history.model.UpdateWatch
import tachiyomi.domain.history.repository.UpdateWatchRepository

class ManageUpdateWatch(
    private val repository: UpdateWatchRepository,
) {
    fun subscribeAll(): Flow<List<UpdateWatch>> {
        return repository.subscribeAll()
    }

    suspend fun getAll(): List<UpdateWatch> {
        return repository.getAll()
    }

    suspend fun getById(mangaId: Long): UpdateWatch? {
        return repository.getById(mangaId)
    }

    suspend fun insert(updateWatch: UpdateWatch) {
        repository.insert(updateWatch)
    }

    suspend fun updatePaused(mangaId: Long, isPaused: Boolean) {
        val existing = repository.getById(mangaId)
        if (existing == null) {
            repository.insert(UpdateWatch(mangaId, isPaused))
        } else {
            repository.updatePaused(mangaId, isPaused)
        }
    }

    suspend fun delete(mangaId: Long) {
        repository.delete(mangaId)
    }

    suspend fun updateBackgroundRefresh(
        mangaId: Long,
        enabled: Boolean,
        interval: Int,
        profile: UpdateWatch.RefreshProfile
    ) {
        repository.updateBackgroundRefresh(mangaId, enabled, interval, profile)
    }

    suspend fun updateLastBackgroundCheckAt(mangaId: Long, timestamp: Long) {
        repository.updateLastBackgroundCheckAt(mangaId, timestamp)
    }

    suspend fun updateStaleMilestone(mangaId: Long, milestone: Int) {
        repository.updateStaleMilestone(mangaId, milestone)
    }

    suspend fun resetStaleMilestone(mangaId: Long) {
        repository.resetStaleMilestone(mangaId)
    }
}
