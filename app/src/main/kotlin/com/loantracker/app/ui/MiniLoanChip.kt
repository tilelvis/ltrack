package com.loantracker.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loantracker.app.data.Loan

/**
 * Slim one-line stand-in for the front loan card: a progress ring plus the balance. It sits in the
 * top bar while the full card is scrolled out of view, shares the card's gradient so it reads as
 * the same loan, and scrolls back to the card when tapped.
 */
@Composable
fun MiniLoanChip(
    loan: Loan,
    paid: Double,
    paletteIndex: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = loanCardPalette(paletteIndex)
    val shape = RoundedCornerShape(50)
    val balance = (loan.totalPayable - paid).coerceAtLeast(0.0)
    val progress =
        if (loan.totalPayable > 0) (paid / loan.totalPayable).coerceIn(0.0, 1.0).toFloat() else 0f

    Row(
        modifier = modifier
            .padding(end = 12.dp)
            .widthIn(max = 150.dp)
            .shadow(6.dp, shape)
            .clip(shape)
            .background(palette.brush)
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)), shape)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)
            .semantics {
                contentDescription = "${loan.title}, ${formatMoney(balance)} outstanding. Tap to show the loan card."
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Canvas(modifier = Modifier.size(16.dp)) {
            val sw = 2.5.dp.toPx()
            val inset = sw / 2
            val arc = Size(size.width - sw, size.height - sw)
            drawArc(Color.White.copy(alpha = 0.3f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(sw))
            drawArc(
                palette.accent, -90f, 360f * progress, false, Offset(inset, inset), arc,
                style = Stroke(sw, cap = StrokeCap.Round)
            )
        }
        Text(
            formatMoney(balance),
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            maxLines = 1
        )
    }
}
