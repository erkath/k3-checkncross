package me.k3.organizer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class CalendarEventTest {
    private val berlin = ZoneId.of("Europe/Berlin") // UTC+2 in mid-October
    private val thu = LocalDate.of(2026, 10, 15)

    private fun millis(dt: LocalDateTime, zone: ZoneId) = dt.atZone(zone).toInstant().toEpochMilli()

    private fun timed(start: LocalDateTime, end: LocalDateTime) = CalendarInstance(
        eventId = 1, title = "Standup", beginMillis = millis(start, berlin), endMillis = millis(end, berlin),
        allDay = false, color = 0xFF3366CC.toInt(), location = " ",
    )

    private fun allDay(from: LocalDate, toExclusive: LocalDate) = CalendarInstance(
        eventId = 2, title = null, beginMillis = millis(from.atStartOfDay(), ZoneOffset.UTC),
        endMillis = millis(toExclusive.atStartOfDay(), ZoneOffset.UTC), allDay = true, color = null, location = null,
    )

    @Test
    fun timed_readInLocalZone() {
        val event = timed(thu.atTime(9, 0), thu.atTime(9, 30)).toEventOn(thu, berlin)!!
        assertEquals(thu.atTime(9, 0), event.start)
        assertEquals("09:00–09:30", event.timeLabelOn(thu))
        assertNull(event.location) // blank location dropped
    }

    @Test
    fun timed_onOtherDay_isNull() {
        assertNull(timed(thu.atTime(9, 0), thu.atTime(10, 0)).toEventOn(thu.plusDays(1), berlin))
        // Ending exactly at midnight does not touch the next day.
        assertNull(timed(thu.atTime(23, 0), thu.plusDays(1).atStartOfDay()).toEventOn(thu.plusDays(1), berlin))
    }

    @Test
    fun overnight_showsOnBothDays_withEllipsis() {
        val instance = timed(thu.atTime(22, 0), thu.plusDays(1).atTime(2, 0))
        assertEquals("22:00–…", instance.toEventOn(thu, berlin)!!.timeLabelOn(thu))

        val next = thu.plusDays(1)
        val second = instance.toEventOn(next, berlin)!!
        assertEquals("…–02:00", second.timeLabelOn(next))
        assertEquals(LocalTime.MIDNIGHT, second.sortTimeOn(next))
    }

    @Test
    fun endingAtMidnight_hasPlainLabel() {
        val event = timed(thu.atTime(23, 0), thu.plusDays(1).atStartOfDay()).toEventOn(thu, berlin)!!
        assertEquals("23:00–00:00", event.timeLabelOn(thu))
    }

    @Test
    fun zeroLength_belongsToItsDay() {
        val instance = timed(thu.atTime(9, 0), thu.atTime(9, 0))
        assertNotNull(instance.toEventOn(thu, berlin))
        assertNull(instance.toEventOn(thu.plusDays(1), berlin))
    }

    @Test
    fun allDay_readInUtc_notShiftedByZone() {
        // In UTC+2 the UTC midnight is 02:00 local; read in the local zone it would leak into Friday.
        val instance = allDay(thu, thu.plusDays(1))
        val event = instance.toEventOn(thu, berlin)!!
        assertTrue(event.allDay)
        assertEquals("", event.title)
        assertNull(instance.toEventOn(thu.plusDays(1), berlin))
        assertNull(instance.toEventOn(thu.minusDays(1), berlin))
    }

    @Test
    fun allDay_multiDay_coversEachDay() {
        val instance = allDay(thu, thu.plusDays(3))
        assertNotNull(instance.toEventOn(thu, berlin))
        assertNotNull(instance.toEventOn(thu.plusDays(2), berlin))
        assertNull(instance.toEventOn(thu.plusDays(3), berlin))
    }
}
