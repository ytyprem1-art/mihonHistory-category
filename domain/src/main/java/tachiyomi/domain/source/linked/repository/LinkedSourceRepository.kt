package tachiyomi.domain.source.linked.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.source.linked.model.LinkedSourceGroup

interface LinkedSourceRepository {
    fun getGroups(): Flow<List<LinkedSourceGroup>>
    suspend fun getGroupById(id: Long): LinkedSourceGroup?
    fun getMembersByGroupId(groupId: Long): Flow<List<Long>>
    suspend fun insertGroup(name: String): Long
    suspend fun updateGroup(id: Long, name: String)
    suspend fun deleteGroup(id: Long)
    fun getGroupForManga(mangaId: Long, sourceId: Long): Flow<LinkedSourceGroup?>
    suspend fun getGroupIdForManga(mangaId: Long, sourceId: Long): Long?
    suspend fun insertMember(groupId: Long, mangaId: Long)
    suspend fun removeMangaFromGroups(mangaId: Long, sourceId: Long)
    suspend fun removeMember(groupId: Long, mangaId: Long, sourceId: Long)
    suspend fun createAndLink(name: String, mangaId: Long)
}
