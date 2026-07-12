package eu.kanade.presentation.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.history.components.HistoryItem
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.tachiyomi.ui.history.HistoryScreenModel
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.ListGroupHeader
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import java.time.LocalDate

@Composable
fun HistoryScreen(
    state: HistoryScreenModel.State,
    snackbarHostState: SnackbarHostState,
    onSearchQueryChange: (String?) -> Unit,
    onClickCover: (mangaId: Long) -> Unit,
    onClickResume: (mangaId: Long, chapterId: Long) -> Unit,
    onClickFavorite: (mangaId: Long) -> Unit,
    onDialogChange: (HistoryScreenModel.Dialog?) -> Unit,
    onTabSelected: (Long) -> Unit,
    onClickChangeCategory: (mangaId: Long) -> Unit,
    screenModel: HistoryScreenModel,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            Column(modifier = Modifier.fillMaxWidth()) {
                SearchToolbar(
                    titleContent = { AppBarTitle(stringResource(MR.strings.history)) },
                    searchQuery = state.searchQuery,
                    onChangeSearchQuery = onSearchQueryChange,
                    actions = {
                        val actions = mutableListOf<AppBar.Action>()

                        // Tombol Edit Kategori (Hanya muncul jika bukan tab "Semua")
                        if (state.selectedCategoryId != 0L) {
                            state.historyCategories.find { it.id == state.selectedCategoryId }?.let { category ->
                                actions.add(
                                    AppBar.Action(
                                        title = "Kelola Kategori",
                                        icon = Icons.Outlined.Settings,
                                        onClick = {
                                            onDialogChange(HistoryScreenModel.Dialog.ManageHistoryCategory(category))
                                        },
                                    )
                                )
                            }
                        }

                        actions.add(
                            AppBar.Action(
                                title = "Tambah Kategori History",
                                icon = Icons.Outlined.Create,
                                onClick = {
                                    onDialogChange(HistoryScreenModel.Dialog.CreateHistoryCategory)
                                },
                            )
                        )

                        actions.add(
                            AppBar.Action(
                                title = stringResource(MR.strings.pref_clear_history),
                                icon = Icons.Outlined.DeleteSweep,
                                onClick = {
                                    onDialogChange(HistoryScreenModel.Dialog.DeleteAll)
                                },
                            )
                        )

                        AppBarActions(actions)
                    },
                    scrollBehavior = scrollBehavior,
                )

                if (state.historyCategories.isNotEmpty()) {
                    ScrollableTabRow(
                        selectedTabIndex = run {
                            val index = state.historyCategories.indexOfFirst { it.id == state.selectedCategoryId }
                            if (index == -1) 0 else index + 1
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Tab(
                            selected = state.selectedCategoryId == 0L,
                            onClick = { onTabSelected(0L) },
                            text = { Text("Semua") },
                        )
                        state.historyCategories.forEach { category ->
                            Tab(
                                selected = state.selectedCategoryId == category.id,
                                onClick = { onTabSelected(category.id) },
                                text = { Text(category.name) },
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        state.list.let {
            if (it == null) {
                LoadingScreen(Modifier.padding(contentPadding))
            } else if (it.isEmpty()) {
                val msg = if (!state.searchQuery.isNullOrEmpty()) {
                    MR.strings.no_results_found
                } else {
                    MR.strings.information_no_recent_manga
                }
                EmptyScreen(
                    stringRes = msg,
                    modifier = Modifier.padding(contentPadding),
                )
            } else {
                HistoryScreenContent(
                    history = it,
                    contentPadding = contentPadding,
                    selectedCategoryId = state.selectedCategoryId,
                    categoryMap = state.mangaToCategoryMap, // 👈 OPER MAP MEMORI
                    onClickCover = { history -> onClickCover(history.mangaId) },
                    onClickResume = { history -> onClickResume(history.mangaId, history.chapterId) },
                    onClickDelete = { item -> onDialogChange(HistoryScreenModel.Dialog.Delete(item)) },
                    onClickFavorite = { history -> onClickFavorite(history.mangaId) },
                    onClickChangeCategory = onClickChangeCategory,
                )
            }
        }
    }
}

@Composable
private fun HistoryScreenContent(
    history: List<HistoryUiModel>,
    contentPadding: PaddingValues,
    selectedCategoryId: Long,
    categoryMap: Map<Long, Long>,
    onClickCover: (HistoryWithRelations) -> Unit,
    onClickResume: (HistoryWithRelations) -> Unit,
    onClickDelete: (HistoryWithRelations) -> Unit,
    onClickFavorite: (HistoryWithRelations) -> Unit,
    onClickChangeCategory: (Long) -> Unit,
) {
    val filteredHistory = remember(history, selectedCategoryId, categoryMap) {
        val result = history.filter { uiModel ->
            when (uiModel) {
                is HistoryUiModel.Header -> true
                is HistoryUiModel.Item -> {
                    val cId = categoryMap[uiModel.item.mangaId] ?: 0L
                    cId == selectedCategoryId
                }
            }
        }
        result.filterIndexed { index, uiModel ->
            if (uiModel is HistoryUiModel.Header) {
                result.getOrNull(index + 1) is HistoryUiModel.Item
            } else {
                true
            }
        }
    }

    FastScrollLazyColumn(
        contentPadding = contentPadding,
    ) {
        items(
            items = filteredHistory,
            key = { "history-${it.hashCode()}" },
        ) { item ->
            when (item) {
                is HistoryUiModel.Header -> {
                    ListGroupHeader(
                        modifier = Modifier.animateItemFastScroll(),
                        text = relativeDateText(item.date),
                    )
                }

                is HistoryUiModel.Item -> {
                    val value = item.item

                    HistoryItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItemFastScroll(),
                        history = value,
                        onClickCover = { onClickCover(value) },
                        onClickResume = { onClickResume(value) },
                        onClickDelete = { onClickDelete(value) },
                        onClickFavorite = { onClickFavorite(value) },
                        onLongClick = {
                            onClickChangeCategory(value.mangaId)
                        },
                    )
                }
            }
        }
    }
}

sealed interface HistoryUiModel {
    data class Header(val date: LocalDate) : HistoryUiModel
    data class Item(val item: HistoryWithRelations) : HistoryUiModel
}

@PreviewLightDark
@Composable
internal fun HistoryScreenPreviews(
    @PreviewParameter(HistoryScreenModelStateProvider::class)
    historyState: HistoryScreenModel.State,
) {
    TachiyomiPreviewTheme {
        Text("Preview Mode")
    }
}
