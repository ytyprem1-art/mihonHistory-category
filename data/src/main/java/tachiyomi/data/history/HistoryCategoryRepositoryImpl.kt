package tachiyomi.data.history

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers // 👈 Tambah import ini
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.domain.history.repository.HistoryCategory
import tachiyomi.domain.history.repository.HistoryCategoryRepository
import tachiyomi.data.Database

class HistoryCategoryRepositoryImpl(
    private val database: Database,
    // 👇 UBAH BARIS INI: Pakai Dispatchers.IO langsung
    private val context: kotlin.coroutines.CoroutineContext = Dispatchers.IO
) : HistoryCategoryRepository {
    // ... sisa kode di bawahnya tetap sama, jangan diubah ...

    override fun getHistoryCategories(): Flow<List<HistoryCategory>> {
        return database.historycategoriesQueries.getHistoryCategories()
            .asFlow()
            .mapToList(context)
            .map { list ->
                list.map { HistoryCategory(it._id, it.name) }
            }
    }

    override suspend fun insertHistoryCategory(name: String) {
        database.historycategoriesQueries.insertHistoryCategory(name)
    }

    override suspend fun deleteHistoryCategory(id: Long) {
        database.historycategoriesQueries.deleteHistoryCategory(id)
    }

    override suspend fun insertMangaMapping(mangaId: Long, categoryId: Long) {
        database.historycategoriesQueries.insertMangaMapping(mangaId, categoryId)
    }

    override suspend fun deleteMangaMapping(mangaId: Long) {
        database.historycategoriesQueries.deleteMangaMapping(mangaId)
    }

    override suspend fun getCategoryForManga(mangaId: Long): Long? {
        return database.historycategoriesQueries.getCategoryForManga(mangaId).executeAsOneOrNull()
    }
}
