package com.loantracker.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.loantracker.app.data.Severity

/**
 * Single source of truth for the app's look.
 *
 * Light mode is deliberately low-glare: no pure white or pure black, a warm
 * sage-tinted background, soft containers, and dark-green (not navy/purple)
 * text on tinted surfaces. Every Material role is set so no default lavender
 * or purple leaks into the nav bar, chips, or cards.
 */
fun loanTrackerColorScheme(dark: Boolean): ColorScheme =
    if (dark) darkColorScheme(
        primary = Color(0xFF3EDC9C),
        onPrimary = Color(0xFF06271A),
        primaryContainer = Color(0xFF124A36),
        onPrimaryContainer = Color(0xFFC9EEDA),
        secondary = Color(0xFFB3CBBD),
        onSecondary = Color(0xFF1E3329),
        secondaryContainer = Color(0xFF33493D),
        onSecondaryContainer = Color(0xFFD5E8DC),
        tertiary = Color(0xFFFF7A4D),
        onTertiary = Color(0xFF3A1200),
        tertiaryContainer = Color(0xFF5A2A14),
        onTertiaryContainer = Color(0xFFFFD9CB),
        background = Color(0xFF0A100D),
        onBackground = Color(0xFFE0E6E2),
        surface = Color(0xFF0A100D),
        onSurface = Color(0xFFE0E6E2),
        surfaceVariant = Color(0xFF1E2923),
        onSurfaceVariant = Color(0xFFB4C0B8),
        outline = Color(0xFF85938A),
        outlineVariant = Color(0xFF3B4841),
        error = Color(0xFFF0958B),
        onError = Color(0xFF4A0F0B),
        errorContainer = Color(0xFF6B2520),
        onErrorContainer = Color(0xFFFAD4D0),
        surfaceDim = Color(0xFF0A100D),
        surfaceBright = Color(0xFF2F3A33),
        surfaceContainerLowest = Color(0xFF070B09),
        surfaceContainerLow = Color(0xFF111915),
        surfaceContainer = Color(0xFF16201B),
        surfaceContainerHigh = Color(0xFF1E2923),
        surfaceContainerHighest = Color(0xFF26332C)
    ) else lightColorScheme(
        primary = Color(0xFF0B6B4B),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD6F0E2),
        onPrimaryContainer = Color(0xFF073D2B),
        secondary = Color(0xFF4F6558),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFDCE9E0),
        onSecondaryContainer = Color(0xFF1A2D23),
        tertiary = Color(0xFFFF5A2C),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFE4D9),
        onTertiaryContainer = Color(0xFF4A1A08),
        background = Color(0xFFFAF8F3),
        onBackground = Color(0xFF1C2420),
        surface = Color(0xFFFAF8F3),
        onSurface = Color(0xFF1C2420),
        surfaceVariant = Color(0xFFF0ECE3),
        onSurfaceVariant = Color(0xFF4E5650),
        outline = Color(0xFF7A8A80),
        outlineVariant = Color(0xFFDDD8CB),
        error = Color(0xFFB3392F),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFF8E1DE),
        onErrorContainer = Color(0xFF5A1511),
        surfaceDim = Color(0xFFE8E4DA),
        surfaceBright = Color(0xFFFAF8F3),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF7F5EE),
        surfaceContainer = Color(0xFFF3F0E8),
        surfaceContainerHigh = Color(0xFFEDE9DF),
        surfaceContainerHighest = Color(0xFFE6E2D7)
    )

/**
 * The one accent that pops: a vivid coral-orange (Material's tertiary role). Use it sparingly,
 * for the primary action (FABs), the selected card dot and small highlights. The base theme stays
 * calm sage green so the accent draws the eye.
 */
@Composable
fun accentTextColor(): Color =
    if (isDarkTheme()) Color(0xFFFF9A73) else Color(0xFFC2410C)  // readable on the page background

/** True when the active app theme is dark (works with the manual theme toggle). */
@Composable
fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** Top bar blends into the page background: no white band, no seam. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun appBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = Color.Transparent,
    scrolledContainerColor = Color.Transparent
)

/** Win / celebration gold. */
val GoldAccent = Color(0xFFFFC24B)

/** Hot coral gradient used for the primary call to action. */
val CoralBrush: Brush
    get() = Brush.linearGradient(listOf(Color(0xFFFF5A2C), Color(0xFFFF8A4A)))

/** Page background plus soft emerald and coral glows so the surface has depth, not a flat fill. */
@Composable
fun Modifier.appBackdrop(): Modifier {
    val dark = isDarkTheme()
    val base = MaterialTheme.colorScheme.background
    val emerald = if (dark) Color(0xFF1FBF8A).copy(alpha = 0.20f) else Color(0xFF159A6E).copy(alpha = 0.10f)
    val coral = if (dark) Color(0xFFFF6A3D).copy(alpha = 0.14f) else Color(0xFFFF5A2C).copy(alpha = 0.07f)
    return this.background(base).drawBehind {
        drawRect(Brush.radialGradient(listOf(emerald, Color.Transparent), Offset(size.width * 0.1f, 0f), size.width * 0.9f))
        drawRect(Brush.radialGradient(listOf(coral, Color.Transparent), Offset(size.width, size.height * 0.85f), size.width * 0.8f))
    }
}

enum class Tone { SUCCESS, CAUTION, ELEVATED, DANGER, NEUTRAL }

data class ToneColors(val fg: Color, val bg: Color)

/** Soft status colors with readable contrast in both themes (replaces raw red/green/amber). */
@Composable
fun toneColors(tone: Tone): ToneColors {
    return if (isDarkTheme()) when (tone) {
        Tone.SUCCESS -> ToneColors(Color(0xFF7FD6A4), Color(0xFF1E3A2B))
        Tone.CAUTION -> ToneColors(Color(0xFFE6BE6A), Color(0xFF3A2F17))
        Tone.ELEVATED -> ToneColors(Color(0xFFF0A070), Color(0xFF3D2718))
        Tone.DANGER -> ToneColors(Color(0xFFF0958B), Color(0xFF3E2220))
        Tone.NEUTRAL -> ToneColors(Color(0xFFA8B5AD), Color(0xFF26322B))
    } else when (tone) {
        Tone.SUCCESS -> ToneColors(Color(0xFF1F6B4A), Color(0xFFDDEEE3))
        Tone.CAUTION -> ToneColors(Color(0xFF8A5A0B), Color(0xFFF8EBD0))
        Tone.ELEVATED -> ToneColors(Color(0xFFA8480F), Color(0xFFF9E6D9))
        Tone.DANGER -> ToneColors(Color(0xFFA8322A), Color(0xFFF8E1DE))
        Tone.NEUTRAL -> ToneColors(Color(0xFF55615A), Color(0xFFE6EBE7))
    }
}

fun Severity.tone(): Tone = when (this) {
    Severity.PAID -> Tone.SUCCESS
    Severity.NORMAL -> Tone.SUCCESS
    Severity.MODERATE -> Tone.CAUTION
    Severity.ELEVATED -> Tone.ELEVATED
    Severity.CRITICAL -> Tone.DANGER
}

@Composable
fun severityColor(severity: Severity): Color = toneColors(severity.tone()).fg

@Composable
fun severityContainer(severity: Severity): Color = toneColors(severity.tone()).bg

fun daysLeftText(days: Long): String = when {
    days <= 0L -> "Overdue"
    days == 1L -> "1 day left"
    else -> "$days days left"
}
