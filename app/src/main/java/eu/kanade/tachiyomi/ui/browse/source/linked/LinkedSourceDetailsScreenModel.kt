package eu.kanade.tachiyomi.ui.browse.source.linked

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.linked.interactor.ManageLinkedSourceGroup
import tachiyomi.domain.source.linked.model.LinkedSourceGroup
import eu.kanade.tachiyomi.ui.manga.LinkedMember
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class LinkedSourceDetailsScreenModel(
    private val groupId: Long,
    private val manageLinkedSourceGroup: ManageLinkedSourceGroup = Injekt.get(),
    private val chapterRepository: ChapterRepository = Injekt.get(),
    private val historyRepository: HistoryRepository = Injekt.get(),
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

    data class State(
        val group: LinkedSourceGroup? = null,
        val members: List<LinkedMember> = emptyList(),
    )
}
