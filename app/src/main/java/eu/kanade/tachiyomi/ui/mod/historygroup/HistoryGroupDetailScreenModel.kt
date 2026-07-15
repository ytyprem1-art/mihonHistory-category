package eu.kanade.tachiyomi.ui.mod.historygroup

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.util.insertSeparators
import eu.kanade.presentation.history.HistoryUiModel
import eu.kanade.tachiyomi.util.lang.toLocalDate
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.history.group.interactor.ManageHistoryGroups
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.history.model.HistoryWithRelations
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class HistoryGroupDetailScreenModel(
    private val groupId: Long,
    private val manageHistoryGroups: ManageHistoryGroups = Injekt.get(),
    private val getHistory: GetHistory = Injekt.get(),
) : StateScreenModel<HistoryGroupDetailScreenModel.State>(State()) {

    init {
        screenModelScope.launchIO {
            val group = manageHistoryGroups.getGroups().find { it.id == groupId }
            mutableState.update { it.copy(groupName = group?.name ?: "") }
        }

        screenModelScope.launchIO {
            combine(
                manageHistoryGroups.subscribeMembers(groupId),
                getHistory.subscribe(""),
            ) { memberIds, historyList ->
                historyList.filter { it.mangaId in memberIds }
                    .toHistoryUiModels()
            }.collectLatest { list ->
                mutableState.update { it.copy(list = list) }
            }
        }
    }

    private fun List<HistoryWithRelations>.toHistoryUiModels(): List<HistoryUiModel> {
        return map { HistoryUiModel.Item(it) }
            .insertSeparators { before, after ->
                val beforeDate = before?.item?.readAt?.time?.toLocalDate()
                val afterDate = after?.item?.readAt?.time?.toLocalDate()
                when {
                    beforeDate != afterDate && afterDate != null -> HistoryUiModel.Header(afterDate)
                    else -> null
                }
            }
    }

    @Immutable
    data class State(
        val groupName: String = "",
        val list: List<HistoryUiModel>? = null,
    )
}
