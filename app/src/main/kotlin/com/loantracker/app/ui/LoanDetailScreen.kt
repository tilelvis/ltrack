package com.loantracker.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loantracker.app.data.Payment
import com.loantracker.app.data.PaymentSource
import com.loantracker.app.data.Severity
import com.loantracker.app.services.LenderActions
import com.loantracker.app.services.PdfService
import java.time.format.DateTimeFormatter

private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
private val DATETIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")

fun formatMoneyDetail(amount: Double): String = "KSh %,.0f".format(amount)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanDetailScreen(
    loanId: String,
    vm: LoanViewModel,
    onBack: () -> Unit,
    onAddPayment: (String) -> Unit,
    onMpesaImport: (String) -> Unit,
    onSmsScan: (String) -> Unit
) {
    val context = LocalContext.current
    val loan by vm.loan(loanId).collectAsStateWithLifecycle()
    val payments by vm.payments(loanId).collectAsStateWithLifecycle()
    val totalPaid by vm.totalPaid(loanId).collectAsStateWithLifecycle()
    val recommendation by vm.dailyRecommendation(loanId).collectAsStateWithLifecycle()
    val repaid = loan?.isRepaid(totalPaid) == true
    val lastPaid = remember(payments) { payments.lastPaidDateByLoan().values.firstOrNull() }

    var showDeleteLoanDialog by remember { mutableStateOf(false) }
    val deletePaymentTarget = remember { mutableStateOf<Payment?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = appBarColors(),
                title = { Text(loan?.title ?: "Loan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onMpesaImport(loanId) }) {
                        Icon(Icons.Filled.QrCode, contentDescription = "Paste M-Pesa")
                    }
                    IconButton(onClick = { onSmsScan(loanId) }) {
                        Icon(Icons.Filled.Sms, contentDescription = "Scan SMS")
                    }
                    IconButton(onClick = {
                        val l = loan ?: return@IconButton
                        PdfService.generateLoanReport(context, l, payments, totalPaid)
                    }) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = "PDF report")
                    }
                    IconButton(onClick = { showDeleteLoanDialog = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete loan")
                    }
                }
            )
        },
        floatingActionButton = {
            if (!repaid) FloatingActionButton(
                onClick = { onAddPayment(loanId) },
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add payment")
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

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
        ) {
            item {
                HeroDetailCard(
                    loan = l,
                    totalPaid = totalPaid,
                    repaid = repaid,
                    lastPaid = lastPaid
                )
            }

            recommendation?.takeIf { it.severity != Severity.PAID }?.let { rec ->
                item { DetailRecommendationCard(rec) }
            }

            val lenderIsTitle = l.lenderName.equals(l.title, ignoreCase = true)
            if ((!l.lenderName.isNullOrBlank() && !lenderIsTitle) ||
                !l.lenderPhone.isNullOrBlank() ||
                !l.lenderEmail.isNullOrBlank()
            ) {
                item { LenderCard(loan = l, totalPaid = totalPaid) }
            }

            item {
                Text(
                    "Payments (${payments.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            if (payments.isEmpty()) {
                item {
                    Text(
                        "No payments yet. Tap + to record one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(payments, key = { it.id }) { p ->
                    PaymentRow(
                        payment = p,
                        onShare = {
                            PdfService.generatePaymentReceipt(context, l, p, totalPaid)
                        },
                        onDelete = { deletePaymentTarget.value = p }
                    )
                }
            }
        }
    }

    if (showDeleteLoanDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteLoanDialog = false },
            title = { Text("Delete loan?") },
            text = {
                Text("This will permanently delete \"${loan?.title}\" and all its payments.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteLoanDialog = false
                    loan?.let { vm.deleteLoan(it) }
                    onBack()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteLoanDialog = false }) { Text("Cancel") }
            }
        )
    }

    deletePaymentTarget.value?.let { target ->
        AlertDialog(
            onDismissRequest = { deletePaymentTarget.value = null },
            title = { Text("Delete payment?") },
            text = {
                Text("Delete this payment of ${formatMoneyDetail(target.amount)}?")
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deletePayment(target)
                    deletePaymentTarget.value = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletePaymentTarget.value = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun HeroDetailCard(
    loan: com.loantracker.app.data.Loan,
    totalPaid: Double,
    repaid: Boolean,
    lastPaid: java.time.LocalDate?
) {
    val balance = (loan.totalPayable - totalPaid).coerceAtLeast(0.0)
    val progress = if (loan.totalPayable > 0) {
        (totalPaid / loan.totalPayable).coerceIn(0.0, 1.0).toFloat()
    } else 0f
    val percent = (progress * 100).toInt()
    val days = loan.daysRemaining

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Outstanding balance",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                formatMoneyDetail(balance),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Paid ${formatMoneyDetail(totalPaid)} of ${formatMoneyDetail(loan.totalPayable)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    if (repaid) "Repaid" else "$percent% • ${daysLeftText(days)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        repaid -> toneColors(Tone.SUCCESS).fg
                        days <= 0 -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
            if (repaid) {
                RepaidNote(repaymentNote(loan.dueDate, lastPaid))
            } else {
                Text(
                    "Due ${loan.dueDate.format(DATE_FMT)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun DetailRecommendationCard(rec: com.loantracker.app.data.DailyPaymentRecommendation) {
    val color = severityColor(rec.severity)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = severityContainer(rec.severity))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                if (rec.severity == Severity.PAID) "Loan repaid!"
                else "Pay ${formatMoneyDetail(rec.dailyAmount)} today",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(Modifier.height(2.dp))
            Text(
                rec.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LenderCard(
    loan: com.loantracker.app.data.Loan,
    totalPaid: Double
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Lender",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            loan.lenderName?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            loan.lenderPhone?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            loan.lenderEmail?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                loan.lenderPhone?.let { phone ->
                    AssistChip(
                        onClick = { LenderActions.call(context, phone) },
                        label = { Text("Call") },
                        leadingIcon = { Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    AssistChip(
                        onClick = { LenderActions.whatsapp(context, loan, totalPaid) },
                        label = { Text("WhatsApp") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    AssistChip(
                        onClick = { LenderActions.sms(context, phone, loan, totalPaid) },
                        label = { Text("SMS") },
                        leadingIcon = { Icon(Icons.Filled.Sms, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
                loan.lenderEmail?.let { email ->
                    AssistChip(
                        onClick = { LenderActions.email(context, loan, totalPaid, email) },
                        label = { Text("Email") },
                        leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PaymentRow(
    payment: Payment,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    formatMoneyDetail(payment.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    payment.paidAt.format(DATETIME_FMT) + "  ·  " +
                        (payment.mpesaCode ?: if (payment.source == PaymentSource.MPESA) "M-Pesa" else "Manual"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Filled.Share, contentDescription = "Share receipt")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete payment")
            }
        }
    }
}
