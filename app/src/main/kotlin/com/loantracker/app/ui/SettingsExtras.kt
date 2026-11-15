package com.loantracker.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.loantracker.app.services.BackupService
import androidx.core.app.NotificationManagerCompat
import com.loantracker.app.services.PenaltyPrefs
import com.loantracker.app.services.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Turn the 8:00 am and 5:00 pm reminders on or off, or send yourself a test one. */
@Composable
fun RemindersSettingsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var on by remember { mutableStateOf(ReminderScheduler.enabled(context)) }
    val notificationsOk = NotificationManagerCompat.from(context).areNotificationsEnabled()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Daily reminders", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "8:00 am and 5:00 pm, for as long as you have an active loan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = on, onCheckedChange = { on = it; ReminderScheduler.setEnabled(context, it) })
            }
            if (!notificationsOk) {
                Text(
                    "Notifications are turned off for this app. Turn them on in your phone’s settings to get reminders.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            TextButton(onClick = { scope.launch(Dispatchers.IO) { ReminderScheduler.fire(context, 0, test = true) } }) {
                Text("Send a test reminder")
            }
        }
    }
}

/** Pick the late-fee percentage used by the pay-today card's estimate. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PenaltySettingsCard() {
    val context = LocalContext.current
    var pct by remember { mutableStateOf(PenaltyPrefs.get(context)) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Late fee estimate", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Used on the pay-today card to show what missing the due date could cost. " +
                    "Your lender’s real terms always win.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.0, 3.0, 5.0, 10.0, 15.0, 20.0).forEach { v ->
                    FilterChip(
                        selected = pct == v,
                        onClick = { pct = v; PenaltyPrefs.set(context, v) },
                        label = { Text(if (v == 0.0) "None" else "${v.toInt()}%") }
                    )
                }
            }
        }
    }
}

/** Export everything to a JSON file you choose, or merge a backup file back in. */
@Composable
fun BackupSettingsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            status = BackupService.backup(context, uri).fold(
                onSuccess = { "Saved ${it.loans} loans and ${it.payments} payments." },
                onFailure = { "Backup failed: ${it.message ?: "unknown error"}" }
            )
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            status = BackupService.restore(context, uri).fold(
                onSuccess = {
                    Toast.makeText(context, "Backup restored", Toast.LENGTH_SHORT).show()
                    "Restored ${it.loans} loans and ${it.payments} payments."
                },
                onFailure = { "Restore failed: ${it.message ?: "unknown error"}" }
            )
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Back up your data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Everything stays on this phone. A backup file lets you keep a copy somewhere safe " +
                    "and bring it back after a reinstall. Restoring adds to what’s here; it doesn’t erase anything.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { exporter.launch("loan-tracker-backup-${LocalDate.now()}.json") }) { Text("Back up") }
                FilledTonalButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                    Text("Restore")
                }
            }
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold) }
        }
    }
}
