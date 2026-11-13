package com.loantracker.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loantracker.app.data.Loan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Container / text / accent colors for one loan card. Each loan keeps its own color. */
data class CardPalette(val container: Color, val content: Color, val accent: Color)

@Composable
fun loanCardPalette(index: Int): CardPalette {
    val s = MaterialTheme.colorScheme
    val dark = isDarkTheme()
    return when (index % 4) {
        0 -> CardPalette(s.primaryContainer, s.onPrimaryContainer, s.primary)
        1 -> if (dark) CardPalette(Color(0xFF203645), Color(0xFFCFE5F3), Color(0xFF8CC4E6))
             else CardPalette(Color(0xFFDCEBF5), Color(0xFF12303F), Color(0xFF2F6F96))
        2 -> CardPalette(s.tertiaryContainer, s.onTertiaryContainer, s.tertiary)
        else -> if (dark) CardPalette(Color(0xFF2C2840), Color(0xFFE2DDF5), Color(0xFFB3A5F0))
                else CardPalette(Color(0xFFE6E1F4), Color(0xFF231A45), Color(0xFF6C55C9))
    }
}

/**
 * Wallet-style deck of loan cards. The front card shows full details; the next cards peek out
 * behind it. Swipe the front card sideways to bring another loan forward, or tap a peeking card.
 */
@Composable
fun LoanCardStack(
    loans: List<Loan>,
    paidByLoan: Map<String, Double>,
    frontIndex: Int,
    onSelect: (Int) -> Unit,
    onOpen: (Loan) -> Unit,
    modifier: Modifier = Modifier
) {
    val n = loans.size
    if (n == 0) return

    val maxDepth = minOf(n - 1, 2)
    val peek = 20.dp
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var widthPx by remember { mutableStateOf(1f) }
    val haptic = LocalHapticFeedback.current

    fun step(dir: Int) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onSelect(((frontIndex + dir) % n + n) % n)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = peek * maxDepth)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
    ) {
        for (depth in maxDepth downTo 0) {
            val idx = (frontIndex + depth) % n
            val loan = loans[idx]
            key(loan.id) {
                StackCard(
                    loan = loan,
                    paid = paidByLoan[loan.id] ?: 0.0,
                    palette = loanCardPalette(idx),
                    depth = depth,
                    position = frontIndex,
                    total = n,
                    peek = peek,
                    offsetX = offsetX,
                    widthPx = widthPx,
                    scope = scope,
                    onSelectSelf = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(idx)
                    },
                    onOpen = { onOpen(loan) },
                    onStep = { dir -> step(dir) }
                )
            }
        }
    }
}

@Composable
private fun BoxScope.StackCard(
    loan: Loan,
    paid: Double,
    palette: CardPalette,
    depth: Int,
    position: Int,
    total: Int,
    peek: Dp,
    offsetX: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    widthPx: Float,
    scope: CoroutineScope,
    onSelectSelf: () -> Unit,
    onOpen: () -> Unit,
    onStep: (Int) -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = 1f - 0.05f * depth,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "stackScale"
    )
    val lift by animateFloatAsState(
        targetValue = depth.toFloat(),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "stackLift"
    )
    val faceAlpha by animateFloatAsState(
        targetValue = if (depth == 0) 1f else 0f,
        animationSpec = tween(220),
        label = "stackFace"
    )

    val balance = (loan.totalPayable - paid).coerceAtLeast(0.0)
    val isFront = depth == 0

    val sizeModifier = if (isFront) Modifier.fillMaxWidth() else Modifier.matchParentSize()

    val dragModifier = if (isFront && total > 1) {
        Modifier.pointerInput(position, total, widthPx) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    scope.launch {
                        val threshold = widthPx * 0.28f
                        when {
                            offsetX.value < -threshold -> {
                                offsetX.animateTo(-widthPx * 1.2f, tween(170))
                                onStep(1)
                                offsetX.snapTo(0f)
                            }
                            offsetX.value > threshold -> {
                                offsetX.animateTo(widthPx * 1.2f, tween(170))
                                onStep(-1)
                                offsetX.snapTo(0f)
                            }
                            else -> offsetX.animateTo(0f, spring(Spring.DampingRatioMediumBouncy))
                        }
                    }
                },
                onDragCancel = { scope.launch { offsetX.animateTo(0f, spring()) } },
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                }
            )
        }
    } else Modifier

    val semanticsModifier = if (isFront) {
        Modifier.semantics {
            contentDescription =
                "${loan.title}, ${formatMoney(balance)} outstanding, loan ${position + 1} of $total"
            if (total > 1) {
                customActions = listOf(
                    CustomAccessibilityAction("Next loan") { onStep(1); true },
                    CustomAccessibilityAction("Previous loan") { onStep(-1); true }
                )
            }
        }
    } else Modifier

    Card(
        modifier = sizeModifier
            .graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, 1f)
                scaleX = scale
                scaleY = scale
                translationY = lift * peek.toPx()
                if (isFront) {
                    translationX = offsetX.value
                    rotationZ = offsetX.value / widthPx * 6f
                }
            }
            .then(semanticsModifier)
            .then(dragModifier)
            .clickable(onClick = if (isFront) onOpen else onSelectSelf),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = palette.container),
        border = BorderStroke(1.dp, palette.content.copy(alpha = 0.10f))
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            LoanCardFace(
                loan = loan,
                paid = paid,
                palette = palette,
                modifier = Modifier.graphicsLayer { alpha = faceAlpha }
            )
            // Only the title shows on the sliver of a card that sits behind the front one.
            Text(
                loan.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = palette.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 5.dp)
                    .graphicsLayer { alpha = 1f - faceAlpha }
            )
        }
    }
}

@Composable
private fun LoanCardFace(
    loan: Loan,
    paid: Double,
    palette: CardPalette,
    modifier: Modifier = Modifier
) {
    val balance = (loan.totalPayable - paid).coerceAtLeast(0.0)
    val progress =
        if (loan.totalPayable > 0) (paid / loan.totalPayable).coerceIn(0.0, 1.0).toFloat() else 0f
    val percent = (progress * 100).toInt()
    val days = loan.daysRemaining

    // Count-up and fill animation each time the card appears or the numbers change.
    val shownBalance = remember { Animatable(0f) }
    val shownProgress = remember { Animatable(0f) }
    LaunchedEffect(balance, progress) {
        launch { shownBalance.animateTo(balance.toFloat(), tween(800)) }
        launch { shownProgress.animateTo(progress, tween(900)) }
    }

    Column(modifier = modifier.padding(20.dp)) {
        Text(
            loan.title,
            style = MaterialTheme.typography.titleSmall,
            color = palette.content.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            formatMoney(shownBalance.value.toDouble()),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = palette.content
        )
        Text(
            "of ${formatMoney(loan.totalPayable)}",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.content.copy(alpha = 0.75f)
        )
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { shownProgress.value },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = palette.accent,
            trackColor = palette.content.copy(alpha = 0.15f)
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
                color = palette.content
            )
            Text(
                daysLeftText(days),
                style = MaterialTheme.typography.bodyMedium,
                color = if (days <= 0) MaterialTheme.colorScheme.error else palette.content
            )
        }
    }
}

/** Page dots under the stack: the selected loan is a wider pill, tap a dot to jump to it. */
@Composable
fun LoanDots(
    count: Int,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(count) { i ->
                val w by animateDpAsState(if (i == selected) 22.dp else 8.dp, label = "dotW")
                Box(
                    modifier = Modifier
                        .size(width = w, height = 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (i == selected) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                        .clickable { onSelect(i) }
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "${selected + 1} of $count loans · swipe the card to switch",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
