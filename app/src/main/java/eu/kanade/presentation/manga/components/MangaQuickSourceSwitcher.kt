package eu.kanade.presentation.manga.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import eu.kanade.domain.source.interactor.GetEnabledSources
import eu.kanade.presentation.browse.components.QuickSourceSwitcherDialog
import tachiyomi.domain.source.interactor.GetRemoteManga
import tachiyomi.domain.source.model.Source
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun MangaQuickSourceSwitcher(
    currentSourceId: Long,
    onOpenSource: (Long, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSourceSwitcher by remember { mutableStateOf(false) }
    val getEnabledSources: GetEnabledSources = remember { Injekt.get() }
    val sources by getEnabledSources.subscribe().collectAsState(emptyList())

    FloatingActionButton(
        onClick = { showSourceSwitcher = true },
        modifier = modifier,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.CompareArrows,
            contentDescription = "Switch Source",
        )
    }

    if (showSourceSwitcher) {
        QuickSourceSwitcherDialog(
            onDismissRequest = { showSourceSwitcher = false },
            sources = sources,
            currentSourceId = currentSourceId,
            excludeCurrentSource = false,
            onSourceSelected = { source ->
                val query = if (source.supportsLatest) {
                    GetRemoteManga.QUERY_LATEST
                } else {
                    GetRemoteManga.QUERY_POPULAR
                }
                onOpenSource(source.id, query)
            },
        )
    }
}
