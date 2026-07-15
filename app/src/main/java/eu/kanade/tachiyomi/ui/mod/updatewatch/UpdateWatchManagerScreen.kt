package eu.kanade.tachiyomi.ui.mod.updatewatch

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.history.components.HistoryItem
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class UpdateWatchManagerScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { UpdateWatchManagerScreenModel() }
        val state by screenModel.state.collectAsState()

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = "Tracked manga",
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            UpdateWatchManagerContent(
                state = state,
                contentPadding = contentPadding,
                onClickManga = { navigator.push(MangaScreen(it)) },
                onUntrack = screenModel::untrack,
            )
        }
    }
}

@Composable
private fun UpdateWatchManagerContent(
    state: UpdateWatchManagerScreenModel.State,
    contentPadding: PaddingValues,
    onClickManga: (Long) -> Unit,
    onUntrack: (Long) -> Unit,
) {
    val items = state.items
    if (items == null) {
        LoadingScreen(Modifier.padding(contentPadding))
    } else if (items.isEmpty()) {
        EmptyScreen(
            message = "No manga are currently tracked for updates.",
            modifier = Modifier.padding(contentPadding),
        )
    } else {
        FastScrollLazyColumn(contentPadding = contentPadding) {
            items(
                items = items,
                key = { "tracked-${it.trackingManga.id}" }
            ) { item ->
                val sourceManager = remember { Injekt.get<SourceManager>() }
                val sourceName = remember(item.trackingManga.source) {
                    sourceManager.getOrStub(item.trackingManga.source).name
                }

                var showMenu by remember { mutableStateOf(false) }

                HistoryItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItemFastScroll(),
                    history = HistoryWithRelations(
                        id = 0,
                        mangaId = item.trackingManga.id,
                        chapterId = item.latestChapter.id,
                        title = item.group?.name ?: item.trackingManga.title,
                        chapterNumber = item.latestChapter.chapterNumber,
                        readAt = if (item.latestChapter.dateUpload > 0) java.util.Date(item.latestChapter.dateUpload) else null,
                        readDuration = 0,
                        coverData = MangaCover(
                            mangaId = item.trackingManga.id,
                            sourceId = item.trackingManga.source,
                            isMangaFavorite = item.trackingManga.favorite,
                            url = item.trackingManga.thumbnailUrl,
                            lastModified = item.trackingManga.coverLastModified,
                        )
                    ),
                    secondaryText = run {
                        val ch = if (item.latestChapter.chapterNumber >= 0) {
                            "Ch. ${eu.kanade.presentation.util.formatChapterNumber(item.latestChapter.chapterNumber)}"
                        } else {
                            "No chapters"
                        }
                        "$ch · $sourceName"
                    },
                    onClickCover = { onClickManga(item.trackingManga.id) },
                    onClickResume = { onClickManga(item.trackingManga.id) },
                    onClickDelete = null,
                    onOverflowClick = { showMenu = true },
                    overflowContent = {
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Untrack") },
                                onClick = {
                                    onUntrack(item.trackingManga.id)
                                    showMenu = false
                                },
                                leadingIcon = {
                                    androidx.compose.material3.Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                    },
                    onClickFavorite = { /* unused */ },
                    onLongClick = { /* unused */ },
                    selectionMode = false,
                    selected = false,
                    subtitleBadge = {
                        val ageText = if (item.daysSinceRelease >= 0) {
                            "${item.daysSinceRelease} days since latest release"
                        } else {
                            "Unknown release date"
                        }
                        Text(
                            text = ageText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                )
            }
        }
    }
}
