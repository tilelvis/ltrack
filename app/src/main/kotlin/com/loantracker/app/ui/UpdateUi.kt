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

/** Shows the update flow (available, downloading, install, error). Host it once, near the app root. */
@Composable
fun UpdateDialogs(controller: UpdateController) {
    val context = LocalContext.current
    val s = controller.state
    val info = when (s) {
        is State.Available -> s.info
        is State.Downloading -> s.info
        is State.Ready -> s.info
        is State.Failed -> s.info
        else -> null
    }
    // Open the installer once per downloaded file; the card's button covers the permission detour.
    if (s is State.Ready) LaunchedEffect(s.file.path) { controller.install(context, s.file) }

    if (info != null) {
        UpdateCardDialog(
            controller = controller,
            state = s,
            info = info,
            onInstall = { (s as? State.Ready)?.let { controller.install(context, it.file) } }
        )
    } else if (s is State.Failed) {
        AlertDialog(
            onDismissRequest = { controller.dismiss() },
            title = { Text("Update problem") },
            text = { Text(s.message, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { Button(onClick = { controller.dismiss() }) { Text("OK") } }
        )
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
