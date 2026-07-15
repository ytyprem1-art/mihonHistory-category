package eu.kanade.presentation.manga.components

import android.text.format.DateUtils
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.tachiyomi.ui.manga.LinkedMember
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.linked.model.LinkedSourceGroup
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.TextButton
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.util.concurrent.TimeUnit

@Composable
fun LinkedSourcesSheet(
    onDismissRequest: () -> Unit,
    onSearchSourcesClick: () -> Unit,
    onJoinGroupClick: () -> Unit,
    onAddSourceClick: () -> Unit,
    onMemberOpenClick: (Manga) -> Unit,
    onMemberReadClick: (mangaId: Long, chapterId: Long) -> Unit,
    onMemberLatestClick: (mangaId: Long, chapterId: Long) -> Unit,
    onMemberRemoveClick: (Manga) -> Unit,
    onMemberRefreshClick: (Manga) -> Unit,
    linkedGroup: LinkedSourceGroup?,
    linkedMembers: List<LinkedMember>,
    currentMangaId: Long,
    refreshingIds: Set<Long>,
) {
    AdaptiveSheet(
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
        ) {
            Text(
                text = "Linked Sources",
                modifier = Modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.titleLarge,
            )

            if (linkedGroup == null) {
                Text(
                    text = "This manga is not linked yet.",
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .padding(top = 8.dp, bottom = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                TextButton(
                    onClick = onSearchSourcesClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Search Sources")
                }

                TextButton(
                    onClick = onJoinGroupClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Join Existing Group")
                }
            } else {
                GroupHeader(
                    group = linkedGroup,
                    onRenameGroup = { /* TODO */ },
                    onDeleteGroup = { /* TODO */ },
                )

                Spacer(modifier = Modifier.height(8.dp))

                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState),
                ) {
                    MembersTableHeader()
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    for (member in linkedMembers) {
                        MemberTableRow(
                            member = member,
                            isRefreshing = refreshingIds.contains(member.manga.id),
                            currentMangaId = currentMangaId,
                            onRefresh = { onMemberRefreshClick(member.manga) },
                            onOpen = { onMemberOpenClick(member.manga) },
                            onRead = { onMemberReadClick(member.manga.id, it) },
                            onLatest = { onMemberLatestClick(member.manga.id, it) },
                            onRemove = { onMemberRemoveClick(member.manga) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = onAddSourceClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "+ Add Source")
                }
            }

            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        }
    }
}

@Composable
private fun GroupHeader(
    group: LinkedSourceGroup,
    onRenameGroup: () -> Unit,
    onDeleteGroup: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = group.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        IconButton(onClick = { showMenu = true }) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = "Group actions",
            )

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Rename Group") },
                    onClick = {
                        showMenu = false
                        onRenameGroup()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Delete Group") },
                    onClick = {
                        showMenu = false
                        onDeleteGroup()
                    },
                )
            }
        }
    }
}

@Composable
private fun MembersTableHeader() {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(40.dp)) // Cover
        HeaderCell(text = "Source", width = 140.dp)
        HeaderCell(text = "Read", width = 90.dp)
        HeaderCell(text = "Latest", width = 80.dp)
        HeaderCell(text = "Status", width = 150.dp)
        HeaderCell(text = "Last Check", width = 140.dp)
        Spacer(modifier = Modifier.width(144.dp)) // Actions (Refresh, Open, Remove)
    }
}

