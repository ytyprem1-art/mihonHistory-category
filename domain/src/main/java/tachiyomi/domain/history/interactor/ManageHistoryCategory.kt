package tachiyomi.domain.history.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.history.repository.HistoryCategory
import tachiyomi.domain.history.repository.HistoryCategoryRepository

class ManageHistoryCategory(
    private val repository: HistoryCategoryRepository
) {
    fun subscribe(): Flow<List<HistoryCategory>> {
        return repository.getHistoryCategories()
    }

    suspend fun create(name: String) {
        repository.insertHistoryCategory(name)
    }

    suspend fun rename(id: Long, name: String) {
        repository.updateHistoryCategory(id, name)
    }

    suspend fun delete(id: Long) {
        repository.deleteHistoryCategory(id)
    }

    suspend fun moveToCategory(mangaId: Long, categoryId: Long) {
        repository.deleteMangaMapping(mangaId)
        if (categoryId != 0L) {
            repository.insertMangaMapping(mangaId, categoryId)
        }
    }

    suspend fun getMangaCategory(mangaId: Long): Long? {
        return repository.getCategoryForManga(mangaId)
    }
}
