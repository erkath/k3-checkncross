package me.k3.organizer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

private val Default = Typography()

/** Headings in a serif for a bookish, paper feel; body text stays sans-serif for easy reading. */
private fun TextStyle.serif(weight: FontWeight? = null) =
    copy(fontFamily = FontFamily.Serif, fontWeight = weight ?: fontWeight)

val Typography = Typography(
    displayLarge = Default.displayLarge.serif(),
    displayMedium = Default.displayMedium.serif(),
    displaySmall = Default.displaySmall.serif(),
    headlineLarge = Default.headlineLarge.serif(),
    headlineMedium = Default.headlineMedium.serif(),
    headlineSmall = Default.headlineSmall.serif(),
    titleLarge = Default.titleLarge.serif(FontWeight.SemiBold),
    titleMedium = Default.titleMedium.serif(FontWeight.SemiBold),
    titleSmall = Default.titleSmall.serif(FontWeight.SemiBold),
    // Section titles ("Scheduled", "Anytime") use labelLarge.
    labelLarge = Default.labelLarge.serif(FontWeight.SemiBold),
)
