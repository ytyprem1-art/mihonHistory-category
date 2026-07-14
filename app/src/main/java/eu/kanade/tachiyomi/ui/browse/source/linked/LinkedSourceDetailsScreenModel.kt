package eu.kanade.tachiyomi.ui.browse.source.linked

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.linked.interactor.ManageLinkedSourceGroup
import tachiyomi.domain.source.linked.model.LinkedSourceGroup
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class LinkedSourceDetailsScreenModel(
    private val groupId: Long,
    private val manageLinkedSourceGroup: ManageLinkedSourceGroup = Injekt.get(),
) : StateScreenModel<LinkedSourceDetailsScreenModel.State>(State()) {

    init {
        screenModelScope.launchIO {
            val group = manageLinkedSourceGroup.getGroupById(groupId)
            mutableState.update { it.copy(group = group) }
        }

        screenModelScope.launchIO {
            manageLinkedSourceGroup.subscribeMembers(groupId).collectLatest { members ->
                mutableState.update { it.copy(members = members) }
            }
        }
    }

    data class State(
        val group: LinkedSourceGroup? = null,
        val members: List<Manga> = emptyList(),
    )
}
