package eu.kanade.tachiyomi.ui.mod.updatewatch

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.util.lang.toLocalDate
import androidx.compose.runtime.getValue
import eu.kanade.core.preference.asState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import logcat.LogPriority
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.history.interactor.ManageUpdateWatch
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.linked.interactor.ManageLinkedSourceGroup
import tachiyomi.domain.source.linked.model.LinkedSourceGroup
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class UpdateWatchManagerScreenModel(
    private val manageUpdateWatch: ManageUpdateWatch = Injekt.get(),
    private val manageLinkedSourceGroup: ManageLinkedSourceGroup = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val chapterRepository: ChapterRepository = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
) : StateScreenModel<UpdateWatchManagerScreenModel.State>(State()) {

    val sortMode by libraryPreferences.trackedMangaSort.asState(screenModelScope)

    init {
        screenModelScope.launchIO {
            val itemsFlow = manageUpdateWatch.subscribeAll()
                .flatMapLatest { trackedList ->
                    if (trackedList.isEmpty()) return@flatMapLatest flowOf(emptyList<UpdateWatchUiModel.Item>())

                    val flows = trackedList.map { tracking ->
                        val mangaId = tracking.mangaId
                        combine(
                            getManga.subscribe(mangaId),
                            chapterRepository.getChapterByMangaIdAsFlow(mangaId),
                            manageLinkedSourceGroup.subscribeGroupForManga(mangaId, 0L),
                        ) { manga, chapters, group ->
                            if (manga == null) return@combine null

                            val latestChapter = if (chapters.isNotEmpty()) {
                                chapters.filter { it.dateUpload > 0 }
                                    .maxByOrNull { it.dateUpload }
                            } else {
                                null
                            }

                            val daysSinceRelease = latestChapter?.let {
                                val today = LocalDate.now()
                                val releaseDate = it.dateUpload.toLocalDate()
                                ChronoUnit.DAYS.between(releaseDate, today)
                            } ?: -1L

                            UpdateWatchUiModel.Item(
                                group = group,
                                trackingManga = manga,
                                latestChapter = latestChapter ?: Chapter.create().copy(chapterNumber = -1.0),
                                daysSinceRelease = daysSinceRelease
                            )
                        }
                    }
                    combine(flows) { it.filterNotNull() }
                }

            combine(
                itemsFlow,
                state.map { it.searchQuery }.distinctUntilChanged(),
                libraryPreferences.trackedMangaSort.changes(),
            ) { items, query, sort ->
                val sortedItems = when (sort) {
                    LibraryPreferences.TrackedMangaSort.NewestRelease -> items.sortedBy { it.daysSinceRelease }
                    LibraryPreferences.TrackedMangaSort.OldestRelease -> items.sortedByDescending { it.daysSinceRelease }
                    LibraryPreferences.TrackedMangaSort.TitleAZ -> items.sortedBy { it.trackingManga.title.lowercase() }
                    LibraryPreferences.TrackedMangaSort.TitleZA -> items.sortedByDescending { it.trackingManga.title.lowercase() }
                }

                if (query.isNullOrBlank()) return@combine sortedItems

                sortedItems.filter { item ->
                    val sourceName = sourceManager.getOrStub(item.trackingManga.source).name
                    item.trackingManga.title.contains(query, ignoreCase = true) ||
                        sourceName.contains(query, ignoreCase = true)
                }
            }.collectLatest { filteredItems ->
                mutableState.update { it.copy(items = filteredItems) }
            }
        }
    }

    fun updateSearchQuery(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun setSortMode(mode: LibraryPreferences.TrackedMangaSort) {
        libraryPreferences.trackedMangaSort.set(mode)
    }

    fun untrack(mangaId: Long) {
        screenModelScope.launchIO {
            try {
                manageUpdateWatch.delete(mangaId)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    @Immutable
    data class State(
        val searchQuery: String? = null,
        val items: List<UpdateWatchUiModel.Item>? = null,
    )
}
