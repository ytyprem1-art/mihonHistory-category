package tachiyomi.domain.history.group.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.history.group.model.HistoryGroup

interface HistoryGroupRepository {
    fun subscribe(): Flow<List<HistoryGroup>>
    fun subscribeMembers(groupId: Long): Flow<List<Long>>
    suspend fun getGroups(): List<HistoryGroup>
    suspend fun getGroupById(id: Long): HistoryGroup?
    suspend fun getGroupForManga(mangaId: Long): HistoryGroup?
    fun subscribeGroupForManga(mangaId: Long): Flow<HistoryGroup?>
    suspend fun insertGroup(name: String): Long
    suspend fun updateGroup(id: Long, name: String)
    suspend fun deleteGroup(id: Long)
    suspend fun assignMangaToGroup(mangaId: Long, groupId: Long)
    suspend fun createGroupWithMembers(name: String, mangaIds: List<Long>)
    suspend fun removeMangaFromGroup(mangaId: Long)
}
