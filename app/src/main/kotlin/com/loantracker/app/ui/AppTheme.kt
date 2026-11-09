package com.loantracker.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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
        primary = Color(0xFF6FCFA0),
        onPrimary = Color(0xFF06271A),
        primaryContainer = Color(0xFF1F4A37),
        onPrimaryContainer = Color(0xFFC9EEDA),
        secondary = Color(0xFFB3CBBD),
        onSecondary = Color(0xFF1E3329),
        secondaryContainer = Color(0xFF33493D),
        onSecondaryContainer = Color(0xFFD5E8DC),
        tertiary = Color(0xFFE3C27A),
        onTertiary = Color(0xFF3A2A05),
        tertiaryContainer = Color(0xFF54401A),
        onTertiaryContainer = Color(0xFFF6E3B8),
        background = Color(0xFF0F1511),
        onBackground = Color(0xFFE0E6E2),
        surface = Color(0xFF0F1511),
        onSurface = Color(0xFFE0E6E2),
        surfaceVariant = Color(0xFF26322B),
        onSurfaceVariant = Color(0xFFB4C0B8),
        outline = Color(0xFF85938A),
        outlineVariant = Color(0xFF3B4841),
        error = Color(0xFFF0958B),
        onError = Color(0xFF4A0F0B),
        errorContainer = Color(0xFF6B2520),
        onErrorContainer = Color(0xFFFAD4D0),
        surfaceDim = Color(0xFF0F1511),
        surfaceBright = Color(0xFF2F3A33),
        surfaceContainerLowest = Color(0xFF0A0F0C),
        surfaceContainerLow = Color(0xFF161D19),
        surfaceContainer = Color(0xFF1A2420),
        surfaceContainerHigh = Color(0xFF222D27),
        surfaceContainerHighest = Color(0xFF2B372F)
    ) else lightColorScheme(
        primary = Color(0xFF2E7D5B),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD3EADB),
        onPrimaryContainer = Color(0xFF10392A),
        secondary = Color(0xFF4F6558),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFDCE9E0),
        onSecondaryContainer = Color(0xFF1A2D23),
        tertiary = Color(0xFF8A6A1F),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFF4E7C8),
        onTertiaryContainer = Color(0xFF3A2A05),
        background = Color(0xFFF4F7F4),
        onBackground = Color(0xFF1C2420),
        surface = Color(0xFFF4F7F4),
        onSurface = Color(0xFF1C2420),
        surfaceVariant = Color(0xFFE6EDE8),
        onSurfaceVariant = Color(0xFF46544C),
        outline = Color(0xFF7A8A80),
        outlineVariant = Color(0xFFCBD6CE),
        error = Color(0xFFB3392F),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFF8E1DE),
        onErrorContainer = Color(0xFF5A1511),
        surfaceDim = Color(0xFFE1E7E2),
        surfaceBright = Color(0xFFF4F7F4),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFEFF4F0),
        surfaceContainer = Color(0xFFE9F0EB),
        surfaceContainerHigh = Color(0xFFE3EBE5),
        surfaceContainerHighest = Color(0xFFDDE6DF)
    )

/** True when the active app theme is dark (works with the manual theme toggle). */
@Composable
fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** Top bar blends into the page background: no white band, no seam. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun appBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.background,
    scrolledContainerColor = MaterialTheme.colorScheme.background
)

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
