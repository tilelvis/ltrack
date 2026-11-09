package com.loantracker.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.loantracker.app.services.DetectedPayment
import com.loantracker.app.services.SmsService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

private val DATETIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")

private fun formatMoneyScan(amount: Double): String = "KSh %,.0f".format(amount)

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
    var selected by remember { mutableStateOf(setOf<String>()) }
    var scanning by remember { mutableStateOf(false) }
    var hasScanned by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }

    fun doScan() {
        val l = loan ?: return
        scanning = true
        scope.launch {
            val codes = payments.mapNotNull { it.mpesaCode?.uppercase() }.toSet()
            val results = SmsService.scanForLoan(context, l, codes)
            detections = results
            selected = results.filter { !it.alreadyImported }.map { it.mpesa.code }.toSet()
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
                        Icon(Icons.Filled.Refresh, contentDescription = "Scan")
                    }
                }
            )
        },
        bottomBar = {
            if (selected.isNotEmpty() && detections.isNotEmpty()) {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    actions = {
                        Text(
                            "${selected.size} selected",
                            modifier = Modifier.padding(start = 16.dp),
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    floatingActionButton = {
                        ExtendedFloatingActionButton(
                            onClick = {
                                val l = loan ?: return@ExtendedFloatingActionButton
                                detections
                                    .filter { it.mpesa.code in selected }
                                    .forEach { det ->
                                        val payment = SmsService.toPayment(det, l.id)
                                        vm.addPayment(payment)
                                    }
                                onBack()
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Text("Import ${selected.size}")
                        }
                    }
                )
            }
        }
    ) { padding ->
        val l = loan
        if (l == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "Scanning for M-Pesa messages",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        buildString {
                            append("Account: ${l.keyword ?: "(none)"}")
                            append("\nRange: ${l.startDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}")
                            append(" → ${l.dueDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (permissionDenied) {
                PermissionDeniedView(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    onGrant = {
                        permissionLauncher.launch(Manifest.permission.READ_SMS)
                    }
                )
                return@Column
            }

            if (scanning) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Scanning inbox…")
                    }
                }
                return@Column
            }

            if (hasScanned && detections.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "No matching messages found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No M-Pesa messages matched this loan's account number and date range.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                return@Column
            }

            if (!hasScanned && detections.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Tap the refresh icon to scan your SMS inbox.")
                        Text(
                            "We'll find M-Pesa messages matching this loan's account number and date range.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
                return@Column
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(detections, key = { it.mpesa.code }) { det ->
                    DetectionRow(
                        detected = det,
                        isSelected = det.mpesa.code in selected,
                        onToggle = {
                            if (det.alreadyImported) return@DetectionRow
                            selected = if (det.mpesa.code in selected) {
                                selected - det.mpesa.code
                            } else {
                                selected + det.mpesa.code
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionDeniedView(
    modifier: Modifier = Modifier,
    onGrant: () -> Unit
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "SMS permission required",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Grant SMS read permission to scan your inbox for M-Pesa messages.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onGrant) { Text("Grant permission") }
        }
    }
}

@Composable
private fun DetectionRow(
    detected: DetectedPayment,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val mpesa = detected.mpesa
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (detected.alreadyImported)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (detected.alreadyImported) {
                AssistChip(
                    onClick = {},
                    label = { Text("Already in app", style = MaterialTheme.typography.labelSmall) }
                )
            } else {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggle() }
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    formatMoneyScan(mpesa.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    mpesa.date.format(DATETIME_FMT),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Code: ${mpesa.code}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                mpesa.sender?.let {
                    Text(
                        "From: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                mpesa.account?.let {
                    Text(
                        "Account: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
