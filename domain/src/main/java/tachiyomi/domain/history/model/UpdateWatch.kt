package tachiyomi.domain.history.model

data class UpdateWatch(
    val mangaId: Long,
    val isPaused: Boolean,
)
