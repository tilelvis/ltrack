package com.loantracker.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * The app's signature detail: a dotted ledger leader between a label and its figure,
 * like a line in a receipt book. Used sparingly, only where a label meets a number.
 */
@Composable
fun LedgerLine(
    left: String,
    right: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelMedium,
    leftColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    rightColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val dots = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Text(left, style = style, color = leftColor, maxLines = 1)
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(12.dp)
                .padding(horizontal = 8.dp)
        ) {
            val y = size.height - 3.dp.toPx()
            drawLine(
                color = dots,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 4.dp.toPx()))
            )
        }
        Text(right, style = style, color = rightColor, maxLines = 1)
    }
}
