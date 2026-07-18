package eu.kanade.presentation.manga.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun TrackUpdateWatchDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var interval by remember { mutableIntStateOf(7) }
    var customInterval by remember { mutableStateOf("") }
    val isCustom = interval !in listOf(7, 14, 21)

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Track manga") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Expected update interval",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Used to calculate Upcoming, Due today, and Overdue.",
                    style = MaterialTheme.typography.bodySmall,
                )

                listOf(7, 14, 21).forEach { preset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                interval = preset
                                customInterval = ""
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = interval == preset,
                            onClick = null,
                        )
                        Text(
                            text = "$preset days",
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = isCustom,
                        onClick = {
                            if (!isCustom) {
                                interval = customInterval.toIntOrNull() ?: 0
                            }
                        },
                    )
                    OutlinedTextField(
                        value = customInterval,
                        onValueChange = {
                            customInterval = it.filter { c -> c.isDigit() }
                            val newVal = customInterval.toIntOrNull()
                            if (newVal != null && newVal > 0) {
                                interval = newVal
                            } else {
                                interval = 0
                            }
                        },
                        label = { Text("Custom days") },
                        modifier = Modifier.padding(start = 8.dp).weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (interval > 0) {
                        onConfirm(interval)
                        onDismissRequest()
                    }
                },
                enabled = interval > 0,
            ) {
                Text("Track")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}
