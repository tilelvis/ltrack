package com.loantracker.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.loantracker.app.data.Loan
import com.loantracker.app.data.Payment
import com.loantracker.app.services.PenaltyPrefs
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DayStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

private data class PayOption(val label: String, val amount: Double)

/**
 * The dashboard's main call to action: the amount to pay today (changes with the chosen plan),
 * a one-line reason, three amounts to pick from, the repayment plan chart, collapsible payment
 * habits, and a fine-print penalty estimate. The chart is the one bright element; the rest is quiet.
 */
@Composable
fun PayTodayCard(
    loan: Loan,
    paid: Double,
    payments: List<Payment>,
    onPayNow: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val penaltyPct = remember { PenaltyPrefs.get(context) }
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
    val penalty = balance * penaltyPct / 100.0
    val repaidAfter = (((paid + chosen) / loan.totalPayable) * 100).toInt().coerceIn(0, 100)
    val dueShort = loan.dueDate.format(DateTimeFormatter.ofPattern("d MMM"))

    val insight = when {
        index == 0 && days > 1 -> "On pace for $dueShort. Skip today and tomorrow is ${formatMoney(tomorrow)}"
        index == 0 -> "Due today. Pay now to avoid the late fee"
        after <= 0.0 -> "Clears this loan today"
        else -> "Takes you to $repaidAfter% repaid, then ${formatMoney(newDaily)} a day"
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

    val hairline = cs.onSurface.copy(alpha = 0.08f)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant),
        border = BorderStroke(1.dp, hairline)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    loan.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    daysLeftText(daysLeft),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (daysLeft <= 2) accentTextColor() else cs.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Pay ${formatMoney(shown.value.toDouble())} today",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Crossfade(targetState = insight, animationSpec = tween(250), label = "insight") { text ->
                Text(text, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }

            Spacer(Modifier.height(16.dp))
            // Segmented control: the selected segment is a small surface step with a hairline.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(cs.surfaceContainerHigh)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                options.forEachIndexed { i, o ->
                    val on = i == index
                    val seg = RoundedCornerShape(8.dp)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(seg)
                            .background(if (on) cs.surface else Color.Transparent)
                            .then(if (on) Modifier.border(1.dp, hairline, seg) else Modifier)
                            .clickable { picked = i }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(o.label, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                        Text(
                            formatMoney(o.amount),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.onSurface
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                LegendItem(dashed = false, label = "Your plan", color = cs.primary)
                LegendItem(dashed = true, label = "On pace", color = cs.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            PlanChart(balance, after, days.toInt(), cs.primary, cs.onSurfaceVariant, cs.onSurface.copy(alpha = 0.10f))
            Spacer(Modifier.height(4.dp))
            LedgerLine(left = "Today ${formatMoney(balance)}", right = "Due $dueShort", modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))

            val totals = DayOfWeek.values().associateWith { d ->
                payments.filter { it.paidAt.dayOfWeek == d }.sumOf { it.amount }
            }
            val best = totals.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key
            val top = (totals.values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { habitsOpen = !habitsOpen }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Payment habits" + (best?.let { " · most on ${it.getDisplayName(DayStyle.FULL, Locale.ENGLISH)}s" } ?: ""),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.onSurface
                )
                Icon(
                    if (habitsOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (habitsOpen) "Hide payment habits" else "Show payment habits",
                    tint = cs.onSurfaceVariant
                )
            }
            AnimatedVisibility(habitsOpen) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        DayOfWeek.values().forEach { d ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(max(4.0, 48.0 * (totals.getValue(d) / top)).dp)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(if (d == best) cs.primary else cs.onSurface.copy(alpha = 0.18f))
                            )
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DayOfWeek.values().forEach { d ->
                            Text(
                                d.name.first().toString(),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    if (options.size > 1) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Usual up to ${formatMoney(usual)}, stretch ${formatMoney(stretch)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onPayNow(chosen) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = cs.onSurface, contentColor = cs.surface)
            ) {
                Text("Pay ${formatMoney(chosen)} now", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(
                if (penaltyPct > 0) "Past due, about ${penaltyPct.toInt()}% (${formatMoney(penalty)}) may apply. Your lender’s terms win."
                else "Late fees aren’t estimated. Set one in Settings. Your lender’s terms win.",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LegendItem(dashed: Boolean, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(16.dp, 4.dp)) {
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = (if (dashed) 2.dp else 3.dp).toPx(),
                cap = StrokeCap.Round,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PlanChart(balance: Double, after: Double, days: Int, plan: Color, pace: Color, grid: Color) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .semantics { contentDescription = "Repayment plan: balance from today to the due date" }
    ) {
        val pad = 12.dp.toPx()
        val base = 116.dp.toPx()
        val span = 96.dp.toPx()
        val w = size.width
        fun x(day: Int) = pad + (day.toFloat() / days.coerceAtLeast(1)) * (w - 2 * pad)
        fun y(v: Double) = base - (v / balance.coerceAtLeast(1.0)).toFloat() * span

        listOf(20f, 68f, 116f).forEach { gy ->
            drawLine(grid, Offset(pad, gy.dp.toPx()), Offset(w - pad, gy.dp.toPx()), 1.dp.toPx())
        }
        val area = Path().apply {
            moveTo(x(0), y(balance)); lineTo(x(0), y(after)); lineTo(x(days), y(0.0))
            lineTo(x(days), base); lineTo(x(0), base); close()
        }
        drawPath(area, plan.copy(alpha = 0.10f), style = Fill)
        drawLine(
            pace.copy(alpha = 0.7f), Offset(x(0), y(balance)), Offset(x(days), y(0.0)), 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
        )
        val line = Path().apply { moveTo(x(0), y(balance)); lineTo(x(0), y(after)); lineTo(x(days), y(0.0)) }
        drawPath(line, plan, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(plan, 5.dp.toPx(), Offset(x(0), y(after)))
        drawCircle(plan, 4.dp.toPx(), Offset(x(days), y(0.0)))
    }
}
