package eu.kanade.tachiyomi.ui.mod.updatewatch

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.util.lang.toLocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.linked.interactor.ManageLinkedSourceGroup
import tachiyomi.domain.source.linked.model.LinkedSourceGroup
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class UpdateWatchScreenModel(
    private val manageUpdateWatch: ManageUpdateWatch = Injekt.get(),
    private val manageLinkedSourceGroup: ManageLinkedSourceGroup = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val chapterRepository: ChapterRepository = Injekt.get(),
) : StateScreenModel<UpdateWatchScreenModel.State>(State()) {

    init {
        screenModelScope.launchIO {
            manageUpdateWatch.subscribeAll()
                .flatMapLatest { trackedList ->
                    val activeTracking = trackedList.filter { !it.isPaused }
                    if (activeTracking.isEmpty()) return@flatMapLatest flowOf(emptyList<UpdateWatchUiModel>())

                    val flows = activeTracking.map { tracking ->
                        val mangaId = tracking.mangaId
                        combine(
                            getManga.subscribe(mangaId),
                            chapterRepository.getChapterByMangaIdAsFlow(mangaId),
                            manageLinkedSourceGroup.subscribeGroupForManga(mangaId, 0L), // sourceId doesn't matter for group lookup by mangaId in this interactor's implementation usually, let's check
                        ) { manga, chapters, group ->
                            if (manga == null || chapters.isEmpty()) return@combine null

                            val latestChapter = chapters.filter { it.dateUpload > 0 }
                                .maxByOrNull { it.dateUpload } ?: return@combine null

                            val today = LocalDate.now()
                            val releaseDate = latestChapter.dateUpload.toLocalDate()
                            val daysSinceRelease = ChronoUnit.DAYS.between(releaseDate, today)

                            if (daysSinceRelease >= 6) {
                                UpdateWatchUiModel.Item(
                                    group = group,
                                    trackingManga = manga,
                                    latestChapter = latestChapter,
                                    daysSinceRelease = daysSinceRelease
                                )
                            } else {
                                null
                            }
                        }
                    }
                    combine(flows) { it.filterNotNull() }.map { items ->
                        val upcoming = items.filter { it.daysSinceRelease == 6L }
                        val dueToday = items.filter { it.daysSinceRelease == 7L }
                        val overdue = items.filter { it.daysSinceRelease > 7L }

                        mutableListOf<UpdateWatchUiModel>().apply {
                            if (upcoming.isNotEmpty()) {
                                add(UpdateWatchUiModel.Header("Upcoming"))
                                addAll(upcoming)
                            }
                            if (dueToday.isNotEmpty()) {
                                add(UpdateWatchUiModel.Header("Due today"))
                                addAll(dueToday)
                            }
                            if (overdue.isNotEmpty()) {
                                add(UpdateWatchUiModel.Header("Overdue"))
                                addAll(overdue.sortedBy { it.daysSinceRelease })
                            }
                        }
                    }
                }
                .collectLatest { items ->
                    mutableState.update { it.copy(items = items) }
                }
        }
    }

    fun pauseTracking(mangaId: Long) {
        screenModelScope.launchIO {
            try {
                manageUpdateWatch.updatePaused(mangaId, true)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    @Immutable
    data class State(
        val items: List<UpdateWatchUiModel>? = null,
    )
}

sealed interface UpdateWatchUiModel {
    data class Header(val title: String) : UpdateWatchUiModel
    data class Item(
        val group: LinkedSourceGroup?,
        val trackingManga: Manga,
        val latestChapter: Chapter,
        val daysSinceRelease: Long,
    ) : UpdateWatchUiModel
}
