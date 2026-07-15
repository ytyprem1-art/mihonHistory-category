package eu.kanade.presentation.browse

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.presentation.browse.components.LinkedSourceGroupItem
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.tachiyomi.ui.browse.source.linked.LinkedSourcesScreenModel
import tachiyomi.domain.source.linked.model.LinkedSourceGroup
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

@Composable
fun LinkedSourcesScreen(
    groups: List<LinkedSourcesScreenModel.GroupWithMetadata>,
    searchQuery: String?,
    onSearchQueryChange: (String?) -> Unit,
    onClickCreate: () -> Unit,
    onClickDelete: (LinkedSourceGroup) -> Unit,
    onClickGroup: (LinkedSourceGroup) -> Unit,
    navigateUp: () -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            SearchToolbar(
                searchQuery = searchQuery,
                onChangeSearchQuery = onSearchQueryChange,
                titleContent = { Text("Linked Source Groups") },
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
            val msg = if (!searchQuery.isNullOrBlank()) {
                MR.strings.no_results_found
            } else {
                MR.strings.information_no_recent // Placeholder
            }
            EmptyScreen(
                stringRes = msg,
                modifier = Modifier.padding(contentPadding),
            )
            return@Scaffold
        }

        FastScrollLazyColumn(
            contentPadding = contentPadding,
        ) {
            items(
                items = groups,
                key = { it.group.id },
            ) { item ->
                LinkedSourceGroupItem(
                    modifier = Modifier.animateItemFastScroll(),
                    group = item.group,
                    representativeManga = item.representativeManga,
                    sourceNames = item.sourceNames,
                    onClick = { onClickGroup(item.group) },
                    onLongClick = { onClickDelete(item.group) },
                )
            }
        }
    }
}
