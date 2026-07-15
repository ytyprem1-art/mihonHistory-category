package eu.kanade.tachiyomi.ui.mod.updatewatch

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.history.components.HistoryItem
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.library.service.LibraryPreferences
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

        val scrollState = rememberLazyListState()

        LaunchedEffect(screenModel.sortMode) {
            scrollState.scrollToItem(0)
        }

        Scaffold(
            topBar = { scrollBehavior ->
                SearchToolbar(
                    searchQuery = state.searchQuery,
                    onChangeSearchQuery = screenModel::updateSearchQuery,
                    titleContent = { Text("Tracked manga") },
                    navigateUp = navigator::pop,
                    actions = {
                        var showSortMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Sort,
                                    contentDescription = stringResource(MR.strings.action_sort),
                                )
                            }

                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                            ) {
                                listOf(
                                    LibraryPreferences.TrackedMangaSort.NewestRelease to "Newest release",
                                    LibraryPreferences.TrackedMangaSort.OldestRelease to "Oldest release",
                                    LibraryPreferences.TrackedMangaSort.TitleAZ to MR.strings.action_sort_alpha,
                                    LibraryPreferences.TrackedMangaSort.TitleZA to "Title Z–A",
                                ).forEach { (mode, titleRes) ->
                                    DropdownMenuItem(
                                        text = {
                                            val title = when (titleRes) {
                                                is StringResource -> stringResource(titleRes)
                                                is String -> titleRes
                                                else -> ""
                                            }
                                            Text(title)
                                        },
                                        onClick = {
                                            screenModel.setSortMode(mode)
                                            showSortMenu = false
                                        },
                                        trailingIcon = {
                                            RadioButton(
                                                selected = screenModel.sortMode == mode,
                                                onClick = null,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            UpdateWatchManagerContent(
                state = state,
                contentPadding = contentPadding,
                scrollState = scrollState,
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
    scrollState: androidx.compose.foundation.lazy.LazyListState,
    onClickManga: (Long) -> Unit,
    onUntrack: (Long) -> Unit,
) {
    val items = state.items
    if (items == null) {
        LoadingScreen(Modifier.padding(contentPadding))
    } else if (items.isEmpty()) {
        val msg = if (!state.searchQuery.isNullOrBlank()) {
            MR.strings.no_results_found
        } else {
            null
        }
        EmptyScreen(
            message = if (msg != null) stringResource(msg) else "No manga are currently tracked for updates.",
            modifier = Modifier.padding(contentPadding),
        )
    } else {
        FastScrollLazyColumn(
            contentPadding = contentPadding,
            state = scrollState,
        ) {
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
                                    Icon(
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
