package eu.kanade.tachiyomi.ui.browse.source.linked

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.LinkedSourcesScreen
import eu.kanade.presentation.util.Screen
import tachiyomi.domain.source.linked.model.LinkedSourceGroup
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

class LinkedSourcesScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { LinkedSourcesScreenModel() }
        val state by screenModel.state.collectAsState()

        var showCreateDialog by remember { mutableStateOf(false) }
        var groupToRename by remember { mutableStateOf<LinkedSourceGroup?>(null) }
        var groupToDelete by remember { mutableStateOf<LinkedSourceGroup?>(null) }

        LinkedSourcesScreen(
            groups = state.groups,
            searchQuery = state.searchQuery,
            onSearchQueryChange = screenModel::updateSearchQuery,
            sortMode = screenModel.sortMode,
            onSortModeChange = screenModel::setSortMode,
            onClickCreate = { showCreateDialog = true },
            onClickRename = { groupToRename = it },
            onClickDelete = { groupToDelete = it },
            onClickGroup = { navigator.push(LinkedSourceDetailsScreen(it.id)) },
            navigateUp = navigator::pop,
        )

        if (showCreateDialog) {
            var name by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text("New Group") },
                text = {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Group Name") },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (name.isNotBlank()) {
                                screenModel.createGroup(name)
                                showCreateDialog = false
                            }
                        },
                    ) {
                        Text(stringResource(MR.strings.action_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }

        groupToRename?.let { group ->
            var name by remember { mutableStateOf(group.name) }
            AlertDialog(
                onDismissRequest = { groupToRename = null },
                title = { Text("Rename Group") },
                text = {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Group Name") },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (name.isNotBlank()) {
                                screenModel.renameGroup(group.id, name)
                                groupToRename = null
                            }
                        },
                    ) {
                        Text(stringResource(MR.strings.action_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { groupToRename = null }) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }

        groupToDelete?.let { group ->
            AlertDialog(
                onDismissRequest = { groupToDelete = null },
                title = { Text("Delete Group") },
                text = { Text("Are you sure you want to delete '${group.name}'?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            screenModel.deleteGroup(group.id)
                            groupToDelete = null
                        },
                    ) {
                        Text(stringResource(MR.strings.action_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { groupToDelete = null }) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }
    }
}
