package com.loantracker.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loantracker.app.data.Loan

/**
 * Compact stand-in for the front loan card. It sits in the top bar while the full card is
 * scrolled out of view, uses the same gradient so it reads as the same loan, and scrolls back
 * to the card when tapped.
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
    val shape = RoundedCornerShape(16.dp)
    val balance = (loan.totalPayable - paid).coerceAtLeast(0.0)
    val progress =
        if (loan.totalPayable > 0) (paid / loan.totalPayable).coerceIn(0.0, 1.0).toFloat() else 0f

    Column(
        modifier = modifier
            .padding(end = 12.dp)
            .widthIn(max = 160.dp)
            .shadow(10.dp, shape)
            .clip(shape)
            .background(palette.brush)
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics { contentDescription = "${loan.title}, ${formatMoney(balance)} outstanding. Tap to show the loan card." }
    ) {
        Text(
            loan.title,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            formatMoney(balance),
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            maxLines = 1
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.padding(top = 3.dp).height(3.dp).widthIn(min = 96.dp, max = 136.dp),
            color = palette.accent,
            trackColor = Color.White.copy(alpha = 0.3f)
        )
    }
}
