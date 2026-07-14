package eu.kanade.presentation.manga.components

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.linked.model.LinkedSourceGroup
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.TextButton
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun LinkedSourcesSheet(
    onDismissRequest: () -> Unit,
    onSearchSourcesClick: () -> Unit,
    onJoinGroupClick: () -> Unit,
    onAddSourceClick: () -> Unit,
    onMemberOpenClick: (Manga) -> Unit,
    onMemberRemoveClick: (Manga) -> Unit,
    linkedGroup: LinkedSourceGroup?,
    linkedMembers: List<Manga>,
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
                            onRefresh = { /* TODO */ },
                            onOpen = { onMemberOpenClick(member) },
                            onRemove = { onMemberRemoveClick(member) },
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
        HeaderCell(text = "Source", width = 120.dp)
        HeaderCell(text = "Read", width = 60.dp)
        HeaderCell(text = "Latest", width = 60.dp)
        HeaderCell(text = "Last Check", width = 100.dp)
        HeaderCell(text = "Status", width = 80.dp)
        HeaderCell(text = "Actions", width = 120.dp)
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
    member: Manga,
    onRefresh: () -> Unit,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val sourceManager: SourceManager = remember { Injekt.get() }
    val sourceName = remember(member.source) {
        sourceManager.getOrStub(member.source).name
    }

    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MangaCover.Book(
            data = member,
            modifier = Modifier.size(height = 48.dp, width = 32.dp),
        )

        Spacer(modifier = Modifier.width(8.dp))

        TextCell(text = sourceName, width = 120.dp)
        TextCell(text = "-", width = 60.dp)
        TextCell(text = "-", width = 60.dp)
        TextCell(text = "N/A", width = 100.dp)
        TextCell(text = "Unknown", width = 80.dp)

        Row(
            modifier = Modifier.width(120.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
            }
            IconButton(onClick = onOpen) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "Open")
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
    )
}
