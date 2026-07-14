package tachiyomi.domain.source.linked.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.source.linked.model.LinkedSourceGroup
import tachiyomi.domain.source.linked.repository.LinkedSourceRepository

class ManageLinkedSourceGroup(
    private val repository: LinkedSourceRepository,
) {
    fun subscribe(): Flow<List<LinkedSourceGroup>> {
        return repository.getGroups()
    }

    suspend fun create(name: String): Long {
        return repository.insertGroup(name)
    }

    suspend fun createAndLink(name: String, mangaId: Long) {
        repository.createAndLink(name, mangaId)
    }

    suspend fun joinGroup(groupId: Long, mangaId: Long, sourceId: Long) {
        val currentGroupId = repository.getGroupIdForManga(mangaId, sourceId)
        if (currentGroupId != groupId) {
            repository.insertMember(groupId, mangaId)
        }
    }

    suspend fun rename(id: Long, name: String) {
        repository.updateGroup(id, name)
    }

    suspend fun delete(id: Long) {
        repository.deleteGroup(id)
    }

    fun subscribeGroupForManga(mangaId: Long, sourceId: Long): Flow<LinkedSourceGroup?> {
        return repository.getGroupForManga(mangaId, sourceId)
    }

    suspend fun getGroupForManga(mangaId: Long, sourceId: Long): LinkedSourceGroup? {
        val groupId = repository.getGroupIdForManga(mangaId, sourceId) ?: return null
        return repository.getGroupById(groupId)
    }
}
