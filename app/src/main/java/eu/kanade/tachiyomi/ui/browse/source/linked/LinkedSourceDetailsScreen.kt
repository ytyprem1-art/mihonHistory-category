package eu.kanade.tachiyomi.ui.browse.source.linked

import androidx.compose.material3.AlertDialog
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
import eu.kanade.presentation.browse.LinkedSourceDetailsScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.manga.LinkedMember
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class LinkedSourceDetailsScreen(private val groupId: Long) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
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
    }
}
