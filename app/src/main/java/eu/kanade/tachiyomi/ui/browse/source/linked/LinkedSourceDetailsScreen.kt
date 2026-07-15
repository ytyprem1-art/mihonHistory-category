package eu.kanade.tachiyomi.ui.browse.source.linked

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.LinkedSourceDetailsScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.manga.LinkedMember
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class LinkedSourceDetailsScreen(private val groupId: Long) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val screenModel = rememberScreenModel { LinkedSourceDetailsScreenModel(groupId) }
        val state by screenModel.state.collectAsState()

        var memberToDelete by remember { mutableStateOf<LinkedMember?>(null) }

        LinkedSourceDetailsScreen(
            group = state.group,
            members = state.members,
            refreshingIds = state.refreshingIds,
            onClickMember = { navigator.push(MangaScreen(it.manga.id)) },
            onClickAdd = {
                state.group?.let {
                    navigator.push(LinkedSourceSearchScreen(it.id, it.name))
                }
            },
            onClickCreateHistoryGroup = screenModel::createHistoryGroup,
            onClickSetTrackingSource = screenModel::showTrackingSourcePicker,
            onClickResumeTracking = screenModel::resumeTracking,
            onRefreshMember = { screenModel.refreshMember(it.manga.id) },
            onDeleteMember = { memberToDelete = it },
            navigateUp = navigator::pop,
        )

        memberToDelete?.let { member ->
            val sourceManager = remember { Injekt.get<SourceManager>() }
            val sourceName = remember(member.manga.source) {
                sourceManager.getOrStub(member.manga.source).name
            }
            AlertDialog(
                onDismissRequest = { memberToDelete = null },
                title = { Text("Remove Source") },
                text = {
                    Text("Remove '${member.manga.title}' from '$sourceName' group?")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            screenModel.removeMember(member.manga.id, member.manga.source)
                            memberToDelete = null
                        },
                    ) {
                        Text(stringResource(MR.strings.action_remove))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { memberToDelete = null }) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }

        when (val dialog = state.dialog) {
            is LinkedSourceDetailsScreenModel.Dialog.CreateHistoryGroupWarning -> {
                val isEligible = dialog.eligible.size >= 2
                val sourceManager = remember { Injekt.get<SourceManager>() }

                AlertDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    title = { Text("Some sources will be skipped") },
                    text = {
                        androidx.compose.foundation.layout.Column {
                            if (dialog.withoutHistory.isNotEmpty()) {
                                Text(
                                    text = "No read history:",
                                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = dialog.withoutHistory.joinToString(", ") {
                                        sourceManager.get(it.manga.source)?.name ?: "Unknown source"
                                    },
                                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                            if (dialog.inOtherGroup.isNotEmpty()) {
                                Text(
                                    text = "Already in another History Group:",
                                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = dialog.inOtherGroup.joinToString(", ") {
                                        sourceManager.get(it.manga.source)?.name ?: "Unknown source"
                                    },
                                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                            Text(
                                text = if (isEligible) {
                                    "Create history group using the remaining ${dialog.eligible.size} sources?"
                                } else {
                                    "At least two eligible sources are required to create a History Group."
                                },
                                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (isEligible) {
                                    screenModel.performCreateHistoryGroup(state.group?.name ?: "", dialog.eligible.map { it.manga.id })
                                } else {
                                    screenModel.dismissDialog()
                                }
                            },
                        ) {
                            Text(stringResource(if (isEligible) MR.strings.action_ok else MR.strings.action_ok))
                        }
                    },
                    dismissButton = if (isEligible) {
                        {
                            TextButton(onClick = screenModel::dismissDialog) {
                                Text(stringResource(MR.strings.action_cancel))
                            }
                        }
                    } else null,
                )
            }
            is LinkedSourceDetailsScreenModel.Dialog.TrackingSourcePicker -> {
                val sourceManager = remember { Injekt.get<SourceManager>() }
                AlertDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    title = { Text("Set tracking source") },
                    text = {
                        androidx.compose.foundation.lazy.LazyColumn {
                            items(dialog.members) { member ->
                                androidx.compose.material3.ListItem(
                                    modifier = Modifier.clickable {
                                        screenModel.updateTrackingSource(member.manga.id)
                                    },
                                    headlineContent = { Text(member.manga.title) },
                                    supportingContent = {
                                        Text(sourceManager.getOrStub(member.manga.source).name)
                                    },
                                    trailingContent = {
                                        if (state.group?.trackingMangaId == member.manga.id) {
                                            Text(
                                                text = "Tracking",
                                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                                color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = screenModel::dismissDialog) {
                            Text(stringResource(MR.strings.action_cancel))
                        }
                    },
                )
            }
            null -> {}
        }

        LaunchedEffect(Unit) {
            screenModel.events.collect { event: LinkedSourceDetailsScreenModel.Event ->
                when (event) {
                    LinkedSourceDetailsScreenModel.Event.HistoryGroupCreated -> {
                        context.toast("History group created")
                    }
                    is LinkedSourceDetailsScreenModel.Event.ShowMessage -> {
                        context.toast(event.message)
                    }
                }
            }
        }
    }
}
