package tachiyomi.data.source

import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.coroutines.flow.Flow
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
            LinkedSourceGroup(_id, name, 0L)
        }.awaitAsOneOrNull()
    }

    override suspend fun insertGroup(name: String): Long {
        return database.linked_sourcesQueries.insertGroup(name).awaitAsOne()
    }

    override suspend fun updateGroup(id: Long, name: String) {
        database.linked_sourcesQueries.updateGroup(name, id)
    }

    override suspend fun deleteGroup(id: Long) {
        database.linked_sourcesQueries.deleteGroup(id)
    }

    override suspend fun getGroupIdForManga(mangaId: Long): Long? {
        return database.linked_sourcesQueries.getGroupIdForManga(mangaId).awaitAsOneOrNull()
    }

    override suspend fun insertMember(groupId: Long, mangaId: Long) {
        database.linked_sourcesQueries.insertMember(groupId, mangaId)
    }
}
