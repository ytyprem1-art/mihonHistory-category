package tachiyomi.domain.history.model

data class UpdateWatchInboxItem(
    val mangaId: Long,
    val mangaTitle: String,
    val sourceId: Long,
    val sourceName: String,
    val chapterCount: Int,
    val chapterRange: String,
    val firstFoundAt: Long,
    val lastFoundAt: Long,
    val latestChapterId: Long,
    val latestChapterNumber: Double,
    val latestChapterUploadAt: Long = 0L,
)
