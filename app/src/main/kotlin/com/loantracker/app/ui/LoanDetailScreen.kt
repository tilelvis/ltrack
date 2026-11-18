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
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
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
    onPayNow: (String, Double) -> Unit,
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
    var showEditDialog by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val deletePaymentTarget = remember { mutableStateOf<Payment?>(null) }

    Scaffold(
        modifier = Modifier.appBackdrop(),
        containerColor = Color.Transparent,
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
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Edit loan") },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                onClick = { menuOpen = false; showEditDialog = true }
                            )
                            val hidden = loan?.isHidden == true
                            DropdownMenuItem(
                                text = { Text(if (hidden) "Unhide loan" else "Hide from dashboard") },
                                leadingIcon = {
                                    Icon(
                                        if (hidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    loan?.let { vm.hideLoan(it.id, !hidden) }
                                    if (!hidden) onBack()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete loan", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                },
                                onClick = { menuOpen = false; showDeleteLoanDialog = true }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (!repaid) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    ActionButton("Add payment") { onAddPayment(loanId) }
                }
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
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
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
                item { DetailRecommendationCard(rec, onClick = { onPayNow(loanId, rec.dailyAmount) }) }
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
                        "No payments yet. Tap Add payment to record one.",
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

    if (showEditDialog) {
        loan?.let { current ->
            EditLoanDialog(
                loan = current,
                onSave = { vm.updateLoan(it); showEditDialog = false },
                onDismiss = { showEditDialog = false }
            )
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
    val cs = MaterialTheme.colorScheme
    val balance = (loan.totalPayable - totalPaid).coerceAtLeast(0.0)
    val progress = if (loan.totalPayable > 0) {
        (totalPaid / loan.totalPayable).coerceIn(0.0, 1.0).toFloat()
    } else 0f
    val percent = (progress * 100).toInt()
    val days = loan.daysRemaining
    val rate = loan.interestRate.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }
    val lender = loan.lenderName?.takeIf { it.isNotBlank() && !it.equals(loan.title, ignoreCase = true) }

    val shownBalance = remember { Animatable(0f) }
    val shownProgress = remember { Animatable(0f) }
    LaunchedEffect(balance) { shownBalance.animateTo(balance.toFloat(), tween(800)) }
    LaunchedEffect(progress) { shownProgress.animateTo(progress, tween(900)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant),
        border = BorderStroke(1.dp, cs.onSurface.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                listOfNotNull(lender, "$rate% flat").joinToString(" · "),
                style = MaterialTheme.typography.labelLarge,
                color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                formatMoneyDetail(shownBalance.value.toDouble()),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface
            )
            Text(
                "of ${formatMoneyDetail(loan.totalPayable)} · due ${loan.dueDate.format(DATE_FMT)}",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(cs.onSurface.copy(alpha = 0.10f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(shownProgress.value.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(cs.primary)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (repaid) "Repaid" else "$percent% repaid · ${daysLeftText(days)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.onSurface
                )
                val (label, tone) = statusLabel(repaid, days)
                val fg = toneColors(tone).fg
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(fg))
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
                }
            }
            Spacer(Modifier.height(16.dp))
            LedgerLine(left = "Borrowed", right = formatMoneyDetail(loan.principal), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            LedgerLine(left = "Interest", right = formatMoneyDetail(loan.totalPayable - loan.principal), modifier = Modifier.fillMaxWidth())
            planStatus(loan, totalPaid)?.let {
                Spacer(Modifier.height(12.dp))
                PlanPill(it, textColor = cs.onSurface, background = Color.Transparent, long = true)
            }
            if (repaid) {
                Spacer(Modifier.height(12.dp))
                RepaidNote(repaymentNote(loan.dueDate, lastPaid))
            }
        }
    }
}

private fun statusLabel(repaid: Boolean, days: Long): Pair<String, Tone> = when {
    repaid -> "Repaid" to Tone.SUCCESS
    days <= 0 -> "Overdue" to Tone.DANGER
    days <= 2 -> "Critical" to Tone.DANGER
    days <= 5 -> "Elevated" to Tone.ELEVATED
    days <= 10 -> "Moderate" to Tone.CAUTION
    else -> "On track" to Tone.SUCCESS
}

@Composable
private fun DetailRecommendationCard(
    rec: com.loantracker.app.data.DailyPaymentRecommendation,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cs.surfaceVariant)
            .border(1.dp, cs.onSurface.copy(alpha = 0.08f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Pay ${formatMoneyDetail(rec.dailyAmount)} today",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(rec.message, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Pay now", tint = cs.onSurfaceVariant)
    }
}

@Composable
private fun ActionButton(text: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = cs.primary, contentColor = cs.onPrimary)
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
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
