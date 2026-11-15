package com.loantracker.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.loantracker.app.data.Loan
import java.time.format.DateTimeFormatter

private val EditDateFmt = DateTimeFormatter.ofPattern("d MMM yyyy")

/** Fix a typo or a changed term on an existing loan. Payments are never touched. */
@Composable
fun EditLoanDialog(loan: Loan, onSave: (Loan) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(loan.title) }
    var principal by remember { mutableStateOf(loan.principal.toLong().toString()) }
    var rate by remember {
        mutableStateOf(if (loan.interestRate % 1.0 == 0.0) loan.interestRate.toInt().toString() else loan.interestRate.toString())
    }
    var lender by remember { mutableStateOf(loan.lenderName.orEmpty()) }
    var start by remember { mutableStateOf(loan.startDate) }
    var due by remember { mutableStateOf(loan.dueDate) }
    var picker by remember { mutableStateOf(0) } // 0 closed, 1 start, 2 due

    val p = principal.toDoubleOrNull()
    val r = rate.toDoubleOrNull()
    val datesOk = !due.isBefore(start)
    val valid = title.isNotBlank() && p != null && p > 0 && r != null && r >= 0 && datesOk

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit loan") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    principal, { principal = it.filter { c -> c.isDigit() } },
                    label = { Text("Amount borrowed (KSh)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    rate, { rate = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Interest (% flat)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(lender, { lender = it }, label = { Text("Lender (optional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Start  ${start.format(EditDateFmt)}", fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = { picker = 1 }) { Text("Change") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Due  ${due.format(EditDateFmt)}", fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = { picker = 2 }) { Text("Change") }
                }
                if (!datesOk) {
                    Text("The due date can’t be before the start date.", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        loan.copy(
                            title = title.trim(),
                            principal = p!!,
                            interestRate = r!!,
                            lenderName = lender.trim().ifBlank { null },
                            startDate = start,
                            dueDate = due
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (picker == 1) DatePickerModal(initialDate = start, onConfirm = { start = it; picker = 0 }, onDismiss = { picker = 0 })
    if (picker == 2) DatePickerModal(initialDate = due, onConfirm = { due = it; picker = 0 }, onDismiss = { picker = 0 })
}
