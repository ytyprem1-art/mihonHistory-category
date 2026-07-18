package tachiyomi.domain.history.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.history.model.UpdateWatchHistory

interface UpdateWatchHistoryRepository {
    suspend fun insert(history: UpdateWatchHistory)
    suspend fun getLatestByMangaId(mangaId: Long): UpdateWatchHistory?
    suspend fun getAll(): List<UpdateWatchHistory>
    fun subscribeLatestByMangaId(mangaId: Long): Flow<UpdateWatchHistory?>
    fun subscribeLatest5ByMangaId(mangaId: Long): Flow<List<UpdateWatchHistory>>
    fun subscribeLatestByMangaIds(mangaIds: List<Long>): Flow<List<UpdateWatchHistory>>
}
