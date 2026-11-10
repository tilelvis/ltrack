package com.loantracker.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loantracker.app.data.DailyPaymentCalculator
import com.loantracker.app.data.DailyPaymentRecommendation
import com.loantracker.app.data.Loan
import com.loantracker.app.data.Payment
import com.loantracker.app.data.Severity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")

fun formatMoney(amount: Double): String = "KSh %,.0f".format(amount)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    vm: LoanViewModel,
    onAddLoan: () -> Unit,
    onOpenLoan: (String) -> Unit
) {
    val loans by vm.loans.collectAsStateWithLifecycle()
    val allPayments by vm.allPayments.collectAsStateWithLifecycle()

    // Only unpaid (active) loans take precedence; repaid loans move to their own section.
    val paidByLoan = remember(allPayments) { allPayments.sumsByLoan() }
    val lastPaidOn = remember(allPayments) { allPayments.lastPaidDateByLoan() }
    val activeLoans = remember(loans, paidByLoan) {
        loans.filter { !it.isRepaid(paidByLoan[it.id] ?: 0.0) }
    }
    val completedLoans = remember(loans, paidByLoan) {
        loans.filter { it.isRepaid(paidByLoan[it.id] ?: 0.0) }
    }

    // The loan card at the front of the stack (kept by id so it survives list changes).
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val frontIndex = activeLoans.indexOfFirst { it.id == selectedId }.let { if (it < 0) 0 else it }
    val selected = activeLoans.getOrNull(frontIndex)
    val selectedPaid = selected?.let { paidByLoan[it.id] } ?: 0.0
    val selectedRecommendation = remember(selected, selectedPaid) {
        selected?.let { DailyPaymentCalculator.calculate(it, selectedPaid) }
    }
    val (currentStreak, bestStreak) = computeStreak(allPayments)

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(title = { Text("Loan Tracker") }, colors = appBarColors())
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddLoan,
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add loan")
            }
        }
    ) { padding ->
        if (loans.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "No loans yet",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tap + to add your first loan.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            if (selected != null) {
                item(key = "loan_stack") {
                    LoanCardStack(
                        loans = activeLoans,
                        paidByLoan = paidByLoan,
                        frontIndex = frontIndex,
                        onSelect = { selectedId = activeLoans[it].id },
                        onOpen = { onOpenLoan(it.id) }
                    )
                }
                if (activeLoans.size > 1) {
                    item(key = "loan_dots") {
                        LoanDots(
                            count = activeLoans.size,
                            selected = frontIndex,
                            onSelect = { selectedId = activeLoans[it].id }
                        )
                    }
                }
                selectedRecommendation?.let { rec ->
                    item(key = "loan_recommendation") { RecommendationCard(rec) }
                }
                item(key = "streak") {
                    StreakCard(currentStreak = currentStreak, bestStreak = bestStreak)
                }
            } else {
                item {
                    AllClearCard(completedCount = completedLoans.size, onAddLoan = onAddLoan)
                }
            }

            if (completedLoans.isNotEmpty()) {
                item {
                    Text(
                        "Completed",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                item {
                    WinsCard(
                        count = completedLoans.size,
                        totalRepaid = completedLoans.sumOf { paidByLoan[it.id] ?: 0.0 }
                    )
                }
                items(completedLoans.take(3), key = { "done_" + it.id }) { loan ->
                    CompletedLoanRow(
                        loan = loan,
                        note = repaymentNote(loan.dueDate, lastPaidOn[loan.id]),
                        onClick = { onOpenLoan(loan.id) }
                    )
                }
                if (completedLoans.size > 3) {
                    item {
                        Text(
                            "+${completedLoans.size - 3} more in History",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AllClearCard(completedCount: Int, onAddLoan: () -> Unit) {
    val tone = toneColors(Tone.SUCCESS)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = tone.fg,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "You're all clear",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                "No active loans. $completedCount ${if (completedCount == 1) "loan" else "loans"} cleared.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onAddLoan) { Text("Add a loan") }
        }
    }
}

@Composable
private fun WinsCard(count: Int, totalRepaid: Double) {
    val tone = toneColors(Tone.SUCCESS)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = tone.bg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = tone.fg,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "$count ${if (count == 1) "loan" else "loans"} cleared",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = tone.fg
                )
                Text(
                    "${formatMoney(totalRepaid)} repaid in total",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun CompletedLoanRow(loan: Loan, note: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    loan.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                RepaidNote(note)
            }
            Text(
                formatMoney(loan.totalPayable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecommendationCard(rec: DailyPaymentRecommendation) {
    val color = severityColor(rec.severity)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = severityContainer(rec.severity)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (rec.severity == Severity.PAID) "Loan repaid!"
                    else "Pay ${formatMoney(rec.dailyAmount)} today",
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
}

@Composable
private fun StreakCard(currentStreak: Int, bestStreak: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.LocalFireDepartment,
                contentDescription = null,
                tint = toneColors(Tone.ELEVATED).fg,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "$currentStreak day streak",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (bestStreak > 0) {
                    Text(
                        "Best: $bestStreak ${if (bestStreak == 1) "day" else "days"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactLoanCard(
    loan: Loan,
    totalPaid: Double,
    onClick: () -> Unit
) {
    val days = loan.daysRemaining
    val balance = (loan.totalPayable - totalPaid).coerceAtLeast(0.0)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    loan.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Balance ${formatMoney(balance)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                daysLeftText(days),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (days <= 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun computeStreak(payments: List<Payment>): Pair<Int, Int> {
    if (payments.isEmpty()) return 0 to 0
    val days = payments.map { it.paidAt.toLocalDate() }.distinct().sorted()
    var cursor = LocalDate.now()
    if (cursor !in days) cursor = cursor.minusDays(1)
    var current = 0
    while (cursor in days) {
        current++
        cursor = cursor.minusDays(1)
    }
    var longest = 0
    var run = 0
    for (i in days.indices) {
        if (i > 0 && days[i] == days[i - 1].plusDays(1)) run++ else run = 1
        if (run > longest) longest = run
    }
    return current to longest
}
