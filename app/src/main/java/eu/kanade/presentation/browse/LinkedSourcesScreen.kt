package eu.kanade.presentation.browse

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.util.animateItemFastScroll
import tachiyomi.domain.source.linked.model.LinkedSourceGroup
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

@Composable
fun LinkedSourcesScreen(
    groups: List<LinkedSourceGroup>,
    onClickCreate: () -> Unit,
    onClickDelete: (LinkedSourceGroup) -> Unit,
    onClickGroup: (LinkedSourceGroup) -> Unit,
    navigateUp: () -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = "Linked Source Groups",
                navigateUp = navigateUp,
                actions = {
                    AppBarActions(
                        listOf(
                            AppBar.Action(
                                title = stringResource(MR.strings.action_add),
                                icon = Icons.Outlined.Add,
                                onClick = onClickCreate,
                            ),
                        ),
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        if (groups.isEmpty()) {
            EmptyScreen(
                stringRes = MR.strings.information_no_recent, // Placeholder
                modifier = Modifier.padding(contentPadding),
            )
            return@Scaffold
        }

        FastScrollLazyColumn(
            contentPadding = contentPadding,
        ) {
            items(
                items = groups,
                key = { it.id },
            ) { group ->
                LinkedSourceGroupItem(
                    modifier = Modifier.animateItemFastScroll(),
                    group = group,
                    onClick = { onClickGroup(group) },
                    onLongClick = { onClickDelete(group) },
                )
            }
        }
    }
}

@Composable
private fun LinkedSourceGroupItem(
    group: LinkedSourceGroup,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = MaterialTheme.padding.medium, vertical = 8.dp),
    ) {
        Text(
            text = group.name,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "${group.memberCount} linked sources",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
