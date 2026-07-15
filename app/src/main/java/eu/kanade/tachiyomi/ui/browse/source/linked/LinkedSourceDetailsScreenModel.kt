package eu.kanade.tachiyomi.ui.browse.source.linked

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.source.linked.interactor.ManageLinkedSourceGroup
import tachiyomi.domain.source.linked.model.LinkedSourceGroup
import mihon.domain.source.interactor.UpdateMangaFromRemote
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import logcat.LogPriority
import eu.kanade.tachiyomi.ui.manga.LinkedMember
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class LinkedSourceDetailsScreenModel(
    private val groupId: Long,
    private val manageLinkedSourceGroup: ManageLinkedSourceGroup = Injekt.get(),
    private val chapterRepository: ChapterRepository = Injekt.get(),
    private val historyRepository: HistoryRepository = Injekt.get(),
    private val updateMangaFromRemote: UpdateMangaFromRemote = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
) : StateScreenModel<LinkedSourceDetailsScreenModel.State>(State()) {

    init {
        screenModelScope.launchIO {
            val group = manageLinkedSourceGroup.getGroupById(groupId)
            mutableState.update { it.copy(group = group) }
        }

        screenModelScope.launchIO {
            manageLinkedSourceGroup.subscribeMembers(groupId)
                .flatMapLatest { members ->
                    if (members.isEmpty()) return@flatMapLatest kotlinx.coroutines.flow.flowOf(emptyList<LinkedMember>())

                    val flows = members.map { member ->
                        combine(
                            chapterRepository.getChapterByMangaIdAsFlow(member.id),
                            historyRepository.getLatestHistoryByMangaIdAsFlow(member.id),
                        ) { chapters, history ->
                            val latestChapter = chapters.maxByOrNull { it.chapterNumber }
                            LinkedMember(
                                manga = member,
                                latestChapter = latestChapter?.chapterNumber,
                                latestChapterId = latestChapter?.id,
                                latestChapterDateUpload = latestChapter?.dateUpload?.takeIf { it > 0 },
                                lastRead = history?.chapterNumber,
                                lastReadChapterId = history?.chapterId,
                                readAt = history?.readAt?.time,
                            )
                        }
                    }
                    combine(flows) { it.toList() }
                }
                .collectLatest { members ->
                    mutableState.update { it.copy(members = members) }
                }
        }
    }

    fun refreshMember(mangaId: Long) {
        if (state.value.refreshingIds.contains(mangaId)) return

        screenModelScope.launchIO {
            mutableState.update { it.copy(refreshingIds = it.refreshingIds + mangaId) }
            try {
                val manga = manageLinkedSourceGroup.subscribeMembers(groupId).map { members ->
                    members.find { it.id == mangaId }
                }.flatMapLatest { manga ->
                    if (manga == null) kotlinx.coroutines.flow.flowOf(null)
                    else kotlinx.coroutines.flow.flowOf(manga)
                }.firstOrNull() ?: return@launchIO

                val source = sourceManager.getOrStub(manga.source)
                updateMangaFromRemote(
                    source = source,
                    manga = manga,
                    fetchDetails = true,
                    fetchChapters = true,
                    manualFetch = true,
                ).getOrThrow()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
            } finally {
                mutableState.update { it.copy(refreshingIds = it.refreshingIds - mangaId) }
            }
        }
    }

    fun removeMember(mangaId: Long, sourceId: Long) {
        screenModelScope.launchIO {
            try {
                manageLinkedSourceGroup.removeMember(groupId, mangaId, sourceId)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    data class State(
        val group: LinkedSourceGroup? = null,
        val members: List<LinkedMember> = emptyList(),
        val refreshingIds: Set<Long> = emptySet(),
    )
}
