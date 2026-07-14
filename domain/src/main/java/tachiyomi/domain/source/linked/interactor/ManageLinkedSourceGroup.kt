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

    suspend fun create(name: String) {
        repository.insertGroup(name)
    }

    suspend fun rename(id: Long, name: String) {
        repository.updateGroup(id, name)
    }

    suspend fun delete(id: Long) {
        repository.deleteGroup(id)
    }
}
