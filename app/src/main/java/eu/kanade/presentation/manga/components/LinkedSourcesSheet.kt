package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import tachiyomi.domain.source.linked.model.LinkedSourceGroup
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.TextButton
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun LinkedSourcesSheet(
    onDismissRequest: () -> Unit,
    onSearchSourcesClick: () -> Unit,
    onJoinGroupClick: () -> Unit,
    onAddSourceClick: () -> Unit,
    onManageGroupClick: () -> Unit,
    linkedGroup: LinkedSourceGroup?,
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
                Text(
                    text = "${linkedGroup.name} (${linkedGroup.memberCount} sources)",
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .padding(top = 8.dp, bottom = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                TextButton(
                    onClick = onAddSourceClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Add Source")
                }

                TextButton(
                    onClick = onManageGroupClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Manage Group")
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
