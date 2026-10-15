package me.k3.organizer.data

import android.content.Context
import androidx.core.content.edit

/** Small settings kept between launches (SharedPreferences: a key-value file private to the app). */
class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** The user said "Not now" to showing calendar events; do not ask on the Day screen again. */
    var calendarPromptDismissed: Boolean
        get() = prefs.getBoolean(KEY_CALENDAR_PROMPT_DISMISSED, false)
        set(value) = prefs.edit { putBoolean(KEY_CALENDAR_PROMPT_DISMISSED, value) }

    private companion object {
        const val KEY_CALENDAR_PROMPT_DISMISSED = "calendar_prompt_dismissed"
    }
}
