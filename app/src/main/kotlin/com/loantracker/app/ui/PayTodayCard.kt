package com.loantracker.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loantracker.app.data.Loan
import com.loantracker.app.data.Payment
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DayStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

// Deep coral keeps white text readable; the dark panels darken it further.
private val HeroTop = Color(0xFFA8300C)
private val HeroMid = Color(0xFFC7400F)
private val HeroBottom = Color(0xFFE0561F)
private val HeroGold = Color(0xFFFFE08A)
private val PanelDark = Color.Black.copy(alpha = 0.28f)

/** Late penalty assumption until a per-lender rate is stored. */
private const val LATE_PENALTY_PERCENT = 5.0

private data class PayOption(val label: String, val amount: Double)

/**
 * The dashboard's main call to action: the amount to pay today (changes with the chosen plan),
 * a one-line reason, three amounts to pick from, the repayment plan chart, collapsible payment
 * habits, and a fine-print penalty estimate.
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
    val daysLeft = loan.daysRemaining
    val days = daysLeft.coerceAtLeast(1L)
    val rec = ceil(balance / days)

    val amounts = remember(payments) { payments.map { it.amount }.sorted() }
    val median = amounts.getOrNull(amounts.size / 2) ?: rec
    val p75 = amounts.getOrNull(min(amounts.lastIndex, (amounts.size * 0.75).toInt())) ?: rec
    val usual = min(balance, max(rec, (median / 50).roundToLong() * 50.0))
    val stretch = min(balance, max(usual + 100, (p75 / 100).roundToLong() * 100.0))
    val options = remember(rec, usual, stretch) {
        val raw = listOf(PayOption("Minimum", rec), PayOption("Your usual", usual), PayOption("Stretch", stretch))
        raw.filterIndexed { i, o -> i == 0 || o.amount > raw[i - 1].amount }
    }

    var picked by rememberSaveable(loan.id) { mutableStateOf(0) }
    var habitsOpen by rememberSaveable(loan.id) { mutableStateOf(false) }
    val index = picked.coerceIn(0, options.lastIndex)
    val chosen = options[index].amount
    val after = (balance - chosen).coerceAtLeast(0.0)
    val tomorrow = if (days > 1) ceil(balance / (days - 1)) else 0.0
    val newDaily = if (days > 1) ceil(after / (days - 1)) else 0.0
    val penalty = balance * LATE_PENALTY_PERCENT / 100.0
    val repaidAfter = (((paid + chosen) / loan.totalPayable) * 100).toInt().coerceIn(0, 100)
    val dueShort = loan.dueDate.format(DateTimeFormatter.ofPattern("d MMM"))

    val insight = when {
        index == 0 && days > 1 -> "On pace for $dueShort — skip today and tomorrow jumps to ${formatMoney(tomorrow)}."
        index == 0 -> "Due today — pay now to dodge the late fee."
        after <= 0.0 -> "Clears this loan today. Nice."
        else -> "Takes you to $repaidAfter% repaid, then ${formatMoney(newDaily)} a day."
    }

    // Count up once when the card first appears; switching plans updates the number instantly.
    val shown = remember { Animatable(0f) }
    var firstRun by remember { mutableStateOf(true) }
    LaunchedEffect(chosen) {
        if (firstRun) {
            firstRun = false
            shown.animateTo(chosen.toFloat(), tween(700))
        } else {
            shown.snapTo(chosen.toFloat())
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(0f to HeroTop, 0.55f to HeroMid, 1f to HeroBottom))
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    loan.title,
                    modifier = Modifier.weight(1f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.95f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.size(10.dp))
                DaysChip(daysLeft)
            }
            Spacer(Modifier.height(7.dp))
            Text(
                "Pay ${formatMoney(shown.value.toDouble())} today",
                fontSize = 31.sp,
                lineHeight = 35.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            Spacer(Modifier.height(3.dp))
            Crossfade(targetState = insight, animationSpec = tween(250), label = "insight") { text ->
                Text(text, fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.96f))
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
                            .border(1.dp, if (on) Color.White else Color.White.copy(alpha = 0.53f), RoundedCornerShape(14.dp))
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
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    LegendItem(dashed = false, label = "Your plan")
                    LegendItem(dashed = true, label = "On pace")
                }
                PlanChart(balance, after, days.toInt(), dueShort)
            }

            Panel {
                val totals = DayOfWeek.values().associateWith { d ->
                    payments.filter { it.paidAt.dayOfWeek == d }.sumOf { it.amount }
                }
                val best = totals.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key
                val top = (totals.values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { habitsOpen = !habitsOpen },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (habitsOpen) "▾ " else "▸ ", color = HeroGold, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "Payment habits" + (best?.let { " · most on ${it.getDisplayName(DayStyle.FULL, Locale.ENGLISH)}s" } ?: ""),
                        fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White
                    )
                }
                AnimatedVisibility(habitsOpen) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                            .height(max(3.0, 40.0 * (totals.getValue(d) / top)).dp)
                                            .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                            .background(if (d == best) Color(0xFFFFC24B) else Color.White.copy(alpha = 0.7f))
                                    )
                                    Text(d.name.first().toString(), fontSize = 11.sp, color = Color.White, textAlign = TextAlign.Center)
                                }
                            }
                        }
                        if (options.size > 1) {
                            Text(
                                "Your usual is up to ${formatMoney(usual)}, and ${formatMoney(stretch)} is a real stretch.",
                                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { onPayNow(chosen) },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = HeroTop)
            ) {
                Text("Pay ${formatMoney(chosen)} now", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            }
            Text(
                "Past due, an estimated ${LATE_PENALTY_PERCENT.toInt()}% fee (about ${formatMoney(penalty)}) may apply — your lender’s actual terms win.",
                modifier = Modifier.padding(top = 10.dp),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun DaysChip(daysLeft: Long) {
    val hot = daysLeft <= 2
    val label = if (daysLeft <= 0) "Overdue" else "$daysLeft day${if (daysLeft == 1L) "" else "s"} left"
    val edge = if (hot) Color(0xFFFFC24B) else Color.White.copy(alpha = 0.33f)
    Text(
        label,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (hot) Color(0xFFFFC24B) else Color.Black.copy(alpha = 0.30f))
            .border(1.dp, edge, CircleShape)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        color = if (hot) Color(0xFF4A2A00) else Color.White
    )
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
private fun LegendItem(dashed: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(16.dp, 4.dp)) {
            drawLine(
                color = if (dashed) Color.White else HeroGold,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = (if (dashed) 2.dp else 3.dp).toPx(),
                cap = StrokeCap.Round,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null
            )
        }
        Spacer(Modifier.size(6.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun PlanChart(balance: Double, after: Double, days: Int, dueShort: String) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(122.dp)
            .semantics { contentDescription = "Repayment plan: balance from today to the due date" }
    ) {
        val pad = 12.dp.toPx()
        val base = 118.dp.toPx()
        val span = 98.dp.toPx()
        val w = size.width
        fun x(day: Int) = pad + (day.toFloat() / days.coerceAtLeast(1)) * (w - 2 * pad)
        fun y(v: Double) = base - (v / balance.coerceAtLeast(1.0)).toFloat() * span

        listOf(54f to 0.13f, 86f to 0.13f, 118f to 0.33f).forEach { (gy, a) ->
            drawLine(Color.White.copy(alpha = a), Offset(pad, gy.dp.toPx()), Offset(w - pad, gy.dp.toPx()), 1.dp.toPx())
        }
        val area = Path().apply {
            moveTo(x(0), y(balance)); lineTo(x(0), y(after)); lineTo(x(days), y(0.0))
            lineTo(x(days), base); lineTo(x(0), base); close()
        }
        drawPath(area, HeroGold.copy(alpha = 0.18f), style = Fill)
        drawLine(
            Color.White.copy(alpha = 0.7f), Offset(x(0), y(balance)), Offset(x(days), y(0.0)), 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
        )
        val plan = Path().apply { moveTo(x(0), y(balance)); lineTo(x(0), y(after)); lineTo(x(days), y(0.0)) }
        drawPath(plan, HeroGold, style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(HeroGold, 5.dp.toPx(), Offset(x(0), y(after)))
        drawCircle(HeroGold, 4.dp.toPx(), Offset(x(days), y(0.0)))
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Today · ${formatMoney(balance)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Due $dueShort", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}
