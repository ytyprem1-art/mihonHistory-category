package tachiyomi.domain.history.interactor

import tachiyomi.domain.history.model.UpdateWatchInboxItem
import tachiyomi.domain.history.repository.UpdateWatchInboxRepository

class ManageUpdateWatchInbox(
    private val repository: UpdateWatchInboxRepository,
) {
    suspend fun insertOrMerge(item: UpdateWatchInboxItem) {
        val existing = repository.getByMangaId(item.mangaId)
        if (existing == null || item.type == UpdateWatchInboxItem.TYPE_INACTIVITY_WARNING) {
            // New item or it's a warning (warnings replace anything else for now,
            // though usually updates replace warnings)
            repository.insert(item)
        } else {
            // Incoming is a chapter update.
            if (existing.type == UpdateWatchInboxItem.TYPE_UPDATE) {
                val mergedChapterIds = (existing.chapterIds + item.chapterIds).distinct()
                val merged = existing.copy(
                    chapterCount = mergedChapterIds.size,
                    chapterRange = mergeRanges(existing.chapterRange, item.chapterRange),
                    lastFoundAt = item.lastFoundAt,
                    latestChapterId = item.latestChapterId,
                    latestChapterNumber = item.latestChapterNumber,
                    chapterIds = mergedChapterIds,
                    latestChapterUploadAt = item.latestChapterUploadAt,
                )
                repository.insert(merged)
            } else {
                // Existing was a warning, replace it with the new update
                repository.insert(item)
            }
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
        // Simple merge: "Ch. 1" + "Ch. 2" -> "Ch. 1, Ch. 2"
        return "$old, $new"
    }
}
