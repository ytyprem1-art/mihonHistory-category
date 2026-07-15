package eu.kanade.tachiyomi.ui.mod.updatewatch

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.ui.manga.LinkedMember
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import logcat.LogPriority
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.linked.interactor.ManageLinkedSourceGroup
import tachiyomi.domain.source.linked.model.LinkedSourceGroup
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class UpdateWatchScreenModel(
    private val manageLinkedSourceGroup: ManageLinkedSourceGroup = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val chapterRepository: ChapterRepository = Injekt.get(),
) : StateScreenModel<UpdateWatchScreenModel.State>(State()) {

    init {
        screenModelScope.launchIO {
            manageLinkedSourceGroup.subscribe()
                .flatMapLatest { groups ->
                    val trackedGroups = groups.filter { it.trackingMangaId != null && !it.isPaused }
                    if (trackedGroups.isEmpty()) return@flatMapLatest kotlinx.coroutines.flow.flowOf(emptyList<UpdateWatchUiModel>())

                    val flows = trackedGroups.map { group ->
                        val mangaId = group.trackingMangaId!!
                        combine(
                            getManga.subscribe(mangaId),
                            chapterRepository.getChapterByMangaIdAsFlow(mangaId),
                        ) { manga, chapters ->
                            if (manga == null || chapters.isEmpty()) return@combine null

                            val latestChapter = chapters.filter { it.dateUpload > 0 }
                                .maxByOrNull { it.dateUpload } ?: return@combine null

                            val daysSinceRelease = (System.currentTimeMillis() - latestChapter.dateUpload) / (1000 * 60 * 60 * 24)

                            if (daysSinceRelease >= 7) {
                                UpdateWatchUiModel.Item(group, manga, latestChapter, daysSinceRelease)
                            } else {
                                null
                            }
                        }
                    }
                    combine(flows) { it.filterNotNull() }.map { items ->
                        val dueToday = items.filter { it.daysSinceRelease == 7L }
                        val overdue = items.filter { it.daysSinceRelease > 7L }

                        mutableListOf<UpdateWatchUiModel>().apply {
                            if (dueToday.isNotEmpty()) {
                                add(UpdateWatchUiModel.Header("Due today"))
                                addAll(dueToday)
                            }
                            if (overdue.isNotEmpty()) {
                                add(UpdateWatchUiModel.Header("Overdue"))
                                addAll(overdue.sortedByDescending { it.daysSinceRelease })
                            }
                        }
                    }
                }
                .collectLatest { items ->
                    mutableState.update { it.copy(items = items) }
                }
        }
    }

    fun pauseTracking(groupId: Long) {
        screenModelScope.launchIO {
            try {
                manageLinkedSourceGroup.updateIsPaused(groupId, true)
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
        val group: LinkedSourceGroup,
        val trackingManga: Manga,
        val latestChapter: Chapter,
        val daysSinceRelease: Long,
    ) : UpdateWatchUiModel
}
