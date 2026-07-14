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

    suspend fun joinGroup(groupId: Long, mangaId: Long) {
        val currentGroupId = repository.getGroupIdForManga(mangaId)
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

    suspend fun getGroupForManga(mangaId: Long): LinkedSourceGroup? {
        val groupId = repository.getGroupIdForManga(mangaId) ?: return null
        return repository.getGroupById(groupId)
    }
}
