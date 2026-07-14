package tachiyomi.domain.source.linked.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.source.linked.model.LinkedSourceGroup

interface LinkedSourceRepository {
    fun getGroups(): Flow<List<LinkedSourceGroup>>
    suspend fun getGroupById(id: Long): LinkedSourceGroup?
    suspend fun insertGroup(name: String): Long
    suspend fun updateGroup(id: Long, name: String)
    suspend fun deleteGroup(id: Long)
    suspend fun getGroupIdForManga(mangaId: Long): Long?
    suspend fun insertMember(groupId: Long, mangaId: Long)
}
