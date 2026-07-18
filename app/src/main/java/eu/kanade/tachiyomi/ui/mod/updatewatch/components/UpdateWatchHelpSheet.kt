package eu.kanade.tachiyomi.ui.mod.updatewatch.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.tachiyomi.ui.mod.updatewatch.helper.UpdateWatchRefreshHelper
import tachiyomi.presentation.core.components.material.padding

@Composable
fun UpdateWatchHelpSheet(
    onDismissRequest: () -> Unit,
) {
    var page by remember { mutableIntStateOf(1) }

    AdaptiveSheet(
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = MaterialTheme.padding.medium)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.padding.medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (page == 2) {
                    IconButton(onClick = { page = 1 }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                }

                Text(
                    text = if (page == 1) "How Auto Refresh Works" else "Check Priority & Limits",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = MaterialTheme.padding.small))

            if (page == 1) {
                PageOne(onNext = { page = 2 })
            } else {
                PageTwo()
            }
        }
    }
}

@Composable
private fun PageOne(onNext: () -> Unit) {
    Column(
        modifier = Modifier.padding(MaterialTheme.padding.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium)
    ) {
        Text(
            text = "Auto Refresh checks tracked manga around the time they are expected to update.",
            style = MaterialTheme.typography.bodyLarge
        )

        BulletItem("Upcoming manga are not checked yet.")
        BulletItem("Manga near their expected update day are checked more often.")
        BulletItem("If no update is found, checks gradually slow down.")
        BulletItem("Weekly manga enter a faster checking window again during the next weekly cycle.")
        BulletItem("Rapid-update series use a faster schedule.")
        BulletItem("Older inactive series are checked less often.")
        BulletItem("You may enable Auto Refresh for as many manga as you want.")
        BulletItem("Some manga may wait for a later background run when many titles from one source are due together.")

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Auto Refresh does not guarantee an update will be found immediately. Sources may be delayed, unavailable, or temporarily block requests.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(MaterialTheme.padding.medium)
            )
        }

        Text(
            text = "Example: A weekly manga expected on Monday is checked more often on Monday and Tuesday. If it is still late, checks slow down until the next weekly window.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            onClick = onNext,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(MaterialTheme.padding.medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        color = MaterialTheme.colorScheme.error,
                        shape = MaterialTheme.shapes.extraSmall,
                    ) {
                        Text(
                            text = "MUST READ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "Important: How Check Priority & Limits Work",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        text = "Learn why some manga are checked first and why others may wait.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun PageTwo() {
    Column(
        modifier = Modifier.padding(MaterialTheme.padding.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium)
    ) {
        Text(
            text = "To keep background activity light and avoid being blocked by sources, the following limits are applied per background run:",
            style = MaterialTheme.typography.bodyLarge
        )

        PriorityBucketItem(
            name = "HOT",
            description = "Manga inside the most likely update window. Checked first and more often.",
            limit = UpdateWatchRefreshHelper.CAP_HOT
        )
        PriorityBucketItem(
            name = "WARM",
            description = "Manga slightly past the expected update window. Checked after HOT manga.",
            limit = UpdateWatchRefreshHelper.CAP_WARM
        )
        PriorityBucketItem(
            name = "COLD",
            description = "Manga still waiting for an update. Checked less often.",
            limit = UpdateWatchRefreshHelper.CAP_COLD
        )
        PriorityBucketItem(
            name = "STALE",
            description = "Manga with no update for a long time. Lowest queue priority. Reaching a stale age does not disable Auto Refresh automatically.",
            limit = UpdateWatchRefreshHelper.CAP_STALE
        )

        HorizontalDivider()

        Text(
            text = "Source Limits",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        BulletItem("Maximum per source: ${UpdateWatchRefreshHelper.CAP_TOTAL} manga per run")
        BulletItem("Maximum active sources: ${UpdateWatchRefreshHelper.GLOBAL_CONCURRENCY} at the same time")

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "These limits are not a limit on how many manga you may track. Manga that do not fit in the current run remain eligible and will be reconsidered later.",
            style = MaterialTheme.typography.bodyMedium
        )

        Text(
            text = "Limits reduce rate limiting, temporary blocks, and protection errors. HOT manga are always prioritized before WARM, COLD, and STALE manga.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BulletItem(text: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(text = "•", modifier = Modifier.padding(end = 8.dp))
        Text(text = text, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PriorityBucketItem(name: String, description: String, limit: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Text(
                    text = "Max $limit",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Text(text = description, style = MaterialTheme.typography.bodyMedium)
    }
}
