package com.loantracker.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.loantracker.app.data.Loan
import com.loantracker.app.data.PaymentInterval
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLoanScreen(vm: LoanViewModel, onBack: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var principal by remember { mutableStateOf("") }
    var interestRate by remember { mutableStateOf("0") }
    var startDate by remember { mutableStateOf(LocalDate.now()) }
    var dueDate by remember { mutableStateOf(LocalDate.now().plusMonths(1)) }
    var keyword by remember { mutableStateOf("001709") }  // constant account number
    var lenderName by remember { mutableStateOf("") }
    var lenderPhone by remember { mutableStateOf("") }
    var lenderEmail by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var showStartPicker by remember { mutableStateOf(false) }
    var showDuePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = appBarColors(),
                title = { Text("New loan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Loan title
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Loan title *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Principal
            OutlinedTextField(
                value = principal,
                onValueChange = { principal = it },
                label = { Text("Principal amount (KSh) *") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            // Interest rate
            OutlinedTextField(
                value = interestRate,
                onValueChange = { interestRate = it },
                label = { Text("Interest rate (%)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            // Start date
            Text("Start date", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = startDate.format(DATE_FMT),
                onValueChange = {},
                readOnly = true,
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(onClick = { showStartPicker = true }) {
                Text("Change start date")
            }

            Spacer(Modifier.height(4.dp))

            // Due date
            Text("Due date", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = dueDate.format(DATE_FMT),
                onValueChange = {},
                readOnly = true,
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(onClick = { showDuePicker = true }) {
                Text("Change due date")
            }

            // Loan duration display
            val durationDays = ChronoUnit.DAYS.between(startDate, dueDate) + 1
            if (durationDays > 0) {
                Text(
                    "Loan duration: $durationDays days",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            // M-Pesa account number (keyword) — constant, used to match SMS payments
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                label = { Text("M-Pesa account number") },
                supportingText = { Text("Constant identifier — matches SMS payments to this loan") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            // Lender contact
            Text("Lender contact (optional)", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = lenderName,
                onValueChange = { lenderName = it },
                label = { Text("Lender name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = lenderPhone,
                onValueChange = { lenderPhone = it },
                label = { Text("Lender phone") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = lenderEmail,
                onValueChange = { lenderEmail = it },
                label = { Text("Lender email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    val p = principal.trim().toDoubleOrNull() ?: return@Button
                    if (title.isBlank()) return@Button
                    val loan = Loan(
                        id = LoanViewModel.newId(),
                        title = title.trim(),
                        principal = p,
                        interestRate = interestRate.trim().toDoubleOrNull() ?: 0.0,
                        startDate = startDate,
                        dueDate = dueDate,
                        expectedPerInterval = 0.0,
                        interval = PaymentInterval.WEEKLY,
                        keyword = keyword.ifBlank { null },
                        lenderName = lenderName.ifBlank { null },
                        lenderPhone = lenderPhone.ifBlank { null },
                        lenderEmail = lenderEmail.ifBlank { null },
                        notes = notes.ifBlank { null }
                    )
                    vm.saveLoan(loan)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Save loan", fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (showStartPicker) {
        DatePickerModal(startDate, { startDate = it; showStartPicker = false }, { showStartPicker = false })
    }
    if (showDuePicker) {
        DatePickerModal(dueDate, { dueDate = it; showDuePicker = false }, { showDuePicker = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerModal(
    initialDate: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let {
                    onConfirm(Instant.ofEpochMilli(it)
                        .atZone(ZoneId.systemDefault()).toLocalDate())
                } ?: onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) { DatePicker(state = state) }
}
