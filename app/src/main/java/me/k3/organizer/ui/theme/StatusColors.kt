package me.k3.organizer.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colors for task statuses that the Material color scheme does not cover. */
@Immutable
data class StatusColors(val done: Color)

val LightStatusColors = StatusColors(done = DoneGreenLight)
val DarkStatusColors = StatusColors(done = DoneGreenDark)

/** Read inside the theme as `LocalStatusColors.current`. */
val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }
