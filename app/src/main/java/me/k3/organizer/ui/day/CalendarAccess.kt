package me.k3.organizer.ui.day

/** What tapping "Allow" / "Connect" does. */
enum class CalendarConnectAction {
    /** Show Android's permission dialog. */
    REQUEST,

    /** Android will not show the dialog any more: send the user to the app's page in Settings. */
    OPEN_SETTINGS,

    /** Access is already there: just re-read the calendar. */
    NONE,
}

/**
 * Android stops showing the permission dialog after the user has denied it twice (or chose
 * "Don't ask again"); a request then fails silently. That state is not reported directly, so it is
 * inferred: the app asked before, and Android no longer wants a rationale shown
 * (`shouldShowRequestPermissionRationale` is false). Before the first request that flag is false too,
 * which is why [requestedBefore] is needed.
 */
fun calendarConnectAction(granted: Boolean, requestedBefore: Boolean, showRationale: Boolean): CalendarConnectAction = when {
    granted -> CalendarConnectAction.NONE
    requestedBefore && !showRationale -> CalendarConnectAction.OPEN_SETTINGS
    else -> CalendarConnectAction.REQUEST
}
