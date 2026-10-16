package me.k3.organizer.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDate

class TodayTrackerTest {
    private val mon = LocalDate.of(2026, 10, 12)

    @Test
    fun untilNextMidnight_fromVariousTimes() {
        assertEquals(Duration.ofHours(24), untilNextMidnight(mon.atStartOfDay()))
        assertEquals(Duration.ofMinutes(90), untilNextMidnight(mon.atTime(22, 30)))
        assertEquals(Duration.ofSeconds(1), untilNextMidnight(mon.atTime(23, 59, 59)))
    }
}
