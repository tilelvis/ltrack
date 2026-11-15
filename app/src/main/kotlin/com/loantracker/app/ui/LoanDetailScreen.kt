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
                    GradientActionButton("Add payment") { onAddPayment(loanId) }
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
    val palette = loanCardPalette(paletteIndexFor(loan))
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
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.brush)
                .padding(20.dp)
        ) {
            Text(
                listOfNotNull(lender, "$rate% flat").joinToString(" · "),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.9f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                formatMoneyDetail(shownBalance.value.toDouble()),
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            Text(
                "of ${formatMoneyDetail(loan.totalPayable)} · due ${loan.dueDate.format(DATE_FMT)}",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.9f)
            )
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(shownProgress.value.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(palette.accent)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (repaid) "Repaid" else "$percent% repaid · ${daysLeftText(days)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                val (label, bg, fg) = statusChip(repaid, days)
                Text(
                    label,
                    modifier = Modifier.clip(CircleShape).background(bg).padding(horizontal = 12.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = fg
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Interest ${formatMoneyDetail(loan.totalPayable - loan.principal)} on top of " +
                    "${formatMoneyDetail(loan.principal)} borrowed",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.88f)
            )
            planStatus(loan, totalPaid)?.let {
                Spacer(Modifier.height(8.dp))
                PlanPill(it, textColor = Color.White, background = Color.Black.copy(alpha = 0.25f), long = true)
            }
            if (repaid) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        repaymentNote(loan.dueDate, lastPaid),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/** Label plus soft chip colours that stay readable on any of the card gradients. */
private fun statusChip(repaid: Boolean, days: Long): Triple<String, Color, Color> = when {
    repaid -> Triple("Repaid", Color(0xFFDDEEE3), Color(0xFF1F6B4A))
    days <= 0 -> Triple("Overdue", Color(0xFFF8E1DE), Color(0xFFA8322A))
    days <= 2 -> Triple("Critical", Color(0xFFF8E1DE), Color(0xFFA8322A))
    days <= 5 -> Triple("Elevated", Color(0xFFF9E6D9), Color(0xFFA8480F))
    days <= 10 -> Triple("Moderate", Color(0xFFF8EBD0), Color(0xFF8A5A0B))
    else -> Triple("On track", Color(0xFFDDEEE3), Color(0xFF1F6B4A))
}

private val ActionGradient: Brush
    get() = Brush.horizontalGradient(listOf(Color(0xFFD9461A), Color(0xFFFF6A3D)))

@Composable
private fun DetailRecommendationCard(
    rec: com.loantracker.app.data.DailyPaymentRecommendation,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, shape)
            .clip(shape)
            .background(ActionGradient)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Pay ${formatMoneyDetail(rec.dailyAmount)} today",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            Spacer(Modifier.height(2.dp))
            Text(rec.message, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.95f))
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Pay now", tint = Color.White)
    }
}

@Composable
private fun GradientActionButton(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .shadow(10.dp, shape, ambientColor = Color(0xFFD9461A), spotColor = Color(0xFFD9461A))
            .clip(shape)
            .background(ActionGradient)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
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
