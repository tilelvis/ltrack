package com.loantracker.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.loantracker.app.data.Payment
import com.loantracker.app.data.PaymentSource
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPaymentScreen(
    loanId: String,
    vm: LoanViewModel,
    onBack: () -> Unit,
    initialAmount: String? = null
) {
    var amount by remember { mutableStateOf(initialAmount.orEmpty()) }
    var mpesaCode by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val amountValid = amount.trim().toDoubleOrNull()?.let { it > 0 } ?: false

    Scaffold(
        topBar = {
            TopAppBar(
                colors = appBarColors(),
                title = { Text("Add Payment") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.filter { ch -> ch.isDigit() || ch == '.' } },
                label = { Text("Amount (KSh) *") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                "Date",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    date.format(DATE_FMT),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { showDatePicker = true }) {
                    Text("Change date")
                }
            }

            OutlinedTextField(
                value = mpesaCode,
                onValueChange = { mpesaCode = it.uppercase().take(12) },
                label = { Text("M-Pesa code (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    val amt = amount.trim().toDoubleOrNull() ?: return@Button
                    val now = LocalDateTime.now()
                    val paidAt = LocalDateTime.of(date, now.toLocalTime())
                    val payment = Payment(
                        id = LoanViewModel.newId(),
                        loanId = loanId,
                        amount = amt,
                        paidAt = paidAt,
                        mpesaCode = mpesaCode.ifBlank { null },
                        source = if (mpesaCode.isNotBlank()) PaymentSource.MPESA else PaymentSource.MANUAL,
                        notes = notes.ifBlank { null }
                    )
                    vm.addPayment(payment)
                    onBack()
                },
                enabled = amountValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("Save payment", fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (showDatePicker) {
        DatePickerModal(
            initialDate = date,
            onConfirm = { date = it; showDatePicker = false },
            onDismiss = { showDatePicker = false }
        )
    }
}
