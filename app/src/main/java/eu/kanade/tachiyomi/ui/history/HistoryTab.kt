package eu.kanade.tachiyomi.ui.history

import android.content.Context
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.history.HistoryScreen
import eu.kanade.presentation.history.components.HistoryCategoryDialog
import eu.kanade.presentation.history.components.HistoryDeleteAllDialog
import eu.kanade.presentation.history.components.HistoryDeleteDialog
import eu.kanade.presentation.manga.DuplicateMangaDialog
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import mihon.feature.migration.dialog.MigrateMangaDialog
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

data object HistoryTab : Tab {

    private val snackbarHostState = SnackbarHostState()

    private val resumeLastChapterReadEvent = Channel<Unit>()

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_history_enter)
            return TabOptions(
                index = 2u,
                title = stringResource(MR.strings.label_recent_manga),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        resumeLastChapterReadEvent.send(Unit)
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val screenModel = rememberScreenModel { HistoryScreenModel() }
        val state by screenModel.state.collectAsState()

        HistoryScreen(
            state = state,
            snackbarHostState = snackbarHostState,
            onSearchQueryChange = screenModel::updateSearchQuery,
            onClickCover = { navigator.push(MangaScreen(it)) },
            onClickResume = screenModel::getNextChapterForManga,
            onDialogChange = screenModel::setDialog,
            onClickFavorite = screenModel::addFavorite,
            onTabSelected = screenModel::updateSelectedCategory,
            onClickChangeCategory = screenModel::showChangeHistoryCategoryDialog,
            screenModel = screenModel,
        )

        val onDismissRequest = { screenModel.setDialog(null) }
        when (val dialog = state.dialog) {

            // 👇 TAMBAHKAN BLOK DIALOG KUSTOM INI DI SINI
            is HistoryScreenModel.Dialog.CreateHistoryCategory -> {
                var categoryName by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = onDismissRequest,
                    title = { androidx.compose.material3.Text("Tambah Kategori History Baru") },
                    text = {
                        androidx.compose.material3.OutlinedTextField(
                            value = categoryName,
                            onValueChange = { categoryName = it },
                            label = { androidx.compose.material3.Text("Nama Kategori") },
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                if (categoryName.isNotBlank()) {
                                    screenModel.createHistoryCategory(categoryName)
                                    onDismissRequest()
                                }
                            }
                        ) {
                            androidx.compose.material3.Text("Simpan")
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = onDismissRequest) {
                            androidx.compose.material3.Text("Batal")
                        }
                    }
                )
            }

            is HistoryScreenModel.Dialog.RenameHistoryCategory -> {
                var categoryName by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(dialog.category.name) }
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = onDismissRequest,
                    title = { androidx.compose.material3.Text("Ubah Nama Kategori") },
                    text = {
                        androidx.compose.material3.OutlinedTextField(
                            value = categoryName,
                            onValueChange = { categoryName = it },
                            label = { androidx.compose.material3.Text("Nama Kategori") },
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                if (categoryName.isNotBlank()) {
                                    screenModel.renameHistoryCategory(dialog.category.id, categoryName)
                                    onDismissRequest()
                                }
                            }
                        ) {
                            androidx.compose.material3.Text("Simpan")
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = onDismissRequest) {
                            androidx.compose.material3.Text("Batal")
                        }
                    }
                )
            }
            is HistoryScreenModel.Dialog.DeleteHistoryCategory -> {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = onDismissRequest,
                    title = { androidx.compose.material3.Text("Delete History Category") },
                    text = { androidx.compose.material3.Text("All manga in this category will become uncategorized.") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                screenModel.deleteHistoryCategory(dialog.category.id)
                                onDismissRequest()
                            }
                        ) {
                            androidx.compose.material3.Text("Delete")
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = onDismissRequest) {
                            androidx.compose.material3.Text("Cancel")
                        }
                    }
                )
            }

            is HistoryScreenModel.Dialog.Delete -> {
                HistoryDeleteDialog(
                    onDismissRequest = onDismissRequest,
                    onDelete = { all ->
                        if (all) {
                            screenModel.removeAllFromHistory(dialog.history.mangaId)
                        } else {
                            screenModel.removeFromHistory(dialog.history)
                        }
                    },
                )
            }
            is HistoryScreenModel.Dialog.DeleteAll -> {
                HistoryDeleteAllDialog(
                    onDismissRequest = onDismissRequest,
                    onDelete = screenModel::removeAllHistory,
                )
            }
            is HistoryScreenModel.Dialog.DuplicateManga -> {
                DuplicateMangaDialog(
                    duplicates = dialog.duplicates,
                    onDismissRequest = onDismissRequest,
                    onConfirm = { screenModel.addFavorite(dialog.manga) },
                    onOpenManga = { navigator.push(MangaScreen(it.id)) },
                    onMigrate = { screenModel.showMigrateDialog(dialog.manga, it) },
                )
            }
            is HistoryScreenModel.Dialog.ChangeHistoryCategory -> {
                HistoryCategoryDialog(
                    categories = dialog.categories,
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onConfirm = { categoryId ->
                        screenModel.moveMangaToHistoryCategory(dialog.mangaId, categoryId)
                    },
                )
            }
            is HistoryScreenModel.Dialog.ChangeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = { navigator.push(CategoryScreen()) },
                    onConfirm = { include, _ ->
                        screenModel.moveMangaToCategoriesAndAddToLibrary(dialog.manga, include)
                    },
                )
            }
            is HistoryScreenModel.Dialog.Migrate -> {
                MigrateMangaDialog(
                    current = dialog.current,
                    target = dialog.target,
                    // Initiated from the context of [dialog.target] so we show [dialog.current].
                    onClickTitle = { navigator.push(MangaScreen(dialog.current.id)) },
                    onDismissRequest = onDismissRequest,
                )
            }
            null -> {}
        }

        LaunchedEffect(state.list) {
            if (state.list != null) {
                (context as? MainActivity)?.ready = true
            }
        }

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { e ->
                when (e) {
                    HistoryScreenModel.Event.InternalError ->
                        snackbarHostState.showSnackbar(context.stringResource(MR.strings.internal_error))
                    HistoryScreenModel.Event.HistoryCleared ->
                        snackbarHostState.showSnackbar(context.stringResource(MR.strings.clear_history_completed))
                    is HistoryScreenModel.Event.OpenChapter -> openChapter(context, e.chapter)
                }
            }
        }

        LaunchedEffect(Unit) {
            resumeLastChapterReadEvent.receiveAsFlow().collectLatest {
                openChapter(context, screenModel.getNextChapter())
            }
        }
    }

    private suspend fun openChapter(context: Context, chapter: Chapter?) {
        if (chapter != null) {
            val intent = ReaderActivity.newIntent(context, chapter.mangaId, chapter.id)
            context.startActivity(intent)
        } else {
            snackbarHostState.showSnackbar(context.stringResource(MR.strings.no_next_chapter))
        }
    }
}
