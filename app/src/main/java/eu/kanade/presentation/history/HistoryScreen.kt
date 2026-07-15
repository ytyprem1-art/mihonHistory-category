package eu.kanade.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource as androidStringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.history.components.HistoryItem
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.history.HistoryScreenModel
import eu.kanade.tachiyomi.ui.mod.updatewatch.UpdateWatchContent
import eu.kanade.tachiyomi.ui.mod.updatewatch.UpdateWatchScreenModel
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.ListGroupHeader
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import java.time.LocalDate
import androidx.compose.material.icons.outlined.Delete

@Composable
fun HistoryScreen(
    state: HistoryScreenModel.State,
    updateWatchState: UpdateWatchScreenModel.State,
    snackbarHostState: SnackbarHostState,
    onSearchQueryChange: (String?) -> Unit,
    onClickCover: (mangaId: Long) -> Unit,
    onClickResume: (mangaId: Long, chapterId: Long) -> Unit,
    onClickFavorite: (mangaId: Long) -> Unit,
    onDialogChange: (HistoryScreenModel.Dialog?) -> Unit,
    onTabSelected: (Long) -> Unit,
    onClickChangeCategory: (mangaId: Long) -> Unit,
    onClickLinkedSourceGroups: () -> Unit,
    onClickGroup: (groupId: Long) -> Unit,
    onPauseTracking: (Long) -> Unit,
    onClickTrackedManga: () -> Unit,
    screenModel: HistoryScreenModel,
) {
    val scrollStates = rememberSaveable(
        saver = Saver<MutableMap<Long, LazyListState>, Map<Long, List<Int>>>(
            save = { map ->
                map.mapValues { (_, state) ->
                    listOf(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
                }
            },
            restore = { savedMap ->
                val restoredMap = mutableMapOf<Long, LazyListState>()
                savedMap.forEach { (id, values) ->
                    restoredMap[id] = LazyListState(
                        firstVisibleItemIndex = values[0],
                        firstVisibleItemScrollOffset = values[1],
                    )
                }
                restoredMap
            },
        ),
    ) {
        mutableMapOf()
    }
    val scrollState = scrollStates.getOrPut(state.selectedCategoryId) { LazyListState() }

    val filteredHistory = remember(state.list, state.selectedCategoryId, state.mangaToCategoryMap) {
        val history = state.list ?: emptyList()
        val result = history.filter { uiModel ->
            when (uiModel) {
                is HistoryUiModel.Header -> true
                is HistoryUiModel.Item -> {
                    val cId = state.mangaToCategoryMap[uiModel.item.mangaId] ?: 0L
                    cId == state.selectedCategoryId
                }
                is HistoryUiModel.Group -> {
                    val cId = state.mangaToCategoryMap[uiModel.representative.mangaId] ?: 0L
                    cId == state.selectedCategoryId
                }
            }
        }
        result.filterIndexed { index, uiModel ->
            if (uiModel is HistoryUiModel.Header) {
                val next = result.getOrNull(index + 1)
                next is HistoryUiModel.Item || next is HistoryUiModel.Group
            } else {
                true
            }
        }
    }

    Scaffold(
        topBar = { scrollBehavior ->
            Column(modifier = Modifier.fillMaxWidth()) {
                if (state.selectionMode) {
                    val filteredHistoryIds = remember(filteredHistory) {
                        filteredHistory.filterIsInstance<HistoryUiModel.Item>().map { it.item.mangaId }
                    }
                    AppBar(
                        titleContent = {
                            AppBarTitle(state.selected.size.toString())
                        },
                        actions = {
                            AppBarActions(
                                listOf(
                                    AppBar.Action(
                                        title = androidStringResource(R.string.history_categories_delete),
                                        icon = Icons.Outlined.Delete,
                                        onClick = {
                                            onDialogChange(
                                                HistoryScreenModel.Dialog.DeleteSelected(state.selected)
                                            )
                                        },
                                        enabled = state.selected.isNotEmpty(),
                                    ),
                                    AppBar.Action(
                                        title = androidStringResource(R.string.history_categories_move_to),
                                        icon = Icons.Outlined.Folder,
                                        onClick = {
                                            onDialogChange(
                                                HistoryScreenModel.Dialog.MoveSelectedToHistoryCategory(
                                                    state.selected,
                                                    state.historyCategories
                                                )
                                            )
                                        },
                                        enabled = state.selected.isNotEmpty(),
                                    ),
                                    AppBar.Action(
                                        title = if (state.selected.size == 1) "Add to history group" else "Create history group",
                                        icon = Icons.Outlined.Merge,
                                        onClick = {
                                            if (state.selected.size == 1) {
                                                screenModel.showAddToHistoryGroupDialog(state.selected.first())
                                            } else {
                                                val selectedItems = state.list?.filterIsInstance<HistoryUiModel.Item>()
                                                    ?.filter { it.item.mangaId in state.selected }
                                                    .orEmpty()

                                                val distinctTitles = selectedItems.map { it.item.title }.distinct()
                                                val suggestedName = if (distinctTitles.size == 1) {
                                                    distinctTitles.first()
                                                } else {
                                                    selectedItems.firstOrNull()?.item?.title ?: ""
                                                }

                                                onDialogChange(
                                                    HistoryScreenModel.Dialog.CreateHistoryGroup(
                                                        state.selected,
                                                        suggestedName
                                                    )
                                                )
                                            }
                                        },
                                        enabled = run {
                                            if (state.selected.size >= 2) return@run true
                                            if (state.selected.size == 1) {
                                                // Only enable for ungrouped items as per requirements
                                                val mangaId = state.selected.first()
                                                val isGrouped = state.list?.filterIsInstance<HistoryUiModel.Group>()
                                                    ?.any { it.representative.mangaId == mangaId } == true
                                                !isGrouped
                                            } else {
                                                false
                                            }
                                        },
                                    ),
                                    AppBar.Action(
                                        title = stringResource(MR.strings.action_select_all),
                                        icon = Icons.Outlined.SelectAll,
                                        onClick = { screenModel.selectAll(filteredHistoryIds) },
                                    ),
                                    AppBar.Action(
                                        title = stringResource(MR.strings.action_select_inverse),
                                        icon = Icons.Outlined.FlipToBack,
                                        onClick = { screenModel.invertSelection(filteredHistoryIds) },
                                    ),
                                ),
                            )
                        },
                        isActionMode = true,
                        onCancelActionMode = { screenModel.toggleSelectionMode() },
                        scrollBehavior = scrollBehavior,
                    )
                } else {
                    SearchToolbar(
                        titleContent = { AppBarTitle(stringResource(MR.strings.history)) },
                        searchQuery = state.searchQuery,
                        onChangeSearchQuery = onSearchQueryChange,
                        actions = {
                            val actions = mutableListOf<AppBar.Action>()

                            if (state.selectedCategoryId == HistoryScreenModel.State.UPDATE_WATCH_TAB_ID) {
                                actions.add(
                                    AppBar.Action(
                                        title = "Tracked manga",
                                        icon = Icons.AutoMirrored.Outlined.ListAlt,
                                        onClick = onClickTrackedManga,
                                    )
                                )
                            }

                            // Tombol Edit Kategori (Hanya muncul jika bukan tab "Semua")
                            if (state.selectedCategoryId != 0L) {
                                state.historyCategories.find { it.id == state.selectedCategoryId }?.let { category ->
                                    actions.add(
                                        AppBar.Action(
                                            title = androidStringResource(R.string.history_categories_manage),
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
                                    title = "Linked source groups",
                                    icon = Icons.Outlined.CollectionsBookmark,
                                    onClick = onClickLinkedSourceGroups,
                                )
                            )

                            actions.add(
                                AppBar.Action(
                                    title = androidStringResource(R.string.history_select),
                                    icon = Icons.Outlined.Checklist,
                                    onClick = { screenModel.toggleSelectionMode() },
                                )
                            )

                            actions.add(
                                AppBar.Action(
                                    title = androidStringResource(R.string.history_categories_create),
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
                                ),
                            )

                            AppBarActions(actions)
                        },
                        scrollBehavior = scrollBehavior,
                    )
                }

                ScrollableTabRow(
                    selectedTabIndex = when (state.selectedCategoryId) {
                        HistoryScreenModel.State.UPDATE_WATCH_TAB_ID -> 0
                        0L -> 1
                        else -> {
                            val index = state.historyCategories.indexOfFirst { it.id == state.selectedCategoryId }
                            if (index == -1) 1 else index + 2
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Tab(
                        selected = state.selectedCategoryId == HistoryScreenModel.State.UPDATE_WATCH_TAB_ID,
                        onClick = { onTabSelected(HistoryScreenModel.State.UPDATE_WATCH_TAB_ID) },
                        text = { Text("Update Watch") },
                    )
                    Tab(
                        selected = state.selectedCategoryId == 0L,
                        onClick = { onTabSelected(0L) },
                        text = { Text(androidStringResource(R.string.history_categories_all)) },
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
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        if (state.selectedCategoryId == HistoryScreenModel.State.UPDATE_WATCH_TAB_ID) {
            UpdateWatchContent(
                state = updateWatchState,
                contentPadding = contentPadding,
                onClickManga = onClickCover,
                onPauseTracking = onPauseTracking,
            )
            return@Scaffold
        }
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
                        history = filteredHistory,
                        contentPadding = contentPadding,
                        scrollState = scrollState,
                        selectionMode = state.selectionMode,
                        selected = state.selected,
                        onClickCover = { history -> onClickCover(history.mangaId) },
                        onClickResume = { history -> onClickResume(history.mangaId, history.chapterId) },
                        onClickDelete = { item -> onDialogChange(HistoryScreenModel.Dialog.Delete(item)) },
                        onClickFavorite = { history -> onClickFavorite(history.mangaId) },
                        onClickChangeCategory = onClickChangeCategory,
                        onClickGroup = onClickGroup,
                        onClickDeleteGroup = { group -> onDialogChange(HistoryScreenModel.Dialog.DeleteHistoryGroup(group)) },
                        onToggleSelection = screenModel::toggleSelection,
                    )
            }
        }
    }
}

@Composable
private fun HistoryScreenContent(
    history: List<HistoryUiModel>,
    contentPadding: PaddingValues,
    scrollState: LazyListState,
    selectionMode: Boolean,
    selected: Set<Long>,
    onClickCover: (HistoryWithRelations) -> Unit,
    onClickResume: (HistoryWithRelations) -> Unit,
    onClickDelete: (HistoryWithRelations) -> Unit,
    onClickFavorite: (HistoryWithRelations) -> Unit,
    onClickChangeCategory: (Long) -> Unit,
    onClickGroup: (Long) -> Unit,
    onClickDeleteGroup: (tachiyomi.domain.history.group.model.HistoryGroup) -> Unit,
    onToggleSelection: (Long) -> Unit,
) {
    FastScrollLazyColumn(
        contentPadding = contentPadding,
        state = scrollState,
    ) {
        items(
            items = history,
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
                            if (selectionMode) {
                                onToggleSelection(value.mangaId)
                            } else {
                                onClickChangeCategory(value.mangaId)
                            }
                        },
                        selectionMode = selectionMode,
                        selected = value.mangaId in selected,
                    )
                }

                is HistoryUiModel.Group -> {
                    val value = item.representative
                    HistoryItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItemFastScroll(),
                        history = value,
                        onClickCover = { onClickGroup(item.group.id) },
                        onClickResume = { onClickGroup(item.group.id) },
                        onClickDelete = { onClickDeleteGroup(item.group) },
                        onClickFavorite = { /* Disable favorite for groups for now */ },
                        onLongClick = {
                            onToggleSelection(value.mangaId)
                        },
                        selectionMode = selectionMode,
                        selected = value.mangaId in selected,
                        subtitleBadge = {
                            Text(
                                text = "${item.memberCount} sources",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.secondaryContainer,
                                        MaterialTheme.shapes.extraSmall
                                    )
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    )
                }
            }
        }
    }
}

sealed interface HistoryUiModel {
    data class Header(val date: LocalDate) : HistoryUiModel
    data class Item(val item: HistoryWithRelations) : HistoryUiModel
    data class Group(
        val group: tachiyomi.domain.history.group.model.HistoryGroup,
        val representative: HistoryWithRelations,
        val memberCount: Int
    ) : HistoryUiModel
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
