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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        ) {
            Column {
                Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "v${info.version}" + (if (sizeMb > 0) " · %.1f MB".format(sizeMb) else "") +
                            " · you have v${controller.currentVersion}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp)) {
                    if (failed != null) {
                        Text(failed.message, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text("What’s new", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                            CenterActions { TextButton(contentPadding = PaddingValues(8.dp), onClick = { controller.dismiss() }) { Tiny("Close") } }
                        }
                        downloading || ready -> {
                            ProgressBlock(progress, ready, sizeMb)
                            if (ready) {
                                Spacer(Modifier.height(12.dp))
                                UpdateButton("Install", null, onInstall)
                                CenterActions { TextButton(contentPadding = PaddingValues(8.dp), onClick = { controller.dismiss() }) { Tiny("Later") } }
                            } else {
                                CenterActions { TextButton(contentPadding = PaddingValues(8.dp), onClick = { controller.cancel() }) { Tiny("Cancel") } }
                            }
                        }
                        else -> {
                            UpdateButton("Update now", null) { controller.startDownload(info) }
                            CenterActions {
                                TextButton(contentPadding = PaddingValues(8.dp), onClick = { controller.dismiss() }) { Tiny("Later") }
                                TextButton(contentPadding = PaddingValues(8.dp), onClick = { controller.skip(info) }) { Tiny("Skip this version") }
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
            modifier = Modifier.padding(top = 6.dp, end = 9.dp).size(7.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiary)
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
                fontWeight = FontWeight.SemiBold
            )
            if (sizeMb > 0) {
                Text(
                    "%.1f of %.1f MB".format(sizeMb * progress, sizeMb),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun UpdateButton(text: String, icon: ImageVector?, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}
