package com.loantracker.app.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.loantracker.app.R

/**
 * Bundled typeface (Plus Jakarta Sans, SIL OFL) so the app looks the same on every phone,
 * instead of inheriting whatever system font the device has set.
 */
@OptIn(ExperimentalTextApi::class)
val AppFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.plus_jakarta_sans, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800)))
)

private fun TextStyle.app(): TextStyle = copy(fontFamily = AppFontFamily)

val AppTypography: Typography = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.app(),
        displayMedium = t.displayMedium.app(),
        displaySmall = t.displaySmall.app(),
        headlineLarge = t.headlineLarge.app(),
        headlineMedium = t.headlineMedium.app(),
        headlineSmall = t.headlineSmall.app(),
        titleLarge = t.titleLarge.app(),
        titleMedium = t.titleMedium.app(),
        titleSmall = t.titleSmall.app(),
        bodyLarge = t.bodyLarge.app(),
        bodyMedium = t.bodyMedium.app(),
        bodySmall = t.bodySmall.app(),
        labelLarge = t.labelLarge.app(),
        labelMedium = t.labelMedium.app(),
        labelSmall = t.labelSmall.app()
    )
}
