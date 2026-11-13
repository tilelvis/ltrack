package com.loantracker.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loantracker.app.services.DetectedPayment
import com.loantracker.app.services.SmsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter

private val DATETIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
private val DATE_ONLY_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM")

private fun formatMoneyScan(amount: Double): String = "KSh %,.0f".format(amount)

/**
 * Scans the SMS inbox automatically when opened. New payments come first and are pre-selected;
 * payments already in the app are tucked into a collapsed section.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsScanScreen(
    loanId: String,
    vm: LoanViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val loan by vm.loan(loanId).collectAsStateWithLifecycle()
    val payments by vm.payments(loanId).collectAsStateWithLifecycle()

    var detections by remember { mutableStateOf<List<DetectedPayment>>(emptyList()) }
    var deselected by remember { mutableStateOf(setOf<String>()) }
    var scanning by remember { mutableStateOf(false) }
    var hasScanned by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    var showImported by rememberSaveable { mutableStateOf(false) }

    // "Already in app" is judged live against the loan's payments, so it stays right after import.
    val importedCodes = remember(payments) { payments.mapNotNull { it.mpesaCode?.uppercase() }.toSet() }
    val fresh = remember(detections, importedCodes) {
        detections.filter { it.mpesa.code.uppercase() !in importedCodes }
    }
    val done = remember(detections, importedCodes) {
        detections.filter { it.mpesa.code.uppercase() in importedCodes }
    }
    val selectedCodes = remember(fresh, deselected) {
        fresh.map { it.mpesa.code }.filter { it !in deselected }.toSet()
    }

    fun doScan() {
        val l = loan ?: return
        scanning = true
        scope.launch {
            val results = withContext(Dispatchers.IO) {
                SmsService.scanForLoan(context, l, emptySet())
            }
            detections = results.distinctBy { it.mpesa.code.uppercase() }
            deselected = emptySet()
            scanning = false
            hasScanned = true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionDenied = false
            doScan()
        } else {
            permissionDenied = true
        }
    }

    // Scan as soon as the loan is loaded (asking for SMS permission first if needed).
    LaunchedEffect(loan?.id) {
        if (loan != null && !hasScanned) {
            if (SmsService.hasPermission(context)) doScan()
            else permissionLauncher.launch(Manifest.permission.READ_SMS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = appBarColors(),
                title = { Text("Scan SMS") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (SmsService.hasPermission(context)) {
                            permissionDenied = false
                            doScan()
                        } else {
                            permissionLauncher.launch(Manifest.permission.READ_SMS)
                        }
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Scan again")
                    }
                }
            )
        },
        bottomBar = {
            if (selectedCodes.isNotEmpty()) {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    actions = {
                        Text(
                            "${selectedCodes.size} selected",
                            modifier = Modifier.padding(start = 16.dp),
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    floatingActionButton = {
                        ExtendedFloatingActionButton(
                            onClick = {
                                val l = loan ?: return@ExtendedFloatingActionButton
                                vm.addPayments(
                                    fresh
                                        .filter { it.mpesa.code in selectedCodes }
                                        .map { SmsService.toPayment(it, l.id) }
                                )
                                onBack()
                            },
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        ) {
                            Text("Import ${selectedCodes.size}")
                        }
                    }
                )
            }
        }
    ) { padding ->
        val l = loan
        if (l == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        when {
            permissionDenied -> CenteredMessage(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                title = "SMS permission required",
                body = "Allow SMS access so Loan Tracker can find M-Pesa messages for this loan.",
                actionLabel = "Allow access",
                onAction = { permissionLauncher.launch(Manifest.permission.READ_SMS) }
            )

            scanning || !hasScanned -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Looking for M-Pesa messages…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            detections.isEmpty() -> CenteredMessage(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                title = "No matching messages",
                body = "No M-Pesa messages matched account ${l.keyword ?: "(none)"} in the " +
                    "last 90 days.",
                actionLabel = "Scan again",
                onAction = { doScan() }
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)
            ) {
                item(key = "info") {
                    Text(
                        "Account ${l.keyword ?: "(none)"}  ·  ${l.startDate.format(DATE_ONLY_FMT)} to " +
                            l.dueDate.format(DATE_ONLY_FMT),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                if (fresh.isEmpty()) {
                    item(key = "caught_up") {
                        val tone = toneColors(Tone.SUCCESS)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = tone.bg)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = tone.fg)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "All caught up",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = tone.fg
                                    )
                                    Text(
                                        "All ${done.size} matching payments are already in the app.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                } else {
                    item(key = "new_header") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "New (${fresh.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                deselected = if (selectedCodes.size == fresh.size)
                                    fresh.map { it.mpesa.code }.toSet() else emptySet()
                            }) {
                                Text(if (selectedCodes.size == fresh.size) "Select none" else "Select all")
                            }
                        }
                    }
                    items(fresh, key = { "new_" + it.mpesa.code }) { det ->
                        DetectionRow(
                            detected = det,
                            imported = false,
                            isSelected = det.mpesa.code in selectedCodes,
                            onToggle = {
                                deselected = if (det.mpesa.code in deselected)
                                    deselected - det.mpesa.code else deselected + det.mpesa.code
                            }
                        )
                    }
                }

                if (done.isNotEmpty()) {
                    item(key = "done_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showImported = !showImported }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Already in app (${done.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                if (showImported) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (showImported) "Hide" else "Show",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (showImported) {
                        items(done, key = { "done_" + it.mpesa.code }) { det ->
                            DetectionRow(
                                detected = det,
                                imported = true,
                                isSelected = false,
                                onToggle = {}
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(
    modifier: Modifier,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetectionRow(
    detected: DetectedPayment,
    imported: Boolean,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val mpesa = detected.mpesa
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (imported) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Already in app",
                    tint = toneColors(Tone.SUCCESS).fg,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            } else {
                Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
            }
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        formatMoneyScan(mpesa.amount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (imported) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        mpesa.date.format(DATETIME_FMT),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "Code ${mpesa.code}" + (mpesa.sender?.let { "  ·  $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    if (imported) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) { content() }
    } else {
        Card(
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) { content() }
    }
}
