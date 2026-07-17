package tachiyomi.domain.history.interactor

import tachiyomi.domain.history.model.UpdateWatchInboxItem
import tachiyomi.domain.history.repository.UpdateWatchInboxRepository

class ManageUpdateWatchInbox(
    private val repository: UpdateWatchInboxRepository,
) {
    suspend fun insertOrMerge(item: UpdateWatchInboxItem) {
        val existing = repository.getByMangaId(item.mangaId)
        if (existing == null) {
            repository.insert(item)
        } else {
            val merged = existing.copy(
                chapterCount = existing.chapterCount + item.chapterCount,
                chapterRange = mergeRanges(existing.chapterRange, item.chapterRange),
                lastFoundAt = item.lastFoundAt,
                latestChapterId = item.latestChapterId,
                latestChapterNumber = item.latestChapterNumber,
            )
            repository.insert(merged)
        }
    }

    suspend fun delete(mangaId: Long) {
        repository.delete(mangaId)
    }

    suspend fun deleteAll() {
        repository.deleteAll()
    }

    private fun mergeRanges(old: String, new: String): String {
        if (old == new) return old
        // Simple merge: "Ch. 1" + "Ch. 2" -> "Ch. 1 - 2"
        // For now just keep it simple or use a better representation if needed.
        // The requirement is "detected chapter count/range".
        return "$old, $new"
    }
}
