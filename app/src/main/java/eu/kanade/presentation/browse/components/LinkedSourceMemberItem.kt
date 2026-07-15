package eu.kanade.presentation.browse.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.tachiyomi.ui.manga.LinkedMember
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.util.secondaryItemAlpha
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val MemberItemHeight = 104.dp

@Composable
fun LinkedSourceMemberItem(
    member: LinkedMember,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val manga = member.manga
    val sourceManager: SourceManager = remember { Injekt.get() }
    val sourceName = remember(manga.source) {
        sourceManager.getOrStub(manga.source).name
    }

    Surface(
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(MemberItemHeight)
                .padding(
                    horizontal = MaterialTheme.padding.medium,
                    vertical = MaterialTheme.padding.small,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MangaCover.Book(
                modifier = Modifier.fillMaxHeight(),
                data = manga,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = MaterialTheme.padding.medium),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = manga.title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )

                Text(
                    text = sourceName,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.secondaryItemAlpha(),
                )

                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (member.lastRead != null) {
                        Text(
                            text = "Read: ${decimalFormat.format(member.lastRead)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (member.latestChapter != null) {
                        Text(
                            text = "Latest: ${decimalFormat.format(member.latestChapter)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
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
}

private val decimalFormat = DecimalFormat("#.###").apply {
    decimalFormatSymbols = DecimalFormatSymbols.getInstance(Locale.US)
}
