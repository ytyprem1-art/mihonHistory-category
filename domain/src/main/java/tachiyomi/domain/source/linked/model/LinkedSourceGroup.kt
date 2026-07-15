package tachiyomi.domain.source.linked.model

data class LinkedSourceGroup(
    val id: Long,
    val name: String,
    val memberCount: Long,
    val trackingMangaId: Long? = null,
)