@Composable
private fun HeaderCell(text: String, width: androidx.compose.ui.unit.Dp) {
    Text(
        text = text,
        modifier = Modifier.width(width),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun MemberTableRow(
    member: LinkedMember,
    isRefreshing: Boolean,
    currentMangaId: Long,
    onRefresh: () -> Unit,
    onOpen: () -> Unit,
    onRead: (Long) -> Unit,
    onLatest: (Long) -> Unit,
    onRemove: () -> Unit,
) {
    val manga = member.manga
    val sourceManager: SourceManager = remember { Injekt.get() }
    val sourceName = remember(manga.source) {
        sourceManager.getOrStub(manga.source).name
    }
    val isCurrentManga = manga.id == currentMangaId

    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .height(60.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .width(188.dp) // 32 (cover) + 8 (spacer) + 140 (source) + 8 (trailing spacer)
                .then(
                    if (isCurrentManga) {
                        Modifier
                    } else {
                        Modifier.clickable { onOpen() }
                    }
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MangaCover.Book(
                data = manga,
                modifier = Modifier.size(height = 48.dp, width = 32.dp),
            )

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                modifier = Modifier.width(140.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = sourceName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
                if (isCurrentManga) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "(Current)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                } else {
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Read (2 lines)
        val isReadClickable = member.lastReadChapterId != null
        Column(
            modifier = Modifier
                .width(90.dp)
                .then(
                    if (member.lastReadChapterId != null) {
                        Modifier.clickable { onRead(member.lastReadChapterId) }
                    } else {
                        Modifier
                    }
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = member.lastRead?.let { decimalFormat.format(it) } ?: "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isReadClickable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (isReadClickable) {
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = member.readAt?.let { formatCompactRelativeTime(it) } ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }

        // Latest (2 lines)
        val isLatestClickable = member.latestChapterId != null
        Column(
            modifier = Modifier
                .width(80.dp)
                .then(
                    if (member.latestChapterId != null) {
                        Modifier.clickable { onLatest(member.latestChapterId) }
                    } else {
                        Modifier
                    }
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = member.latestChapter?.let { decimalFormat.format(it) } ?: "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isLatestClickable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (isLatestClickable) {
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = if ((member.latestChapter != null && member.lastRead != null && member.latestChapter > member.lastRead)) "NEW" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }

        // Status
        val statusText = when {
            member.latestChapter != null && member.lastRead != null && member.latestChapter > member.lastRead ->
                "+${decimalFormat.format(member.latestChapter - member.lastRead)} from last read"
            member.latestChapter != null && member.lastRead != null && member.latestChapter == member.lastRead ->
                "You're up to date"
            else -> ""
        }
        TextCell(text = statusText, width = 150.dp)

        // Last Check
        val lastCheckText = if (manga.lastUpdate > 0L) {
            "Refreshed ${formatCompactRelativeTime(manga.lastUpdate)}"
        } else {
            "Never refreshed"
        }
        TextCell(text = lastCheckText, width = 140.dp)

        // Actions
        Row(
            modifier = Modifier.width(144.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "infinite")
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "rotation",
            )

            IconButton(
                onClick = onRefresh,
                enabled = !isRefreshing,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "Refresh",
                    modifier = if (isRefreshing) Modifier.rotate(rotation) else Modifier,
                )
            }

            if (manga.id != currentMangaId) {
                IconButton(onClick = onOpen) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "Open")
                }
            } else {
                Spacer(modifier = Modifier.width(48.dp))
            }

            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Delete, contentDescription = "Remove")
            }
        }
    }
}

@Composable
private fun TextCell(text: String, width: androidx.compose.ui.unit.Dp) {
    Text(
        text = text,
        modifier = Modifier.width(width),
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

@Composable
private fun formatCompactRelativeTime(epochMillis: Long): String {
    val now = Instant.now().toEpochMilli()
    val diff = now - epochMillis

    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "just now"
        diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m ago"
        DateUtils.isToday(epochMillis) -> "today"
        isYesterday(epochMillis) -> "yesterday"
        else -> "${TimeUnit.MILLISECONDS.toDays(diff)}d ago"
    }
}

private fun isYesterday(epochMillis: Long): Boolean {
    return DateUtils.isToday(epochMillis + DateUtils.DAY_IN_MILLIS)
}

private val decimalFormat = java.text.DecimalFormat("#.###").apply {
    decimalFormatSymbols = java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US)
}
