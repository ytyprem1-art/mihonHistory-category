package eu.kanade.presentation.manga.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import eu.kanade.domain.source.interactor.GetEnabledSources
import eu.kanade.presentation.browse.components.QuickSourceSwitcherDialog
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun MangaSmartSourceJump(
    mangaTitle: String,
    currentSourceId: Long,
    onOpenSource: (Long, String) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var showSourcePicker by remember { mutableStateOf(false) }
    val getEnabledSources: GetEnabledSources = remember { Injekt.get() }
    val sources by getEnabledSources.subscribe().collectAsState(emptyList())

    FloatingActionButton(
        onClick = { showSourcePicker = true },
        modifier = modifier,
    ) {
        Icon(
            imageVector = Icons.Default.Bolt,
            contentDescription = "Smart source jump",
        )
    }

    if (showSourcePicker) {
        QuickSourceSwitcherDialog(
            onDismissRequest = { showSourcePicker = false },
            sources = sources,
            currentSourceId = currentSourceId,
            excludeCurrentSource = false,
            title = "Smart Source Jump",
            onSourceSelected = { source ->
                if (source.isStub) {
                    scope.launch {
                        snackbarHostState.showSnackbar("Source not installed")
                    }
                } else {
                    onOpenSource(source.id, mangaTitle)
                }
            },
        )
    }
}
