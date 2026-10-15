package me.k3.organizer.ui.day

import me.k3.organizer.ui.day.CalendarConnectAction.NONE
import me.k3.organizer.ui.day.CalendarConnectAction.OPEN_SETTINGS
import me.k3.organizer.ui.day.CalendarConnectAction.REQUEST
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarAccessTest {
    @Test
    fun firstTime_asks() {
        assertEquals(REQUEST, calendarConnectAction(granted = false, requestedBefore = false, showRationale = false))
    }

    @Test
    fun deniedOnce_asksAgain() {
        assertEquals(REQUEST, calendarConnectAction(granted = false, requestedBefore = true, showRationale = true))
    }

    @Test
    fun deniedForGood_opensSettings() {
        assertEquals(OPEN_SETTINGS, calendarConnectAction(granted = false, requestedBefore = true, showRationale = false))
    }

    @Test
    fun alreadyGranted_doesNothing() {
        assertEquals(NONE, calendarConnectAction(granted = true, requestedBefore = true, showRationale = false))
    }
}
