package com.loantracker.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.loantracker.app.services.UpdateController
import com.loantracker.app.services.UpdateController.State
import com.loantracker.app.services.UpdateController.UpdateInfo

private val BtnStart = Color(0xFFD9461A)
private val BtnEnd = Color(0xFFFF6A3D)
private val HeroGoldSoft = Color(0xFFFFE08A)

/**
 * One persistent update card for the whole flow. The hero illustration and layout stay put while
 * the state moves Available -> Downloading -> Ready, so nothing flickers or closes mid-download.
 */
@Composable
fun UpdateCardDialog(
    controller: UpdateController,
    state: State,
    info: UpdateInfo,
    onInstall: () -> Unit
) {
    val downloading = state is State.Downloading
    val ready = state is State.Ready
    val failed = state as? State.Failed
    val rawProgress = when (state) {
        is State.Downloading -> state.progress
        is State.Ready -> 1f
        else -> 0f
    }
    val progress by animateFloatAsState(rawProgress, tween(250), label = "dl")
    val ring by animateFloatAsState(if (downloading || ready) rawProgress else 0.82f, tween(250), label = "ring")
    val title = when {
        failed != null -> "Update problem"
        ready -> "Ready to install"
        downloading -> "Getting it ready"
        else -> "Update ready"
    }
    val sizeMb = info.sizeBytes / 1_048_576.0

    Dialog(
        onDismissRequest = { if (!downloading) controller.dismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !downloading,
            dismissOnClickOutside = !downloading,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier.padding(horizontal = 20.dp).widthIn(max = 400.dp).fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column {
                Box(modifier = Modifier.fillMaxWidth().height(190.dp)) {
                    UpdateHero(ring, ready, Modifier.matchParentSize())
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(0.62f)
                            .padding(start = 18.dp, bottom = 14.dp)
                    ) {
                        Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Text(
                            "v${info.version}" + (if (sizeMb > 0) " · %.1f MB".format(sizeMb) else "") +
                                " · you have v${controller.currentVersion}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
                Column(modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 10.dp)) {
                    if (failed != null) {
                        Text(failed.message, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text("What’s new", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(6.dp))
                        val (lines, more) = changelog(info.notes)
                        lines.forEach { Bullet(it) }
                        if (more > 0) {
                            Text(
                                "…and $more more",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, top = 2.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    when {
                        failed != null -> {
                            UpdateButton("Retry", null) { controller.startDownload(info) }
                            CenterActions { TextButton(onClick = { controller.dismiss() }) { Tiny("Close") } }
                        }
                        downloading || ready -> {
                            ProgressBlock(progress, ready, sizeMb)
                            if (ready) {
                                Spacer(Modifier.height(12.dp))
                                UpdateButton("Install", null, onInstall)
                                CenterActions { TextButton(onClick = { controller.dismiss() }) { Tiny("Later") } }
                            } else {
                                CenterActions { TextButton(onClick = { controller.cancel() }) { Tiny("Cancel") } }
                            }
                        }
                        else -> {
                            UpdateButton("Update now", Icons.Filled.Download) { controller.startDownload(info) }
                            CenterActions {
                                TextButton(onClick = { controller.dismiss() }) { Tiny("Later") }
                                TextButton(onClick = { controller.skip(info) }) { Tiny("Skip this version") }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Up to four short changelog lines: the bullet lines of the release notes, else the first lines. */
private fun changelog(notes: String): Pair<List<String>, Int> {
    val lines = notes.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val bullets = lines.filter { it.startsWith("- ") || it.startsWith("* ") }.map { it.drop(2).trim() }
    val source = bullets.ifEmpty { lines.drop(1).ifEmpty { lines } }
    return source.take(4) to (source.size - 4).coerceAtLeast(0)
}

@Composable
private fun Bullet(text: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier.padding(top = 6.dp, end = 9.dp).size(7.dp).clip(CircleShape).background(BtnEnd)
        )
        Text(
            text,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun Tiny(text: String) =
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun CenterActions(content: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { content() }
}

@Composable
private fun ProgressBlock(progress: Float, ready: Boolean, sizeMb: Double) {
    val pct = (progress * 100).toInt()
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (ready) "Downloaded · 100%" else "Downloading · $pct%",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            if (sizeMb > 0) {
                Text(
                    "%.1f of %.1f MB".format(sizeMb * progress, sizeMb),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.outlineVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(BtnStart, Color(0xFFFF9A4A))))
            )
        }
    }
}

@Composable
private fun UpdateButton(text: String, icon: ImageVector?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val sheen by rememberInfiniteTransition(label = "sheen").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "sheenX"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .shadow(12.dp, shape, ambientColor = BtnStart, spotColor = BtnStart)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(BtnStart, BtnEnd)))
            .drawWithContent {
                drawContent()
                val x = size.width * (sheen * 1.6f - 0.3f)
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.28f), Color.Transparent),
                        start = Offset(x, 0f),
                        end = Offset(x + size.width * 0.3f, size.height)
                    )
                )
            }
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.size(8.dp))
        }
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
    }
}

/** Loan-repayment illustration: rising bars, a repayment ring that fills with the download, coins. */
@Composable
private fun UpdateHero(ringProgress: Float, done: Boolean, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val label = if (done) "✓" else "KSh"
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawRect(
            Brush.linearGradient(
                listOf(Color(0xFF052E21), Color(0xFF0B6B4B), Color(0xFF159A6E)),
                start = Offset.Zero,
                end = Offset(w, h)
            )
        )
        drawRect(
            Brush.radialGradient(
                listOf(Color(0xFFFF6A3D).copy(alpha = 0.6f), Color.Transparent),
                center = Offset(w * 0.85f, h),
                radius = w * 0.7f
            )
        )
        listOf(50f, 70f, 92f, 116f).forEachIndexed { i, bh ->
            drawRoundRect(
                Color.White.copy(alpha = 0.14f),
                topLeft = Offset(20.dp.toPx() + i * 34.dp.toPx(), h - 20.dp.toPx() - bh.dp.toPx()),
                size = Size(26.dp.toPx(), bh.dp.toPx()),
                cornerRadius = CornerRadius(6.dp.toPx())
            )
        }
        val c = Offset(w * 0.75f, h * 0.37f)
        val r = 40.dp.toPx()
        val sw = 9.dp.toPx()
        drawCircle(Color.White.copy(alpha = 0.22f), r, c, style = Stroke(sw))
        drawArc(
            brush = Brush.linearGradient(
                listOf(Color(0xFFFF7A4D), HeroGoldSoft),
                start = Offset(c.x - r, c.y - r),
                end = Offset(c.x + r, c.y + r)
            ),
            startAngle = -90f,
            sweepAngle = 360f * ringProgress.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = Offset(c.x - r, c.y - r),
            size = Size(2 * r, 2 * r),
            style = Stroke(sw, cap = StrokeCap.Round)
        )
        val text = measurer.measure(
            label,
            TextStyle(fontSize = if (done) 30.sp else 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        )
        drawText(text, topLeft = Offset(c.x - text.size.width / 2f, c.y - text.size.height / 2f))

        val cx = w * 0.83f
        val cw = 30.dp.toPx()
        val ch = 9.dp.toPx()
        listOf(
            Color(0xFFC98A12) to 0f, Color(0xFFFFC24B) to -7f,
            Color(0xFFC98A12) to -14f, Color(0xFFFFD36E) to -21f
        ).forEach { (col, dy) ->
            drawOval(col, Offset(cx - cw, h - 26.dp.toPx() + dy.dp.toPx()), Size(cw * 2, ch * 2))
        }
        listOf(
            Triple(0.55f, 0.16f, HeroGoldSoft), Triple(0.94f, 0.14f, HeroGoldSoft),
            Triple(0.59f, 0.63f, HeroGoldSoft), Triple(0.63f, 0.30f, Color(0xFFFF8A5B)),
            Triple(0.94f, 0.62f, Color(0xFFFF8A5B)), Triple(0.50f, 0.50f, Color(0xFFFF8A5B))
        ).forEach { (fx, fy, col) -> drawCircle(col, 3.dp.toPx(), Offset(w * fx, h * fy)) }

        drawRect(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.65f)))
    }
}
