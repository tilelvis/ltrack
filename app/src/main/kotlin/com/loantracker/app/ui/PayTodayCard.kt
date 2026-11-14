package com.loantracker.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loantracker.app.data.Loan
import com.loantracker.app.data.Payment
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

// Deep coral keeps white text readable (about 4.4:1 at the light end); panels darken it further.
private val HeroTop = Color(0xFFB8330D)
private val HeroBottom = Color(0xFFD9461A)
private val HeroGold = Color(0xFFFFE08A)
private val PanelDark = Color.Black.copy(alpha = 0.28f)

/** Late penalty assumption until a per-lender rate is stored. */
private const val LATE_PENALTY_PERCENT = 5.0

private data class PayOption(val label: String, val amount: Double)

/**
 * The dashboard's main call to action: what to pay today, what skipping costs, how each amount
 * changes the path to the due date, and when this person pays best.
 */
@Composable
fun PayTodayCard(
    loan: Loan,
    paid: Double,
    payments: List<Payment>,
    onPayNow: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val balance = (loan.totalPayable - paid).coerceAtLeast(0.0)
    val days = loan.daysRemaining.coerceAtLeast(1L)
    val rec = ceil(balance / days)

    val amounts = remember(payments) { payments.map { it.amount }.sorted() }
    val median = amounts.getOrNull(amounts.size / 2) ?: rec
    val p75 = amounts.getOrNull(min(amounts.lastIndex, (amounts.size * 0.75).toInt())) ?: rec
    val usual = min(balance, max(rec, (median / 50).roundToLong() * 50.0))
    val stretch = min(balance, max(usual + 100, (p75 / 100).roundToLong() * 100.0))
    val options = remember(rec, usual, stretch) {
        listOf(PayOption("Minimum", rec), PayOption("Your usual", usual), PayOption("Stretch", stretch))
            .filterIndexed { i, o -> i == 0 || o.amount > listOf(rec, usual, stretch)[i - 1] }
    }

    var picked by rememberSaveable(loan.id) { mutableStateOf(0) }
    val index = picked.coerceIn(0, options.lastIndex)
    val chosen = options[index].amount
    val after = (balance - chosen).coerceAtLeast(0.0)
    val tomorrow = if (days > 1) ceil(balance / (days - 1)) else 0.0
    val newDaily = if (days > 1) ceil(after / (days - 1)) else 0.0
    val penalty = balance * LATE_PENALTY_PERCENT / 100.0
    val repaidAfter = (((paid + chosen) / loan.totalPayable) * 100).toInt().coerceIn(0, 100)

    val shown = remember { Animatable(0f) }
    LaunchedEffect(rec) { shown.animateTo(rec.toFloat(), tween(700)) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(HeroTop, HeroBottom)))
                .padding(20.dp)
        ) {
            Text(
                "Pay ${formatMoney(shown.value.toDouble())} today",
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${loan.title} · $days day${if (days == 1L) "" else "s"} to the due date",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            Panel {
                Text(
                    if (days > 1) "Skip today and tomorrow’s share jumps to ${formatMoney(tomorrow)}."
                    else "Due today. Pay now to avoid a penalty.",
                    fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White
                )
                Text(
                    "Miss the due date and a penalty of about ${formatMoney(penalty)} " +
                        "(${LATE_PENALTY_PERCENT.toInt()}% assumed) lands on top.",
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEachIndexed { i, o ->
                    val on = i == index
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (on) Color.White else PanelDark)
                            .clickable { picked = i }
                            .padding(vertical = 9.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(o.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            color = if (on) HeroTop else Color.White)
                        Text(formatMoney(o.amount), fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            color = if (on) HeroTop else Color.White)
                    }
                }
            }

            Panel {
                BalanceChart(balance, after, days.toInt(), loan.dueDate)
                Text(
                    when {
                        index == 0 -> "Paying ${formatMoney(rec)} keeps you exactly on pace. The dashed line is the same path."
                        after <= 0.0 -> "Paying ${formatMoney(chosen)} clears this loan today."
                        else -> "Paying ${formatMoney(chosen)} today takes you to $repaidAfter% repaid and " +
                            "drops your daily share from ${formatMoney(rec)} to ${formatMoney(newDaily)}."
                    },
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White
                )
            }

            Panel { PerformanceBlock(payments, if (options.size > 1) usual else null, stretch) }

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { onPayNow(chosen) },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = HeroTop)
            ) {
                Text("Pay ${formatMoney(chosen)} now", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(PanelDark)
            .padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content
    )
}

@Composable
private fun BalanceChart(balance: Double, after: Double, days: Int, due: LocalDate) {
    val gold = HeroGold
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
            .semantics { contentDescription = "Balance falling from today to the due date" }
    ) {
        val pad = 10.dp.toPx()
        val w = size.width
        val h = size.height
        fun x(day: Int) = pad + (day.toFloat() / days.coerceAtLeast(1)) * (w - 2 * pad)
        fun y(v: Double) = (h - pad) - (v / balance.coerceAtLeast(1.0)).toFloat() * (h - 2 * pad)
        drawLine(Color.White.copy(alpha = 0.5f), Offset(pad, h - pad), Offset(w - pad, h - pad), 1.dp.toPx())
        drawLine(
            Color.White, Offset(x(0), y(balance)), Offset(x(days), y(0.0)), 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
        )
        val path = Path().apply {
            moveTo(x(0), y(balance)); lineTo(x(0), y(after)); lineTo(x(days), y(0.0))
        }
        drawPath(path, gold, style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(gold, 5.dp.toPx(), Offset(x(0), y(after)))
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Today · ${formatMoney(balance)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Due ${due.format(DateTimeFormatter.ofPattern("d MMM"))}", fontSize = 12.sp,
            fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun PerformanceBlock(payments: List<Payment>, usual: Double?, stretch: Double) {
    val totals = DayOfWeek.values().associateWith { d ->
        payments.filter { it.paidAt.dayOfWeek == d }.sumOf { it.amount }
    }
    val best = totals.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key
    val top = (totals.values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    val today = LocalDate.now()
    val daysPaid = payments.map { it.paidAt.toLocalDate() }
        .filter { !it.isAfter(today) && !it.isBefore(today.minusDays(13)) }.distinct().size

    Text(
        "Your best day: " + (best?.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)?.plus("s")
            ?: "not set yet"),
        fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White
    )
    Row(
        modifier = Modifier.fillMaxWidth().height(62.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        DayOfWeek.values().forEach { d ->
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(max(3.0, 46.0 * (totals.getValue(d) / top)).dp)
                        .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                        .background(if (d == best) HeroGold else Color.White.copy(alpha = 0.75f))
                )
                Text(d.name.first().toString(), fontSize = 11.sp, color = Color.White, textAlign = TextAlign.Center)
            }
        }
    }
    Text(
        "Paid on $daysPaid of the last 14 days." +
            (usual?.let { " Safe zone: up to ${formatMoney(it)} matches your median payment, and ${formatMoney(stretch)} is the top of your range." } ?: ""),
        fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White
    )
}
