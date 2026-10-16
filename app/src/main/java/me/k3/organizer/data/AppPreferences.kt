package me.k3.organizer.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small settings kept between launches (SharedPreferences: a key-value file private to the app). */
class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** The user said "Not now" to the calendar banner; it stays hidden (the "Connect" row at the bottom remains). */
    var calendarPromptDismissed: Boolean
        get() = prefs.getBoolean(KEY_CALENDAR_PROMPT_DISMISSED, false)
        set(value) = prefs.edit { putBoolean(KEY_CALENDAR_PROMPT_DISMISSED, value) }

    /** The app has shown Android's calendar permission dialog at least once (see calendarConnectAction). */
    var calendarPermissionRequested: Boolean
        get() = prefs.getBoolean(KEY_CALENDAR_PERMISSION_REQUESTED, false)
        set(value) = prefs.edit { putBoolean(KEY_CALENDAR_PERMISSION_REQUESTED, value) }

    private val scheduledClosedLastState = MutableStateFlow(prefs.getBoolean(KEY_SCHEDULED_CLOSED_LAST, false))

    /** "Done to the bottom" in the Scheduled section (Day and Week); off = a strict timeline. Shared by all screens. */
    val scheduledClosedLast: StateFlow<Boolean> = scheduledClosedLastState.asStateFlow()

    fun setScheduledClosedLast(value: Boolean) {
        prefs.edit { putBoolean(KEY_SCHEDULED_CLOSED_LAST, value) }
        scheduledClosedLastState.value = value
    }

    private companion object {
        const val KEY_CALENDAR_PROMPT_DISMISSED = "calendar_prompt_dismissed"
        const val KEY_CALENDAR_PERMISSION_REQUESTED = "calendar_permission_requested"
        const val KEY_SCHEDULED_CLOSED_LAST = "scheduled_closed_last"
    }
}
