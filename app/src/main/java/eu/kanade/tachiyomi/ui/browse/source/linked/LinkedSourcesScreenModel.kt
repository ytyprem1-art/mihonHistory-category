package eu.kanade.tachiyomi.ui.browse.source.linked

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.source.linked.interactor.ManageLinkedSourceGroup
import tachiyomi.domain.source.linked.model.LinkedSourceGroup
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.core.common.util.lang.launchIO

class LinkedSourcesScreenModel(
    private val manageLinkedSourceGroup: ManageLinkedSourceGroup = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
) : StateScreenModel<LinkedSourcesScreenModel.State>(State()) {

    init {
        screenModelScope.launchIO {
            manageLinkedSourceGroup.subscribe()
                .distinctUntilChanged()
                .flatMapLatest { groups ->
                    if (groups.isEmpty()) return@flatMapLatest kotlinx.coroutines.flow.flowOf(emptyList<GroupWithMetadata>())

                    val flows = groups.map { group ->
                        manageLinkedSourceGroup.subscribeMembers(group.id).map { members ->
                            GroupWithMetadata(
                                group = group,
                                representativeManga = members.firstOrNull(),
                                sourceNames = members.map { sourceManager.getOrStub(it.source).name }
                            )
                        }
                    }
                    combine(flows) { it.toList() }
                }
                .collectLatest { groups ->
                    mutableState.update { it.copy(groups = groups) }
                }
        }
    }

    fun createGroup(name: String) {
        screenModelScope.launchIO {
            manageLinkedSourceGroup.create(name)
        }
    }

    fun renameGroup(id: Long, name: String) {
        screenModelScope.launchIO {
            manageLinkedSourceGroup.rename(id, name)
        }
    }

    fun deleteGroup(id: Long) {
        screenModelScope.launchIO {
            manageLinkedSourceGroup.delete(id)
        }
    }

    data class State(
        val groups: List<GroupWithMetadata> = emptyList(),
    )

    data class GroupWithMetadata(
        val group: LinkedSourceGroup,
        val representativeManga: Manga?,
        val sourceNames: List<String>,
    )
}
