package com.loantracker.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loantracker.app.data.MpesaParser
import com.loantracker.app.data.Payment
import com.loantracker.app.data.PaymentSource
import java.time.format.DateTimeFormatter

private val DATETIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")

private fun formatMoneyImport(amount: Double): String = "KSh %,.0f".format(amount)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MpesaImportScreen(
    loanId: String,
    vm: LoanViewModel,
    onBack: () -> Unit
) {
    val loan by vm.loan(loanId).collectAsStateWithLifecycle()
    var message by remember { mutableStateOf("") }

    val parsed = remember(message) { MpesaParser.parse(message) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paste M-Pesa") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Paste the M-Pesa confirmation SMS below. We'll parse and validate it against this loan.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("M-Pesa message") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp),
                minLines = 4
            )

            val p = parsed
            if (p == null) {
                if (message.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            "Couldn't parse this as an M-Pesa confirmation. " +
                                "Make sure it contains the transaction code and amount.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                PreviewCard(
                    code = p.code,
                    amount = p.amount,
                    sender = p.sender,
                    date = p.date.format(DATETIME_FMT),
                    account = p.account
                )

                val accountMatches = l.keyword?.let { kw ->
                    p.account?.equals(kw, ignoreCase = true) == true
                } ?: true

                val dateInRange = !p.date.toLocalDate().isBefore(l.startDate) &&
                    !p.date.toLocalDate().isAfter(l.dueDate)

                if (!accountMatches) {
                    WarningCard(
                        text = "Account ${p.account ?: "?"} doesn't match this loan's account (${l.keyword ?: "(none)"}).",
                        color = Color(0xFFDC2626),
                        bgColor = Color(0xFFFEE2E2)
                    )
                }

                if (!dateInRange) {
                    WarningCard(
                        text = "This message is from ${p.date.toLocalDate().format(DATE_FMT)} — " +
                            "outside this loan's period " +
                            "(${l.startDate.format(DATE_FMT)} to ${l.dueDate.format(DATE_FMT)}). " +
                            "It may belong to another loan.",
                        color = Color(0xFFB45309),
                        bgColor = Color(0xFFFEF3C7)
                    )
                }

                if (accountMatches && dateInRange) {
                    WarningCard(
                        text = "Valid payment for this loan.",
                        color = Color(0xFF16A34A),
                        bgColor = Color(0xFFDCFCE7)
                    )
                    Button(
                        onClick = {
                            val payment = Payment(
                                id = LoanViewModel.newId(),
                                loanId = loanId,
                                amount = p.amount,
                                paidAt = p.date,
                                mpesaCode = p.code,
                                phone = p.phone,
                                sender = p.sender,
                                account = p.account,
                                rawMessage = message,
                                source = PaymentSource.MPESA
                            )
                            vm.addPayment(payment)
                            onBack()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text("Save payment", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewCard(
    code: String,
    amount: Double,
    sender: String,
    date: String,
    account: String?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Parsed payment",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            PreviewRow("Amount", formatMoneyImport(amount))
            PreviewRow("Code", code)
            PreviewRow("Sender", sender)
            PreviewRow("Date", date)
            account?.let { PreviewRow("Account", it) }
        }
    }
}

@Composable
private fun PreviewRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun WarningCard(
    text: String,
    color: Color,
    bgColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Text(
            text,
            modifier = Modifier.padding(14.dp),
            color = color,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
