package tachiyomi.domain.source.linked.model

data class LinkedSourceMember(
    val id: Long,
    val groupId: Long,
    val mangaId: Long,
    val mangaTitle: String,
    val sourceId: Long,
)
