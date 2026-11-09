package com.loantracker.app.ui

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loantracker.app.data.Loan
import com.loantracker.app.data.PaymentInterval
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")

private fun formatMoneyHistory(amount: Double): String = "KSh %,.0f".format(amount)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LoanHistoryScreen(
    vm: LoanViewModel,
    onOpenLoan: (String) -> Unit,
    onAddLoan: () -> Unit
) {
    val allLoans by vm.allLoans.collectAsStateWithLifecycle()
    val allPayments by vm.allPayments.collectAsStateWithLifecycle()

    // Compute paid-by-loan once per allPayments change — not per card.
    val paidByLoan = remember(allPayments) {
        allPayments.groupBy { it.loanId }
            .mapValues { (_, v) -> v.sumOf { it.amount } }
    }

    val today = remember { LocalDate.now() }

    // Group loans once per allLoans change.
    val (overdueLoans, activeLoans, hiddenLoans) = remember(allLoans, today) {
        val overdue = allLoans.filter { !it.isHidden && today.isAfter(it.dueDate) }
        val active = allLoans.filter { !it.isHidden && !today.isAfter(it.dueDate) }
        val hidden = allLoans.filter { it.isHidden }
        Triple(overdue, active, hidden)
    }

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(title = { Text("Loan History") }, colors = appBarColors())
        }
    ) { padding ->
        if (allLoans.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.FolderOff,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No loans yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Add your first loan to get started.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onAddLoan) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.size(4.dp))
                        Text("Add loan")
                    }
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Summary row — 4 equal-width cards
            item(key = "summary", contentType = "summary") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryCard(
                        label = "Total",
                        count = allLoans.size,
                        tone = Tone.NEUTRAL,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryCard(
                        label = "Active",
                        count = activeLoans.size,
                        tone = Tone.SUCCESS,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryCard(
                        label = "Overdue",
                        count = overdueLoans.size,
                        tone = Tone.DANGER,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryCard(
                        label = "Hidden",
                        count = hiddenLoans.size,
                        tone = Tone.NEUTRAL,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (overdueLoans.isNotEmpty()) {
                stickyHeader(key = "header_overdue", contentType = "header") {
                    SectionHeader("Overdue", overdueLoans.size)
                }
                items(overdueLoans, key = { it.id }, contentType = { "loanCard" }) { loan ->
                    HistoryLoanCard(
                        loan = loan,
                        totalPaid = paidByLoan[loan.id] ?: 0.0,
                        today = today,
                        onClick = { onOpenLoan(loan.id) }
                    )
                }
            }

            if (activeLoans.isNotEmpty()) {
                stickyHeader(key = "header_active", contentType = "header") {
                    SectionHeader("Active", activeLoans.size)
                }
                items(activeLoans, key = { it.id }, contentType = { "loanCard" }) { loan ->
                    HistoryLoanCard(
                        loan = loan,
                        totalPaid = paidByLoan[loan.id] ?: 0.0,
                        today = today,
                        onClick = { onOpenLoan(loan.id) }
                    )
                }
            }

            if (hiddenLoans.isNotEmpty()) {
                stickyHeader(key = "header_hidden", contentType = "header") {
                    SectionHeader("Hidden", hiddenLoans.size)
                }
                items(hiddenLoans, key = { it.id }, contentType = { "loanCard" }) { loan ->
                    HistoryLoanCard(
                        loan = loan,
                        totalPaid = paidByLoan[loan.id] ?: 0.0,
                        today = today,
                        onClick = { onOpenLoan(loan.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Text(
            "$title ($count)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
}

@Composable
private fun SummaryCard(
    label: String,
    count: Int,
    tone: Tone,
    modifier: Modifier = Modifier
) {
    val tc = toneColors(tone)
    val color = tc.fg
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = tc.bg)
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 10.dp, horizontal = 8.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryLoanCard(
    loan: Loan,
    totalPaid: Double,
    today: LocalDate,
    onClick: () -> Unit
) {
    val isOverdue = !loan.isHidden && today.isAfter(loan.dueDate)
    val statusText = when {
        loan.isHidden -> "Hidden"
        isOverdue -> "Overdue"
        else -> "Active"
    }
    val statusTone = toneColors(
        when {
            loan.isHidden -> Tone.NEUTRAL
            isOverdue -> Tone.DANGER
            else -> Tone.SUCCESS
        }
    )

    val progress = if (loan.totalPayable > 0) {
        (totalPaid / loan.totalPayable).coerceIn(0.0, 1.0).toFloat()
    } else 0f
    val percent = (progress * 100).toInt()

    val contentDesc = "${loan.title}, $statusText, due ${loan.dueDate.format(DATE_FMT)}, " +
        "paid ${formatMoneyHistory(totalPaid)} of ${formatMoneyHistory(loan.totalPayable)}"

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = contentDesc },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Row 1: title + status badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    loan.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.size(8.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = statusTone.bg,
                    contentColor = statusTone.fg
                ) {
                    Text(
                        statusText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Row 2: lender name
            loan.lenderName
                ?.takeIf { it.isNotBlank() && !it.equals(loan.title, ignoreCase = true) }
                ?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Row 3: 3-column stat row
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatColumn(
                    label = "Principal",
                    value = formatMoneyHistory(loan.principal),
                    modifier = Modifier.weight(1f)
                )
                StatColumn(
                    label = "Interest",
                    value = "${loan.interestRate}%",
                    modifier = Modifier.weight(1f)
                )
                StatColumn(
                    label = "Paid",
                    value = formatMoneyHistory(totalPaid),
                    modifier = Modifier.weight(1f)
                )
            }

            // Row 4: progress bar
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = if (isOverdue) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )

            // Row 5: percent + due date
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "$percent% paid",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Due ${loan.dueDate.format(DATE_FMT)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun StatColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// --- Previews ---

@Preview(showBackground = true, name = "Light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark")
@Preview(showBackground = true, fontScale = 2.0f, name = "2x font scale")
@Composable
private fun HistoryLoanCardPreview() {
    MaterialTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            HistoryLoanCard(
                loan = Loan(
                    id = "preview1",
                    title = "NCBA Bank Loan",
                    principal = 50000.0,
                    interestRate = 10.0,
                    startDate = LocalDate.now().minusDays(15),
                    dueDate = LocalDate.now().plusDays(15),
                    expectedPerInterval = 0.0,
                    interval = PaymentInterval.WEEKLY,
                    lenderName = "NCBA Bank Kenya PLC",
                    keyword = "001709"
                ),
                totalPaid = 20000.0,
                today = LocalDate.now(),
                onClick = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Overdue")
@Composable
private fun HistoryLoanCardOverduePreview() {
    MaterialTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            HistoryLoanCard(
                loan = Loan(
                    id = "preview2",
                    title = "Emergency Loan",
                    principal = 15000.0,
                    interestRate = 5.0,
                    startDate = LocalDate.now().minusDays(40),
                    dueDate = LocalDate.now().minusDays(10),
                    expectedPerInterval = 0.0,
                    interval = PaymentInterval.WEEKLY
                ),
                totalPaid = 5000.0,
                today = LocalDate.now(),
                onClick = {}
            )
        }
    }
}
