package tachiyomi.domain.history.interactor

import tachiyomi.domain.history.model.UpdateWatchHistory
import tachiyomi.domain.history.repository.UpdateWatchHistoryRepository

class ManageUpdateWatchHistory(
    private val repository: UpdateWatchHistoryRepository,
) {
    suspend fun insert(history: UpdateWatchHistory) {
        repository.insert(history)
    }
}
