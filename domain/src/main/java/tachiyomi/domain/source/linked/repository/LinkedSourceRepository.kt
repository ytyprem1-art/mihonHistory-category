package tachiyomi.domain.source.linked.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.source.linked.model.LinkedSourceGroup

interface LinkedSourceRepository {
    fun getGroups(): Flow<List<LinkedSourceGroup>>
    suspend fun getGroupById(id: Long): LinkedSourceGroup?
    suspend fun insertGroup(name: String)
    suspend fun updateGroup(id: Long, name: String)
    suspend fun deleteGroup(id: Long)
}
