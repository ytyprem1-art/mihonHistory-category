package eu.kanade.tachiyomi.ui.mod.historygroup

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.history.HistoryUiModel
import eu.kanade.presentation.history.components.HistoryItem
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import kotlinx.coroutines.flow.collectLatest
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

        HistoryGroupDetailScreen(
            groupName = state.groupName,
            list = state.list,
            onClickCover = { navigator.push(MangaScreen(it)) },
            onClickResume = { mangaId, chapterId ->
                context.startActivity(ReaderActivity.newIntent(context, mangaId, chapterId))
            },
            navigateUp = navigator::pop,
        )
    }
}

@Composable
private fun HistoryGroupDetailScreen(
    groupName: String,
    list: List<HistoryUiModel>?,
    onClickCover: (Long) -> Unit,
    onClickResume: (Long, Long) -> Unit,
    navigateUp: () -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = groupName,
                navigateUp = navigateUp,
                scrollBehavior = scrollBehavior,
            )
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
                                onLongClick = { /* Disable move for now */ },
                                selectionMode = false,
                                selected = false,
                            )
                        }
                        is HistoryUiModel.Group -> {
                            // This detail screen shouldn't contain other groups,
                            // but we must be exhaustive for the sealed interface.
                        }
                    }
                }
            }
        }
    }
}
