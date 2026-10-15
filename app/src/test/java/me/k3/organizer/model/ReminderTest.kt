package me.k3.organizer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

class ReminderTest {
    private val mon = LocalDate.of(2026, 10, 12)
    private val fri = mon.plusDays(4)
    private val sat = mon.plusDays(5)
    private val nine = LocalTime.of(9, 0)
    private val hour = Duration.ofHours(1)
    private val quarter = Duration.ofMinutes(15)

    // --- TaskReminder ---

    private val timedTask = Task(id = 7, title = "Dentist", day = mon, startTime = nine, duration = hour)
    private val reminder = TaskReminder(taskId = 7, offset = quarter)

    @Test
    fun taskReminder_validation() {
        assertThrows(IllegalArgumentException::class.java) { TaskReminder(taskId = 0, offset = quarter) }
        assertThrows(IllegalArgumentException::class.java) {
            TaskReminder(taskId = 7, offset = quarter.negated())
        }
    }

    @Test
    fun taskReminder_firesOffsetBeforeStart() {
        assertEquals(mon.atTime(8, 45), reminder.fireTime(timedTask))
    }

    @Test
    fun taskReminder_zeroOffset_firesAtStart() {
        assertEquals(mon.atTime(nine), reminder.copy(offset = Duration.ZERO).fireTime(timedTask))
    }

    @Test
    fun taskReminder_offsetCanCrossMidnight() {
        val early = timedTask.schedule(mon, LocalTime.of(0, 10), hour)
        assertEquals(mon.minusDays(1).atTime(23, 55), reminder.fireTime(early))
    }

    @Test
    fun taskReminder_silentForUntimedOrClosedTask() {
        assertNull(reminder.fireTime(timedTask.schedule(mon)))
        assertNull(reminder.fireTime(timedTask.markDone()))
        assertNull(reminder.fireTime(timedTask.cancel()))
        assertNull(reminder.fireTime(timedTask.moveTo(fri).old))
    }

    @Test
    fun taskReminder_silentWhenDisabled() {
        assertNull(reminder.copy(enabled = false).fireTime(timedTask))
    }

    @Test
    fun taskReminder_rejectsOtherTask() {
        assertThrows(IllegalArgumentException::class.java) { reminder.fireTime(timedTask.copy(id = 8)) }
    }

    @Test
    fun taskReminder_nextFireAfter_onlyInFuture() {
        assertEquals(mon.atTime(8, 45), reminder.nextFireAfter(mon.atTime(8, 0), timedTask))
        assertNull(reminder.nextFireAfter(mon.atTime(8, 45), timedTask))
        assertNull(reminder.nextFireAfter(mon.atTime(8, 50), timedTask))
    }

    // --- RecurringReminder ---

    private val planDay = RecurringReminder(message = "Plan the day", time = nine, days = RecurringReminder.WEEKDAYS)

    @Test
    fun recurring_validation() {
        assertThrows(IllegalArgumentException::class.java) { planDay.copy(message = " ") }
        assertThrows(IllegalArgumentException::class.java) { planDay.copy(days = emptySet()) }
    }

    @Test
    fun recurring_laterToday() {
        assertEquals(mon.atTime(nine), planDay.nextFireAfter(mon.atTime(8, 0)))
    }

    @Test
    fun recurring_exactlyAtTime_goesToNextDay() {
        assertEquals(mon.plusDays(1).atTime(nine), planDay.nextFireAfter(mon.atTime(nine)))
    }

    @Test
    fun recurring_skipsWeekend() {
        assertEquals(mon.plusDays(7).atTime(nine), planDay.nextFireAfter(fri.atTime(10, 0)))
        assertEquals(mon.plusDays(7).atTime(nine), planDay.nextFireAfter(sat.atTime(8, 0)))
    }

    @Test
    fun recurring_singleDay_sameWeekdayAfterTimePassed_isNextWeek() {
        val mondays = planDay.copy(days = setOf(DayOfWeek.MONDAY))
        assertEquals(mon.plusDays(7).atTime(nine), mondays.nextFireAfter(mon.atTime(10, 0)))
    }

    @Test
    fun recurring_everyDay() {
        val daily = planDay.copy(days = RecurringReminder.EVERY_DAY)
        assertEquals(sat.atTime(nine), daily.nextFireAfter(fri.atTime(10, 0)))
    }

    @Test
    fun recurring_silentWhenDisabled() {
        assertNull(planDay.copy(enabled = false).nextFireAfter(mon.atTime(8, 0)))
    }
}
