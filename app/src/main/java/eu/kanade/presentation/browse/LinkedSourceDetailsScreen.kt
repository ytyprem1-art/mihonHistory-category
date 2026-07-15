package eu.kanade.presentation.browse

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.presentation.browse.components.LinkedSourceMemberItem
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.tachiyomi.ui.manga.LinkedMember
import tachiyomi.domain.source.linked.model.LinkedSourceGroup
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold

@Composable
fun LinkedSourceDetailsScreen(
    group: LinkedSourceGroup?,
    members: List<LinkedMember>,
    onClickMember: (LinkedMember) -> Unit,
    navigateUp: () -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = group?.name ?: "",
                navigateUp = navigateUp,
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
                    onClick = { onClickMember(member) },
                )
            }
        }
    }
}
