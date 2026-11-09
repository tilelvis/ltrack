package com.loantracker.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import kotlin.random.Random

/** Shown once when a loan becomes fully repaid (via a payment, or a past loan marked repaid). */
data class Celebration(
    val loanTitle: String,
    val totalRepaid: Double,
    val daysTaken: Long,
    /** Positive = days before the due date, 0 = on the due date, negative = days late. */
    val daysEarly: Long,
    val completedCount: Int,
    val pastLoan: Boolean
)

private class Particle(val x: Float, val phase: Float, val speed: Float, val size: Float, val color: Int)

@Composable
fun CelebrationDialog(celebration: Celebration, onDismiss: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(celebration) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val timing = when {
        celebration.daysEarly > 0L ->
            "${celebration.daysEarly} ${if (celebration.daysEarly == 1L) "day" else "days"} ahead of the due date"
        celebration.daysEarly == 0L -> "Right on the due date"
        else -> "${-celebration.daysEarly} ${if (-celebration.daysEarly == 1L) "day" else "days"} past the due date, but you finished it"
    }
    val encouragement = when {
        celebration.completedCount <= 1 -> "Your first loan cleared. That's how it starts."
        celebration.completedCount < 5 -> "${celebration.completedCount} loans cleared. Keep that momentum."
        else -> "${celebration.completedCount} loans cleared. That's real discipline."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss) { Text("Awesome") }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ConfettiTrophy()
                Text(
                    "Loan cleared!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    celebration.loanTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            formatMoney(celebration.totalRepaid),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "repaid in full",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    timing,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Loan period: ${celebration.daysTaken} ${if (celebration.daysTaken == 1L) "day" else "days"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    encouragement,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                if (celebration.pastLoan) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Saved to your completed loans.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    )
}

@Composable
private fun ConfettiTrophy() {
    val scheme = MaterialTheme.colorScheme
    val palette = listOf(
        scheme.primary,
        scheme.tertiary,
        scheme.secondary,
        toneColors(Tone.ELEVATED).fg
    )
    val particles = remember {
        List(28) {
            Particle(
                x = Random.nextFloat(),
                phase = Random.nextFloat(),
                speed = 0.5f + Random.nextFloat() * 0.8f,
                size = 3f + Random.nextFloat() * 4f,
                color = Random.nextInt(4)
            )
        }
    }
    val t by rememberInfiniteTransition().animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing))
    )
    val scale = remember { Animatable(0.3f) }
    LaunchedEffect(Unit) {
        scale.animateTo(
            1f,
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        )
    }

    Box(
        modifier = Modifier.fillMaxWidth().height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            particles.forEach { p ->
                val y = ((p.phase + t * p.speed) % 1f) * size.height
                val x = p.x * size.width + sin((t + p.phase) * 12.566f) * 8f
                drawCircle(
                    color = palette[p.color].copy(alpha = 0.85f),
                    radius = p.size,
                    center = Offset(x, y)
                )
            }
        }
        Icon(
            Icons.Filled.EmojiEvents,
            contentDescription = null,
            tint = scheme.tertiary,
            modifier = Modifier
                .size(68.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
        )
    }
}
