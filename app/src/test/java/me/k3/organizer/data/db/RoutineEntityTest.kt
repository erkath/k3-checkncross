package me.k3.organizer.data.db

import me.k3.organizer.model.Frequency
import me.k3.organizer.model.Routine
import me.k3.organizer.model.RoutineMark
import me.k3.organizer.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RoutineEntityTest {
    private val mon = LocalDate.of(2026, 10, 12)

    @Test
    fun routine_roundTrip() {
        val daily = Routine(id = 1, title = "Play scales", frequency = Frequency.DAILY, createdOn = mon)
        val weekly = Routine(id = 2, title = "Vacuum", note = "and the hallway", frequency = Frequency.WEEKLY, createdOn = mon, active = false)
        assertEquals(daily, daily.toEntity().toRoutine())
        assertEquals(weekly, weekly.toEntity().toRoutine())
        assertEquals("WEEKLY", weekly.toEntity().frequency)
    }

    @Test
    fun mark_roundTrip() {
        val mark = RoutineMark(routineId = 2, periodStart = mon, status = TaskStatus.CANCELLED, markedOn = mon.plusDays(3))
        assertEquals(mark, mark.toEntity().toMark())
        assertEquals("CANCELLED", mark.toEntity().status)
    }
}
