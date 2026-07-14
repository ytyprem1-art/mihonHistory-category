package eu.kanade.presentation.browse

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.animateItemFastScroll
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.linked.model.LinkedSourceGroup
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun LinkedSourceDetailsScreen(
    group: LinkedSourceGroup?,
    members: List<Manga>,
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
                key = { it.id },
            ) { manga ->
                LinkedSourceMemberItem(
                    modifier = Modifier.animateItemFastScroll(),
                    manga = manga,
                )
            }
        }
    }
}

@Composable
private fun LinkedSourceMemberItem(
    manga: Manga,
    modifier: Modifier = Modifier,
) {
    val sourceManager: SourceManager = remember { Injekt.get() }
    val sourceName = remember(manga.source) {
        sourceManager.getOrStub(manga.source).name
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = 8.dp),
    ) {
        Text(
            text = manga.title,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = sourceName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
