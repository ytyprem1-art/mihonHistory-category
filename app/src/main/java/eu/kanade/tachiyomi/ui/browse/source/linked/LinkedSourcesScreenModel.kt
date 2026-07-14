package eu.kanade.tachiyomi.ui.browse.source.linked

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.update
import tachiyomi.domain.source.linked.interactor.ManageLinkedSourceGroup
import tachiyomi.domain.source.linked.model.LinkedSourceGroup
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.core.common.util.lang.launchIO

class LinkedSourcesScreenModel(
    private val manageLinkedSourceGroup: ManageLinkedSourceGroup = Injekt.get(),
) : StateScreenModel<LinkedSourcesScreenModel.State>(State()) {

    init {
        screenModelScope.launchIO {
            manageLinkedSourceGroup.subscribe().collect { groups ->
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
        val groups: List<LinkedSourceGroup> = emptyList(),
    )
}
