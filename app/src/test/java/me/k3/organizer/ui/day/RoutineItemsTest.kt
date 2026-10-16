package me.k3.organizer.ui.day

import me.k3.organizer.model.Frequency
import me.k3.organizer.model.Routine
import me.k3.organizer.model.RoutineMark
import me.k3.organizer.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class RoutineItemsTest {
    private val mon = LocalDate.of(2026, 10, 12)
    private val thu = mon.plusDays(3)
    private val monday = DayOfWeek.MONDAY

    private val scales = Routine(id = 1, title = "Play scales", frequency = Frequency.DAILY, createdOn = mon)
    private val vacuum = Routine(id = 2, title = "Vacuum", frequency = Frequency.WEEKLY, createdOn = mon)

    private fun on(day: LocalDate, marks: List<RoutineMark> = emptyList(), routines: List<Routine> = listOf(scales, vacuum)) =
        routinesOn(day, today = thu, routines, marks, monday)

    @Test
    fun noMarks_bothOpen_dailyFirst() {
        val items = on(thu)
        assertEquals(listOf(1L, 2L), items.map { it.routine.id })
        assertTrue(items.all { it.status == TaskStatus.OPEN })
        assertEquals(listOf(thu, mon), items.map { it.periodStart })
    }

    @Test
    fun notBeforeCreation_notWhenPaused() {
        assertTrue(on(mon.minusDays(1)).isEmpty())
        assertEquals(listOf(2L), on(thu, routines = listOf(scales.copy(active = false), vacuum)).map { it.routine.id })
    }

    @Test
    fun daily_markBelongsToItsDayOnly() {
        val marks = listOf(RoutineMark(1, thu, TaskStatus.DONE, thu))
        assertEquals(TaskStatus.DONE, on(thu, marks).first { it.routine.id == 1L }.status)
        assertEquals(TaskStatus.OPEN, on(thu.minusDays(1), marks).first { it.routine.id == 1L }.status)
    }

    @Test
    fun weekly_openAllWeek_thenOnlyOnTheDayMarked() {
        val wed = mon.plusDays(2)
        val marks = listOf(RoutineMark(2, mon, TaskStatus.DONE, wed))

        assertEquals(TaskStatus.DONE, on(wed, marks).first { it.routine.id == 2L }.status)
        assertTrue(on(thu, marks).none { it.routine.id == 2L })
        assertTrue(on(mon, marks).none { it.routine.id == 2L })
        // Next week it is open again.
        assertEquals(TaskStatus.OPEN, routinesOn(mon.plusDays(7), mon.plusDays(7), listOf(vacuum), marks, monday).single().status)
    }

    @Test
    fun order_openThenDoneThenCrossed() {
        val walk = Routine(id = 3, title = "Walk", frequency = Frequency.DAILY, createdOn = mon)
        val marks = listOf(RoutineMark(1, thu, TaskStatus.CANCELLED, thu), RoutineMark(3, thu, TaskStatus.DONE, thu))
        assertEquals(listOf(2L, 3L, 1L), on(thu, marks, listOf(scales, vacuum, walk)).map { it.routine.id })
    }

    @Test
    fun futureDays_cannotBeMarked() {
        val tomorrow = on(thu.plusDays(1)).first()
        assertFalse(tomorrow.canMark)
        assertNull(routineCheckboxTap(tomorrow, thu.plusDays(1)))
        assertNull(routineSwipeLeft(tomorrow, thu.plusDays(1)))
        assertTrue(on(thu.minusDays(2)).first().canMark)
    }

    @Test
    fun checkbox_setsDoneOnTheDay_thenClears() {
        val weekly = on(thu).first { it.routine.id == 2L }
        assertEquals(RoutineChange.Set(RoutineMark(2, mon, TaskStatus.DONE, thu)), routineCheckboxTap(weekly, thu))

        val done = weekly.copy(status = TaskStatus.DONE)
        assertEquals(RoutineChange.Clear(2, mon), routineCheckboxTap(done, thu))
        val crossed = weekly.copy(status = TaskStatus.CANCELLED)
        assertEquals(RoutineChange.Clear(2, mon), routineCheckboxTap(crossed, thu))
    }

    @Test
    fun swipeLeft_crossesAndRestores_notOnDone() {
        val daily = on(thu).first { it.routine.id == 1L }
        assertEquals(RoutineChange.Set(RoutineMark(1, thu, TaskStatus.CANCELLED, thu)), routineSwipeLeft(daily, thu))
        assertEquals(RoutineChange.Clear(1, thu), routineSwipeLeft(daily.copy(status = TaskStatus.CANCELLED), thu))
        assertNull(routineSwipeLeft(daily.copy(status = TaskStatus.DONE), thu))
    }
}
