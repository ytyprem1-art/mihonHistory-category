package tachiyomi.domain.history.model

data class UpdateWatch(
    val mangaId: Long,
    val isPaused: Boolean,
    val backgroundRefreshEnabled: Boolean = false,
    val expectedIntervalDays: Int = 7,
)
