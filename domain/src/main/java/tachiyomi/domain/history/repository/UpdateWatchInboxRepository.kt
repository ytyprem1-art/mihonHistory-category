package tachiyomi.domain.history.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.history.model.UpdateWatchInboxItem

interface UpdateWatchInboxRepository {
    fun subscribeAll(): Flow<List<UpdateWatchInboxItem>>
    suspend fun getAll(): List<UpdateWatchInboxItem>
    suspend fun getByMangaId(mangaId: Long): UpdateWatchInboxItem?
    suspend fun insert(item: UpdateWatchInboxItem)
    suspend fun delete(mangaId: Long)
    suspend fun deleteAll()
}
