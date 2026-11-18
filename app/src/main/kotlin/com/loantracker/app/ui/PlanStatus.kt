package com.loantracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loantracker.app.data.Loan
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max

/**
 * How the repaid amount compares with a straight-line plan from the start date to the due date.
 * direction: 1 ahead, 0 on plan, -1 behind.
 */
data class PlanStatus(val short: String, val long: String, val direction: Int)

fun planStatus(loan: Loan, paid: Double, today: LocalDate = LocalDate.now()): PlanStatus? {
    if (loan.totalPayable <= 0.0 || paid + 0.5 >= loan.totalPayable) return null
    val total = ChronoUnit.DAYS.between(loan.startDate, loan.dueDate).coerceAtLeast(1L)
    val elapsed = ChronoUnit.DAYS.between(loan.startDate, today).coerceIn(0L, total)
    val expected = loan.totalPayable * elapsed / total
    val diff = paid - expected
    val tolerance = max(50.0, loan.totalPayable * 0.01)
    return when {
        diff > tolerance -> PlanStatus("${formatMoney(diff)} ahead", "Ahead of plan by ${formatMoney(diff)}", 1)
        diff < -tolerance -> PlanStatus("${formatMoney(-diff)} behind", "Behind plan by ${formatMoney(-diff)}", -1)
        else -> PlanStatus("On plan", "On plan", 0)
    }
}

@Composable
fun PlanPill(status: PlanStatus, textColor: Color, background: Color, long: Boolean = false) {
    val tone = when (status.direction) { 1 -> Tone.SUCCESS; -1 -> Tone.CAUTION; else -> Tone.NEUTRAL }
    Text(
        if (long) status.long else status.short,
        style = MaterialTheme.typography.labelMedium,
        color = toneColors(tone).fg,
        maxLines = 1
    )
}
