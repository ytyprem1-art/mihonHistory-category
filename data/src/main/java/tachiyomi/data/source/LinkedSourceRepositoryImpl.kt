package tachiyomi.data.source

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.source.linked.model.LinkedSourceGroup
import tachiyomi.domain.source.linked.repository.LinkedSourceRepository

class LinkedSourceRepositoryImpl(
    private val database: Database,
) : LinkedSourceRepository {

    override fun getGroups(): Flow<List<LinkedSourceGroup>> {
        return database.linked_sourcesQueries.getGroups { _id, name, memberCount ->
            LinkedSourceGroup(_id, name, memberCount)
        }.subscribeToList()
    }

    override suspend fun getGroupById(id: Long): LinkedSourceGroup? {
        return database.linked_sourcesQueries.getGroupById(id) { _id, name ->
            LinkedSourceGroup(_id, name, 0L) // memberCount not used here usually
        }.awaitAsOneOrNull()
    }

    override suspend fun insertGroup(name: String) {
        database.linked_sourcesQueries.insertGroup(name)
    }

    override suspend fun updateGroup(id: Long, name: String) {
        database.linked_sourcesQueries.updateGroup(name, id)
    }

    override suspend fun deleteGroup(id: Long) {
        database.linked_sourcesQueries.deleteGroup(id)
    }
}
