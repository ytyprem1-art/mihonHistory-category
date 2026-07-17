package eu.kanade.tachiyomi.ui.mod.updatewatch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
import eu.kanade.presentation.util.relativeTimeSpanString
import eu.kanade.tachiyomi.ui.mod.updatewatch.helper.UpdateWatchRefreshHelper
import eu.kanade.tachiyomi.ui.mod.updatewatch.worker.UpdateWatchRefreshScheduler
import eu.kanade.tachiyomi.ui.mod.updatewatch.worker.UpdateWatchRefreshWorker
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.coroutines.launch
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.history.model.UpdateWatch
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

import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions

class UpdateWatchManagerScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { UpdateWatchManagerScreenModel() }
        val state by screenModel.state.collectAsState()

        val scrollState = rememberLazyListState()

        var editItem by remember { mutableStateOf<UpdateWatchUiModel.Item?>(null) }

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
                        val scope = rememberCoroutineScope()
                        val context = LocalContext.current
                        if (eu.kanade.tachiyomi.BuildConfig.DEBUG) {
                            var showDebugMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { showDebugMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Debug",
                                    )
                                }
                                DropdownMenu(
                                    expanded = showDebugMenu,
                                    onDismissRequest = { showDebugMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Run normally (dry run)") },
                                        onClick = {
                                            UpdateWatchRefreshScheduler.runNow(context, UpdateWatchRefreshWorker.SIM_NONE)
                                            showDebugMenu = false
                                            scope.launch {
                                                screenModel.snackbarHostState.showSnackbar("Background refresh dry run started")
                                            }
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Simulate HTTP 429") },
                                        onClick = {
                                            UpdateWatchRefreshScheduler.runNow(context, UpdateWatchRefreshWorker.SIM_HTTP_429)
                                            showDebugMenu = false
                                            scope.launch {
                                                screenModel.snackbarHostState.showSnackbar("Simulating HTTP 429")
                                            }
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Simulate HTTP 403 / Cloudflare") },
                                        onClick = {
                                            UpdateWatchRefreshScheduler.runNow(context, UpdateWatchRefreshWorker.SIM_HTTP_403)
                                            showDebugMenu = false
                                            scope.launch {
                                                screenModel.snackbarHostState.showSnackbar("Simulating HTTP 403")
                                            }
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Simulate Transient Failure") },
                                        onClick = {
                                            UpdateWatchRefreshScheduler.runNow(context, UpdateWatchRefreshWorker.SIM_TRANSIENT)
                                            showDebugMenu = false
                                            scope.launch {
                                                screenModel.snackbarHostState.showSnackbar("Simulating Transient Failure")
                                            }
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Simulate Ordinary Failure") },
                                        onClick = {
                                            UpdateWatchRefreshScheduler.runNow(context, UpdateWatchRefreshWorker.SIM_ORDINARY)
                                            showDebugMenu = false
                                            scope.launch {
                                                screenModel.snackbarHostState.showSnackbar("Simulating Ordinary Failure")
                                            }
                                        },
                                    )
                                }
                            }
                        }

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
            snackbarHost = { SnackbarHost(hostState = screenModel.snackbarHostState) },
        ) { contentPadding ->
            UpdateWatchManagerContent(
                state = state,
                contentPadding = contentPadding,
                scrollState = scrollState,
                onClickManga = { navigator.push(MangaScreen(it)) },
                onUntrack = screenModel::untrack,
                onEditBackgroundRefresh = { editItem = it },
            )

            if (editItem != null) {
                BackgroundRefreshEditDialog(
                    item = editItem!!,
                    onDismissRequest = { editItem = null },
                    onSave = { enabled, interval, profile ->
                        screenModel.updateBackgroundRefresh(editItem!!.trackingManga.id, enabled, interval, profile)
                        editItem = null
                    }
                )
            }
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
    onEditBackgroundRefresh: (UpdateWatchUiModel.Item) -> Unit,
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
                                text = { Text("Edit background refresh") },
                                onClick = {
                                    onEditBackgroundRefresh(item)
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.SettingsBackupRestore,
                                        contentDescription = null
                                    )
                                }
                            )

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
                        Column {
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

                            if (item.backgroundRefreshEnabled) {
                                val eligibility = UpdateWatchRefreshHelper.getEligibility(
                                    enabled = item.backgroundRefreshEnabled,
                                    expectedIntervalDays = item.expectedIntervalDays,
                                    refreshProfile = item.refreshProfile,
                                    latestChapterUploadDate = item.latestChapter.dateUpload,
                                )
                                Text(
                                    text = "Auto refresh · every ${item.expectedIntervalDays} days",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (eligibility.status == UpdateWatchRefreshHelper.RefreshStatus.ACTIVE)
                                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "Profile: ${item.refreshProfile.name.replace("_", " ").lowercase().capitalize()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                val lastCheckText = item.lastBackgroundCheckAt?.let {
                                    "Last checked ${relativeTimeSpanString(it)}"
                                } ?: "Not checked yet"
                                Text(
                                    text = lastCheckText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun BackgroundRefreshEditDialog(
    item: UpdateWatchUiModel.Item,
    onDismissRequest: () -> Unit,
    onSave: (Boolean, Int, UpdateWatch.RefreshProfile) -> Unit,
) {
    var enabled by remember { mutableStateOf(item.backgroundRefreshEnabled) }
    var interval by remember { mutableIntStateOf(item.expectedIntervalDays) }
    var profile by remember { mutableStateOf(item.refreshProfile) }
    var customInterval by remember { mutableStateOf(if (interval !in listOf(7, 14, 30)) interval.toString() else "") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Background Refresh Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Enable background refresh")
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                if (enabled) {
                    Text(
                        text = "Expected update interval (days)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    listOf(7, 14, 30).forEach { preset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    interval = preset
                                    customInterval = ""
                                    profile = UpdateWatch.RefreshProfile.fromInterval(preset)
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = interval == preset && customInterval.isEmpty(),
                                onClick = null
                            )
                            Text(
                                text = "$preset days",
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = customInterval.isNotEmpty() || (interval !in listOf(7, 14, 30)),
                            onClick = { if (customInterval.isEmpty()) customInterval = interval.toString() }
                        )
                        OutlinedTextField(
                            value = customInterval,
                            onValueChange = {
                                customInterval = it.filter { c -> c.isDigit() }
                                val newVal = customInterval.toIntOrNull()
                                if (newVal != null && newVal > 0) {
                                    interval = newVal
                                    profile = UpdateWatch.RefreshProfile.fromInterval(newVal)
                                }
                            },
                            label = { Text("Custom days") },
                            modifier = Modifier.padding(start = 8.dp).weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        text = "Refresh Profile",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    UpdateWatch.RefreshProfile.entries.forEach { p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { profile = p },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = profile == p,
                                onClick = null
                            )
                            Text(
                                text = when (p) {
                                    UpdateWatch.RefreshProfile.WEEKLY_STABLE -> "Weekly stable"
                                    UpdateWatch.RefreshProfile.SLOW_PERIODIC -> "Slow periodic"
                                    UpdateWatch.RefreshProfile.RAPID_IRREGULAR -> "Rapid / irregular"
                                },
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onSave(enabled, interval, profile) }) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismissRequest) {
                Text(stringResource(MR.strings.action_cancel))
            }
        }
    )
}

private fun String.capitalize(): String {
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
}
