package com.loantracker.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loantracker.app.data.DailyPaymentRecommendation
import com.loantracker.app.data.Loan
import com.loantracker.app.data.Payment
import com.loantracker.app.data.Severity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")

fun formatMoney(amount: Double): String = "KSh %,.0f".format(amount)

private fun severityColor(severity: Severity): Color = when (severity) {
    Severity.PAID -> Color(0xFF16A34A)
    Severity.NORMAL -> Color(0xFF047857)
    Severity.MODERATE -> Color(0xFFCA8A04)
    Severity.ELEVATED -> Color(0xFFEA580C)
    Severity.CRITICAL -> Color(0xFFDC2626)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    vm: LoanViewModel,
    onAddLoan: () -> Unit,
    onOpenLoan: (String) -> Unit
) {
    val loans by vm.loans.collectAsStateWithLifecycle()
    val allPayments by vm.allPayments.collectAsStateWithLifecycle()

    val hero = loans.firstOrNull()
    val heroPaid = if (hero != null) {
        vm.totalPaid(hero.id).collectAsStateWithLifecycle().value
    } else 0.0
    val heroRecommendation = if (hero != null) {
        vm.dailyRecommendation(hero.id).collectAsStateWithLifecycle().value
    } else null
    val (currentStreak, bestStreak) = computeStreak(allPayments)

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(title = { Text("Loan Tracker") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddLoan) {
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
            if (hero != null) {
                item {
                    HeroLoanCard(
                        loan = hero,
                        totalPaid = heroPaid,
                        recommendation = heroRecommendation,
                        onClick = { onOpenLoan(hero.id) }
                    )
                }
            }

            item {
                StreakCard(currentStreak = currentStreak, bestStreak = bestStreak)
            }

            val others = loans.drop(1)
            if (others.isNotEmpty()) {
                item {
                    Text(
                        "Other loans",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                items(others, key = { it.id }) { loan ->
                    val paid by vm.totalPaid(loan.id).collectAsStateWithLifecycle()
                    CompactLoanCard(
                        loan = loan,
                        totalPaid = paid,
                        onClick = { onOpenLoan(loan.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroLoanCard(
    loan: Loan,
    totalPaid: Double,
    recommendation: DailyPaymentRecommendation?,
    onClick: () -> Unit
) {
    val balance = (loan.totalPayable - totalPaid).coerceAtLeast(0.0)
    val progress = if (loan.totalPayable > 0) (totalPaid / loan.totalPayable).coerceIn(0.0, 1.0).toFloat() else 0f
    val percent = (progress * 100).toInt()
    val days = loan.daysRemaining

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                loan.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                formatMoney(balance),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                "of ${formatMoney(loan.totalPayable)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
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
                    "$percent% repaid",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    if (days <= 0) "Overdue" else "$days days left",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (days <= 0) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }

    recommendation?.let { rec ->
        Spacer(Modifier.height(8.dp))
        RecommendationCard(rec)
    }
}

@Composable
private fun RecommendationCard(rec: DailyPaymentRecommendation) {
    val color = severityColor(rec.severity)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.12f)
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
                tint = Color(0xFFEA580C),
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
                        "Best: $bestStreak days",
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
                if (days <= 0) "Overdue" else "$days days left",
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
