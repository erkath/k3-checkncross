package me.k3.organizer.ui.week

import me.k3.organizer.model.CalendarEvent
import me.k3.organizer.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

class WeekUiStateTest {
    private val thu = LocalDate.of(2026, 10, 15)
    private val mon = LocalDate.of(2026, 10, 12)
    private val sun = LocalDate.of(2026, 10, 11)

    @Test
    fun weekStart_dependsOnFirstDayOfWeek() {
        assertEquals(mon, weekStartOf(thu, DayOfWeek.MONDAY))
        assertEquals(sun, weekStartOf(thu, DayOfWeek.SUNDAY))
        assertEquals(mon, weekStartOf(mon, DayOfWeek.MONDAY))
        // Sunday belongs to the week before when weeks start on Monday.
        assertEquals(mon.minusDays(7), weekStartOf(sun, DayOfWeek.MONDAY))
    }

    @Test
    fun build_sevenDays_eachWithItsTasksAndEvents() {
        val tasks = listOf(
            Task(id = 1, title = "on monday", day = mon),
            Task(id = 2, title = "on sunday", day = mon.plusDays(6)),
            Task(id = 3, title = "next week", day = mon.plusDays(7)),
            Task(id = 4, title = "backlog"),
        )
        val holiday = CalendarEvent(9, "Holiday", true, thu.atStartOfDay(), thu.plusDays(1).atStartOfDay(), null, null, 0, 0)

        val week = buildWeekUiState(mon, thu, tasks, mapOf(thu to listOf(holiday)))

        assertEquals((0L..6L).map { mon.plusDays(it) }, week.days.map { it.day })
        assertEquals(listOf(1L), week.days[0].untimed.map { it.id })
        assertEquals(listOf(2L), week.days[6].untimed.map { it.id })
        assertEquals(listOf(holiday), week.days[3].allDayEvents)
        assertTrue(week.days.all { it.today == thu })
        assertTrue(week.days[3].isToday)
    }

    @Test
    fun containsToday() {
        assertTrue(WeekUiState(mon, thu).containsToday)
        assertFalse(WeekUiState(mon.plusDays(7), thu).containsToday)
    }

    @Test
    fun openCount_countsOnlyOpenTasks() {
        val tasks = listOf(
            Task(id = 1, title = "a", day = mon),
            Task(id = 2, title = "b", day = mon).markDone(),
            Task(id = 3, title = "c", day = mon).moveTo(mon.plusDays(1)).old,
        )
        assertEquals(1, buildWeekUiState(mon, thu, tasks, emptyMap()).days[0].openCount())
    }

    @Test
    fun shownWeek_followsTodayOnlyIfItWasTodaysWeek() {
        val nextMon = mon.plusDays(7)
        // Sunday night → Monday: the screen on this week moves to the new one.
        assertEquals(nextMon, shownWeekAfterTodayChange(mon, mon.plusDays(6), nextMon, DayOfWeek.MONDAY))
        // Midweek: still the same week.
        assertEquals(mon, shownWeekAfterTodayChange(mon, thu, thu.plusDays(1), DayOfWeek.MONDAY))
        // Browsing another week: stays there.
        val later = mon.plusDays(21)
        assertEquals(later, shownWeekAfterTodayChange(later, mon.plusDays(6), nextMon, DayOfWeek.MONDAY))
    }

    @Test
    fun weekRangeLabel_formats() {
        val en = Locale.ENGLISH
        assertEquals("12–18 Oct", weekRangeLabel(mon, mon.plusDays(6), 2026, en))
        assertEquals("28 Sep – 4 Oct", weekRangeLabel(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 4), 2026, en))
        assertEquals("12–18 Oct 2027", weekRangeLabel(LocalDate.of(2027, 10, 12), LocalDate.of(2027, 10, 18), 2026, en))
        assertEquals("27 Sep – 3 Oct 2027", weekRangeLabel(LocalDate.of(2027, 9, 27), LocalDate.of(2027, 10, 3), 2026, en))
        assertEquals("29 Dec 2025 – 4 Jan 2026", weekRangeLabel(LocalDate.of(2025, 12, 29), LocalDate.of(2026, 1, 4), 2026, en))
    }
}
