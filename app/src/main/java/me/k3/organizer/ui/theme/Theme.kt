package me.k3.organizer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val PaperLightColors = lightColorScheme(
    primary = Olive40,
    onPrimary = Color.White,
    primaryContainer = OliveContainer90,
    onPrimaryContainer = OliveOnContainer10,
    secondary = Khaki40,
    onSecondary = Color.White,
    secondaryContainer = KhakiContainer90,
    onSecondaryContainer = KhakiOnContainer10,
    tertiary = Brick40,
    onTertiary = Color.White,
    tertiaryContainer = BrickContainer90,
    onTertiaryContainer = BrickOnContainer10,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaperVariant,
    onSurfaceVariant = InkVariant,
    outline = PaperOutline,
    outlineVariant = PaperOutlineVariant,
    surfaceContainerLowest = PaperContainerLowest,
    surfaceContainerLow = PaperContainerLow,
    surfaceContainer = PaperContainer,
    surfaceContainerHigh = PaperContainerHigh,
    surfaceContainerHighest = PaperContainerHighest,
)

private val PaperDarkColors = darkColorScheme(
    primary = Olive80,
    onPrimary = OliveOn20,
    primaryContainer = OliveContainer30,
    onPrimaryContainer = OliveOnContainer90,
    secondary = Khaki80,
    onSecondary = KhakiOn20,
    secondaryContainer = KhakiContainer30,
    onSecondaryContainer = KhakiOnContainer90,
    tertiary = Brick80,
    onTertiary = BrickOn20,
    tertiaryContainer = BrickContainer30,
    onTertiaryContainer = BrickOnContainer90,
    background = Leather,
    onBackground = Parchment,
    surface = Leather,
    onSurface = Parchment,
    surfaceVariant = LeatherVariant,
    onSurfaceVariant = ParchmentVariant,
    outline = LeatherOutline,
    outlineVariant = LeatherVariant,
    surfaceContainerLowest = LeatherContainerLowest,
    surfaceContainerLow = LeatherContainerLow,
    surfaceContainer = LeatherContainer,
    surfaceContainerHigh = LeatherContainerHigh,
    surfaceContainerHighest = LeatherContainerHighest,
)

/**
 * The app's own "Paper" palette, light or dark with the system. Wallpaper-based colors
 * (Material You) are deliberately not used: the app should look the same on every phone.
 */
@Composable
fun MyOrganizerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatusColors else LightStatusColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) PaperDarkColors else PaperLightColors,
            typography = Typography,
            content = content,
        )
    }
}
