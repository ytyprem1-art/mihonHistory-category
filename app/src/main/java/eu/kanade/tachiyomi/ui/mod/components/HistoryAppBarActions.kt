package eu.kanade.tachiyomi.ui.mod.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions

@Composable
fun HistoryAppBarActions(
    actions: List<AppBar.AppBarAction>,
    isSearchActive: Boolean,
    isTabletUi: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
) {
    if (!isSearchActive || isTabletUi) {
        AppBarActions(actions)
        return
    }

    IconButton(onClick = onToggleExpand) {
        Icon(
            imageVector = if (isExpanded) Icons.Outlined.ChevronRight else Icons.Outlined.ChevronLeft,
            contentDescription = if (isExpanded) "Collapse actions" else "Expand actions",
        )
    }
}

@Composable
fun HistorySearchExpandedActions(
    actions: List<AppBar.AppBarAction>,
    isVisible: Boolean,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppBarActions(actions)
        }
    }
}
