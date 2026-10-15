package me.k3.organizer.data.calendar

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import me.k3.organizer.model.CalendarEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Reads real data through the system CalendarProvider. Each test works in its own local calendar
 * (account type LOCAL, no Google account needed), which is deleted afterwards together with its events.
 * Assertions look only at events of that calendar, so whatever else is on the device does not matter.
 *
 * Runs on a device or emulator: `./gradlew connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class AndroidCalendarSourceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val resolver: ContentResolver = context.contentResolver
    private val source = AndroidCalendarSource(context)
    private val zone = ZoneId.systemDefault()

    // A day far from today, so the device's own events are unlikely to be around.
    private val day = LocalDate.of(2030, 3, 12)

    private var calendarId = 0L
    private val ourEventIds = mutableSetOf<Long>()

    @Before
    fun setUp() {
        // UiAutomation.grantRuntimePermission exists since Android 9.
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.READ_CALENDAR)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.WRITE_CALENDAR)
        deleteTestCalendars() // leftovers of an interrupted run
        calendarId = createCalendar(visible = true)
    }

    @After
    fun tearDown() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) deleteTestCalendars()
    }

    @Test
    fun hasAccess_afterGrant() {
        assertTrue(source.hasAccess())
    }

    @Test
    fun timedEvent_readWithTimesAndLocation() = runBlocking {
        val id = insertEvent("Standup", day.atTime(10, 0).millis(), day.atTime(10, 15).millis(), location = "Room 4")

        val event = ours(source.eventsOn(day)).single()

        assertEquals(id, event.eventId)
        assertEquals("Standup", event.title)
        assertEquals(day.atTime(10, 0), event.start)
        assertEquals(day.atTime(10, 15), event.end)
        assertEquals("Room 4", event.location)
        assertEquals(false, event.allDay)
    }

    @Test
    fun timedEvent_notOnOtherDays() = runBlocking {
        insertEvent("Standup", day.atTime(10, 0).millis(), day.atTime(10, 15).millis())
        assertTrue(ours(source.eventsOn(day.minusDays(1))).isEmpty())
        assertTrue(ours(source.eventsOn(day.plusDays(1))).isEmpty())
    }

    @Test
    fun allDayEvent_onItsDayOnly_whateverTheZone() = runBlocking {
        // All-day events are stored at UTC midnights with the UTC time zone.
        insertEvent("Holiday", day.utcMidnight(), day.plusDays(1).utcMidnight(), allDay = true)

        val event = ours(source.eventsOn(day)).single()
        assertTrue(event.allDay)
        assertTrue(ours(source.eventsOn(day.plusDays(1))).isEmpty())
        assertTrue(ours(source.eventsOn(day.minusDays(1))).isEmpty())
    }

    @Test
    fun overnightEvent_onBothDays() = runBlocking {
        insertEvent("Night train", day.atTime(22, 0).millis(), day.plusDays(1).atTime(6, 0).millis())

        assertEquals("22:00–…", ours(source.eventsOn(day)).single().timeLabelOn(day))
        val next = day.plusDays(1)
        assertEquals("…–06:00", ours(source.eventsOn(next)).single().timeLabelOn(next))
    }

    @Test
    fun weeklyEvent_expandedIntoOccurrences() = runBlocking {
        insertRecurring("Gym", day.atTime(19, 0).millis(), durationRfc = "PT1H", rrule = "FREQ=WEEKLY;COUNT=3")

        val first = ours(source.eventsOn(day)).single()
        val second = ours(source.eventsOn(day.plusWeeks(1))).single()
        assertEquals(day.plusWeeks(1).atTime(19, 0), second.start)
        assertEquals(first.eventId, second.eventId)
        assertTrue("each occurrence has its own begin", first.beginMillis != second.beginMillis)
        assertTrue(ours(source.eventsOn(day.plusDays(1))).isEmpty())
        assertTrue("COUNT=3 ends the series", ours(source.eventsOn(day.plusWeeks(3))).isEmpty())
    }

    @Test
    fun hiddenCalendar_isSkipped() = runBlocking {
        val hidden = createCalendar(visible = false)
        insertEvent("Secret", day.atTime(12, 0).millis(), day.atTime(13, 0).millis(), calendar = hidden)
        insertEvent("Public", day.atTime(14, 0).millis(), day.atTime(15, 0).millis())

        assertEquals(listOf("Public"), ours(source.eventsOn(day)).map { it.title })
    }

    @Test
    fun events_allDayFirst_thenByStart() = runBlocking {
        insertEvent("Late", day.atTime(18, 0).millis(), day.atTime(19, 0).millis())
        insertEvent("Early", day.atTime(8, 0).millis(), day.atTime(9, 0).millis())
        insertEvent("Holiday", day.utcMidnight(), day.plusDays(1).utcMidnight(), allDay = true)

        assertEquals(listOf("Holiday", "Early", "Late"), ours(source.eventsOn(day)).map { it.title })
    }

    // --- helpers ---

    private fun ours(events: List<CalendarEvent>) = events.filter { it.eventId in ourEventIds }

    private fun java.time.LocalDateTime.millis() = atZone(zone).toInstant().toEpochMilli()

    private fun LocalDate.utcMidnight() = atTime(LocalTime.MIDNIGHT).toInstant(ZoneOffset.UTC).toEpochMilli()

    /** Calendars may only be created by a "sync adapter"; a LOCAL account needs no real one. */
    private fun asSyncAdapter(uri: Uri): Uri = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, TEST_ACCOUNT)
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()

    private fun createCalendar(visible: Boolean): Long {
        val values = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, TEST_ACCOUNT)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, "k3 test")
            put(Calendars.CALENDAR_DISPLAY_NAME, "k3 test")
            put(Calendars.CALENDAR_COLOR, 0xFF3366CC.toInt())
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, TEST_ACCOUNT)
            put(Calendars.CALENDAR_TIME_ZONE, zone.id)
            put(Calendars.VISIBLE, if (visible) 1 else 0)
            put(Calendars.SYNC_EVENTS, 1)
        }
        return ContentUris.parseId(checkNotNull(resolver.insert(asSyncAdapter(Calendars.CONTENT_URI), values)))
    }

    private fun insertEvent(
        title: String,
        startMillis: Long,
        endMillis: Long,
        allDay: Boolean = false,
        location: String? = null,
        calendar: Long = calendarId,
    ): Long = insert(calendar) {
        put(Events.TITLE, title)
        put(Events.DTSTART, startMillis)
        put(Events.DTEND, endMillis)
        put(Events.ALL_DAY, if (allDay) 1 else 0)
        put(Events.EVENT_TIMEZONE, if (allDay) "UTC" else zone.id)
        location?.let { put(Events.EVENT_LOCATION, it) }
    }

    /** A repeating event has a DURATION (RFC 5545, e.g. "PT1H") instead of DTEND. */
    private fun insertRecurring(title: String, startMillis: Long, durationRfc: String, rrule: String): Long = insert(calendarId) {
        put(Events.TITLE, title)
        put(Events.DTSTART, startMillis)
        put(Events.DURATION, durationRfc)
        put(Events.RRULE, rrule)
        put(Events.EVENT_TIMEZONE, zone.id)
    }

    private fun insert(calendar: Long, fill: ContentValues.() -> Unit): Long {
        val values = ContentValues().apply {
            put(Events.CALENDAR_ID, calendar)
            fill()
        }
        val id = ContentUris.parseId(checkNotNull(resolver.insert(Events.CONTENT_URI, values)))
        ourEventIds += id
        return id
    }

    /** Deleting a calendar as its sync adapter removes its events too. */
    private fun deleteTestCalendars() {
        resolver.delete(
            asSyncAdapter(Calendars.CONTENT_URI),
            "${Calendars.ACCOUNT_NAME} = ? AND ${Calendars.ACCOUNT_TYPE} = ?",
            arrayOf(TEST_ACCOUNT, CalendarContract.ACCOUNT_TYPE_LOCAL),
        )
    }

    private companion object {
        const val TEST_ACCOUNT = "k3-checkncross-test"
    }
}
