package com.loantracker.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.loantracker.app.data.Loan
import com.loantracker.app.data.Payment
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A loan is "completed" when payments cover everything owed (principal + interest).
 * Derived from payments, so no database change or migration is needed.
 */
fun Loan.isRepaid(paid: Double): Boolean = totalPayable > 0.0 && paid + 0.5 >= totalPayable

fun List<Payment>.sumsByLoan(): Map<String, Double> =
    groupBy { it.loanId }.mapValues { (_, v) -> v.sumOf { it.amount } }

fun List<Payment>.lastPaidDateByLoan(): Map<String, LocalDate> =
    groupBy { it.loanId }.mapValues { (_, v) ->
        LocalDate.ofEpochDay(v.maxOf { it.paidAt.toLocalDate().toEpochDay() })
    }

/** "Cleared 3 days early" / "Cleared on the due date" / "Cleared 2 days after the due date". */
fun repaymentNote(due: LocalDate, repaidOn: LocalDate?): String {
    if (repaidOn == null) return "Fully repaid"
    val diff = ChronoUnit.DAYS.between(repaidOn, due)
    return when {
        diff > 0L -> "Cleared $diff ${if (diff == 1L) "day" else "days"} early"
        diff == 0L -> "Cleared on the due date"
        else -> "Cleared ${-diff} ${if (-diff == 1L) "day" else "days"} after the due date"
    }
}

@Composable
fun RepaidNote(text: String, modifier: Modifier = Modifier) {
    val tone = toneColors(Tone.SUCCESS)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = tone.fg,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = tone.fg
        )
    }
}
