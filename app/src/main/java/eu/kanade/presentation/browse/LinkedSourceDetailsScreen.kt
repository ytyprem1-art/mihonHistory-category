package eu.kanade.presentation.browse

import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import eu.kanade.presentation.browse.components.LinkedSourceMemberItem
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.tachiyomi.ui.manga.LinkedMember
import tachiyomi.domain.source.linked.model.LinkedSourceGroup
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun LinkedSourceDetailsScreen(
    group: LinkedSourceGroup?,
    members: List<LinkedMember>,
    refreshingIds: Set<Long>,
    onClickMember: (LinkedMember) -> Unit,
    onClickAdd: () -> Unit,
    onClickCreateHistoryGroup: () -> Unit,
    onRefreshMember: (LinkedMember) -> Unit,
    onDeleteMember: (LinkedMember) -> Unit,
    navigateUp: () -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = group?.name ?: "",
                navigateUp = navigateUp,
                actions = {
                    AppBarActions(
                        listOf(
                            AppBar.Action(
                                title = stringResource(MR.strings.action_add),
                                icon = Icons.Outlined.Add,
                                onClick = onClickAdd,
                            ),
                            AppBar.OverflowAction(
                                title = "Create history group",
                                onClick = onClickCreateHistoryGroup,
                            ),
                        ),
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        FastScrollLazyColumn(
            contentPadding = contentPadding,
        ) {
            items(
                items = members,
                key = { it.manga.id },
            ) { member ->
                LinkedSourceMemberItem(
                    modifier = Modifier.animateItemFastScroll(),
                    member = member,
                    isRefreshing = refreshingIds.contains(member.manga.id),
                    onRefresh = { onRefreshMember(member) },
                    onDelete = { onDeleteMember(member) },
                    onClick = { onClickMember(member) },
                )
            }
        }
    }
}
