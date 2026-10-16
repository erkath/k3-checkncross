package me.k3.organizer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class RoutineTest {
    private val thu = LocalDate.of(2026, 10, 15)
    private val mon = LocalDate.of(2026, 10, 12)
    private val scales = Routine(id = 1, title = "Play scales", frequency = Frequency.DAILY, createdOn = mon)
    private val vacuum = Routine(id = 2, title = "Vacuum", frequency = Frequency.WEEKLY, createdOn = mon)

    @Test
    fun blankTitle_rejected() {
        assertThrows(IllegalArgumentException::class.java) { scales.copy(title = " ") }
    }

    @Test
    fun periodStart_dailyIsTheDay_weeklyIsWeekStart() {
        assertEquals(thu, scales.periodStart(thu, DayOfWeek.MONDAY))
        assertEquals(mon, vacuum.periodStart(thu, DayOfWeek.MONDAY))
        assertEquals(mon.minusDays(1), vacuum.periodStart(thu, DayOfWeek.SUNDAY))
    }

    @Test
    fun mark_validation() {
        RoutineMark(1, mon, TaskStatus.DONE, thu)
        assertThrows(IllegalArgumentException::class.java) { RoutineMark(1, mon, TaskStatus.OPEN, thu) }
        assertThrows(IllegalArgumentException::class.java) { RoutineMark(1, mon, TaskStatus.MOVED, thu) }
        assertThrows(IllegalArgumentException::class.java) { RoutineMark(0, mon, TaskStatus.DONE, thu) }
        assertThrows(IllegalArgumentException::class.java) { RoutineMark(1, thu, TaskStatus.DONE, mon) }
    }
}
