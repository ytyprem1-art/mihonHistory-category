package eu.kanade.presentation.browse

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.browse.components.LinkedSourceMemberItem
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.tachiyomi.ui.manga.LinkedMember
import tachiyomi.domain.source.linked.model.LinkedSourceGroup
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun LinkedSourceDetailsScreen(
    group: LinkedSourceGroup?,
    members: List<LinkedMember>,
    refreshingIds: Set<Long>,
    onClickMember: (LinkedMember) -> Unit,
    onClickAdd: () -> Unit,
    onClickCreateHistoryGroup: () -> Unit,
    onClickSetTrackingSource: () -> Unit,
    onClickResumeTracking: () -> Unit,
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
                        buildList {
                            add(
                                AppBar.Action(
                                    title = stringResource(MR.strings.action_add),
                                    icon = Icons.Outlined.Add,
                                    onClick = onClickAdd,
                                )
                            )
                            add(
                                AppBar.OverflowAction(
                                    title = "Set tracking source",
                                    onClick = onClickSetTrackingSource,
                                )
                            )
                            if (group?.isPaused == true) {
                                add(
                                    AppBar.OverflowAction(
                                        title = "Resume tracking",
                                        onClick = onClickResumeTracking,
                                    )
                                )
                            }
                            add(
                                AppBar.OverflowAction(
                                    title = "Create history group",
                                    onClick = onClickCreateHistoryGroup,
                                )
                            )
                        },
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        val sourceManager: SourceManager = remember { Injekt.get() }
        FastScrollLazyColumn(
            contentPadding = contentPadding,
        ) {
            if (group?.trackingMangaId != null) {
                item {
                    val trackingMember = members.find { it.manga.id == group.trackingMangaId }
                    val sourceName = trackingMember?.let { sourceManager.getOrStub(it.manga.source).name } ?: "Unknown"
                    val statusText = if (group.isPaused) " (Paused)" else " · Every 7 days"

                    androidx.compose.material3.Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ) {
                        Text(
                            text = "Tracking: $sourceName$statusText",
                            modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(
                items = members,
                key = { it.manga.id },
            ) { member ->
                LinkedSourceMemberItem(
                    modifier = Modifier.animateItemFastScroll(),
                    member = member,
                    isRefreshing = refreshingIds.contains(member.manga.id),
                    isTracking = group?.trackingMangaId == member.manga.id,
                    onRefresh = { onRefreshMember(member) },
                    onDelete = { onDeleteMember(member) },
                    onClick = { onClickMember(member) },
                )
            }
        }
    }
}
