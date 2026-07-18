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
    val chapterIds: List<Long>,
    val latestChapterUploadAt: Long = 0L,
    val type: Int = TYPE_UPDATE,
    val milestone: Int = 0,
) {
    companion object {
        const val TYPE_UPDATE = 0
        const val TYPE_INACTIVITY_WARNING = 1
    }
}
