package eu.kanade.tachiyomi.ui.mod.updatewatch.worker

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Transient in-memory state for manga currently being processed or queued
 * by the active background worker.
 */
object UpdateWatchRefreshState {
    private val _queuedMangaIds = MutableStateFlow<Set<Long>>(emptySet())
    val queuedMangaIds: StateFlow<Set<Long>> = _queuedMangaIds.asStateFlow()

    fun setQueued(ids: Set<Long>) {
        _queuedMangaIds.value = ids
    }

    fun clear() {
        _queuedMangaIds.value = emptySet()
    }
}
