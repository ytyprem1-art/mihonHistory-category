package eu.kanade.tachiyomi.ui.mod.historygroup

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.history.HistoryUiModel
import eu.kanade.presentation.history.components.HistoryItem
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.tachiyomi.ui.browse.source.linked.LinkedSourceSearchScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.ListGroupHeader
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

class HistoryGroupDetailScreen(private val groupId: Long) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val screenModel = rememberScreenModel { HistoryGroupDetailScreenModel(groupId) }
        val state by screenModel.state.collectAsState()

        var showRemoveConfirmation by remember { mutableStateOf(false) }

        HistoryGroupDetailScreen(
            groupName = state.groupName,
            list = state.list,
            selectionMode = state.selectionMode,
            selected = state.selected,
            onClickCover = { navigator.push(MangaScreen(it)) },
            onClickResume = { mangaId, chapterId ->
                context.startActivity(ReaderActivity.newIntent(context, mangaId, chapterId))
            },
            onClickAdd = {
                navigator.push(LinkedSourceSearchScreen(groupId, state.groupName))
            },
            onClickEdit = screenModel::toggleSelectionMode,
            onClickRemove = { showRemoveConfirmation = true },
            onToggleSelection = screenModel::toggleSelection,
            navigateUp = navigator::pop,
        )

        if (showRemoveConfirmation) {
            AlertDialog(
                onDismissRequest = { showRemoveConfirmation = false },
                title = { Text("Remove from group") },
                text = { Text("Remove ${state.selected.size} members from this group? They will return to the normal History list.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            screenModel.removeSelectedFromGroup()
                            showRemoveConfirmation = false
                        },
                    ) {
                        Text(stringResource(MR.strings.action_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRemoveConfirmation = false }) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun HistoryGroupDetailScreen(
    groupName: String,
    list: List<HistoryUiModel>?,
    selectionMode: Boolean,
    selected: Set<Long>,
    onClickCover: (Long) -> Unit,
    onClickResume: (Long, Long) -> Unit,
    onClickAdd: () -> Unit,
    onClickEdit: () -> Unit,
    onClickRemove: () -> Unit,
    onToggleSelection: (Long) -> Unit,
    navigateUp: () -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            if (selectionMode) {
                AppBar(
                    titleContent = { Text(selected.size.toString()) },
                    actions = {
                        AppBarActions(
                            listOf(
                                AppBar.Action(
                                    title = "Remove from group",
                                    icon = Icons.Outlined.Delete,
                                    onClick = onClickRemove,
                                    enabled = selected.isNotEmpty(),
                                ),
                            )
                        )
                    },
                    isActionMode = true,
                    onCancelActionMode = onClickEdit,
                    scrollBehavior = scrollBehavior,
                )
            } else {
                AppBar(
                    title = groupName,
                    navigateUp = navigateUp,
                    actions = {
                        AppBarActions(
                            listOf(
                                AppBar.Action(
                                    title = stringResource(MR.strings.action_edit),
                                    icon = Icons.Outlined.Edit,
                                    onClick = onClickEdit,
                                ),
                                AppBar.Action(
                                    title = stringResource(MR.strings.action_add),
                                    icon = Icons.Outlined.Add,
                                    onClick = onClickAdd,
                                ),
                            ),
                        )
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { contentPadding ->
        if (list == null) {
            LoadingScreen(Modifier.padding(contentPadding))
        } else if (list.isEmpty()) {
            EmptyScreen(
                stringRes = MR.strings.information_no_recent_manga,
                modifier = Modifier.padding(contentPadding),
            )
        } else {
            FastScrollLazyColumn(
                contentPadding = contentPadding,
            ) {
                items(
                    items = list,
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
                            val history = item.item
                            HistoryItem(
                                modifier = Modifier.animateItemFastScroll(),
                                history = history,
                                onClickCover = { onClickCover(history.mangaId) },
                                onClickResume = { onClickResume(history.mangaId, history.chapterId) },
                                onClickDelete = { /* Disable delete for now */ },
                                onClickFavorite = { /* Disable favorite for now */ },
                                onLongClick = { onToggleSelection(history.mangaId) },
                                selectionMode = selectionMode,
                                selected = history.mangaId in selected,
                            )
                        }
                        is HistoryUiModel.Group -> {}
                    }
                }
            }
        }
    }
}
