package com.loantracker.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.loantracker.app.services.UpdateController
import com.loantracker.app.services.UpdateController.State

private fun sizeText(bytes: Long): String =
    if (bytes > 0) " (%.1f MB)".format(bytes / 1_048_576.0) else ""

/** Shows the update flow (available, downloading, install, error). Host it once, near the app root. */
@Composable
fun UpdateDialogs(controller: UpdateController) {
    val context = LocalContext.current
    when (val s = controller.state) {
        is State.Available -> AlertDialog(
            onDismissRequest = { controller.dismiss() },
            title = { Text("Update available") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Version ${s.info.version}${sizeText(s.info.sizeBytes)} is ready. " +
                            "You have ${controller.currentVersion}.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (s.info.notes.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "What's new",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            s.info.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { controller.skip(s.info) }) {
                        Text("Skip this version")
                    }
                }
            },
            confirmButton = {
                Button(onClick = { controller.startDownload(s.info) }) { Text("Update now") }
            },
            dismissButton = {
                TextButton(onClick = { controller.dismiss() }) { Text("Later") }
            }
        )

        is State.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Downloading update") },
            text = {
                Column {
                    LinearProgressIndicator(
                        progress = { s.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${(s.progress * 100).toInt()}%  ·  version ${s.info.version}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { controller.cancel() }) { Text("Cancel") }
            }
        )

        is State.Ready -> {
            // Open the installer once per downloaded file; the button covers the permission detour.
            LaunchedEffect(s.file.path) { controller.install(context, s.file) }
            AlertDialog(
                onDismissRequest = { controller.dismiss() },
                title = { Text("Ready to install") },
                text = {
                    Text(
                        "Version ${s.info.version} is downloaded. If Android asks, allow installs " +
                            "from Loan Tracker, then come back and tap Install.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(onClick = { controller.install(context, s.file) }) { Text("Install") }
                },
                dismissButton = {
                    TextButton(onClick = { controller.dismiss() }) { Text("Later") }
                }
            )
        }

        is State.Failed -> AlertDialog(
            onDismissRequest = { controller.dismiss() },
            title = { Text("Update problem") },
            text = { Text(s.message, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                if (s.info != null) {
                    Button(onClick = { controller.startDownload(s.info) }) { Text("Retry") }
                } else {
                    Button(onClick = { controller.dismiss() }) { Text("OK") }
                }
            },
            dismissButton = {
                if (s.info != null) {
                    TextButton(onClick = { controller.dismiss() }) { Text("Close") }
                }
            }
        )

        else -> Unit
    }
}

/** Settings card: current version, "Check now", and the latest status. */
@Composable
fun UpdateSettingsCard(controller: UpdateController) {
    LaunchedEffect(Unit) { controller.clearStatus() }
    val s = controller.state
    val status = when (s) {
        is State.Checking -> "Checking GitHub releases..."
        is State.UpToDate -> "You're on the latest version (${controller.currentVersion})."
        is State.Available -> "Version ${s.info.version} is available."
        is State.Downloading -> "Downloading version ${s.info.version}..."
        else -> "Version ${controller.currentVersion}. Checked each time you open the app."
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Software update",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (s is State.Checking || s is State.Downloading) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                FilledTonalButton(onClick = { controller.checkNow() }) { Text("Check now") }
            }
        }
    }
}
