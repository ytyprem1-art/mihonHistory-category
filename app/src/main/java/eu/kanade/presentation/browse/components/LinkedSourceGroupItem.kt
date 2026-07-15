package eu.kanade.presentation.browse.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.manga.components.MangaCover
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.linked.model.LinkedSourceGroup
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.util.secondaryItemAlpha

@Composable
fun LinkedSourceGroupItem(
    group: LinkedSourceGroup,
    representativeManga: Manga?,
    sourceNames: List<String>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(104.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = MaterialTheme.padding.medium, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MangaCover.Book(
            modifier = Modifier.fillMaxHeight(),
            data = representativeManga,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = MaterialTheme.padding.medium),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = "${group.memberCount} sources",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.secondaryItemAlpha(),
            )

            if (sourceNames.isNotEmpty()) {
                Text(
                    text = sourceNames.joinToString(", "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
