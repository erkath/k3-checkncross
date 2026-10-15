package me.k3.organizer.ui.day

import me.k3.organizer.model.Task
import me.k3.organizer.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

class DayUiStateTest {
    private val mon = LocalDate.of(2026, 10, 12)
    private val tue = mon.plusDays(1)
    private val hour = Duration.ofHours(1)

    private fun timed(id: Long, day: LocalDate, h: Int) =
        Task(id = id, title = "t$id", day = day, startTime = LocalTime.of(h, 0), duration = hour)

    private fun untimed(id: Long, day: LocalDate?) = Task(id = id, title = "t$id", day = day)

    @Test
    fun picksOnlyTasksOfTheDay_andSplitsByTime() {
        val tasks = listOf(timed(1, mon, 9), untimed(2, mon), timed(3, tue, 9), untimed(4, tue), untimed(5, null))
        val state = buildDayUiState(mon, today = mon, tasks)

        assertEquals(listOf(1L), state.timed.map { it.id })
        assertEquals(listOf(2L), state.untimed.map { it.id })
    }

    @Test
    fun timed_sortedByStartTime_thenId() {
        val tasks = listOf(timed(1, mon, 14), timed(2, mon, 9), timed(3, mon, 9))
        assertEquals(listOf(2L, 3L, 1L), buildDayUiState(mon, mon, tasks).timed.map { it.id })
    }

    @Test
    fun untimed_openThenDoneThenGrey_eachInCreationOrder() {
        val tasks = listOf(untimed(4, mon), untimed(1, mon).cancel(), untimed(2, mon).markDone(), untimed(3, mon), untimed(5, mon).markDone())
        assertEquals(listOf(3L, 4L, 2L, 5L, 1L), buildDayUiState(mon, mon, tasks).untimed.map { it.id })
    }

    @Test
    fun timed_greyBelowDone_evenIfEarlier() {
        val tasks = listOf(timed(1, mon, 9).markDone(), timed(2, mon, 14), timed(3, mon, 8).cancel())
        assertEquals(listOf(2L, 1L, 3L), buildDayUiState(mon, mon, tasks).timed.map { it.id })
    }

    @Test
    fun movedTask_sinksBelowDone() {
        val moved = untimed(1, mon).moveTo(tue).old
        val tasks = listOf(moved, untimed(2, mon), untimed(3, mon).markDone())
        assertEquals(listOf(2L, 3L, 1L), buildDayUiState(mon, mon, tasks).untimed.map { it.id })
    }

    // --- gestures ---

    @Test
    fun tapCheckbox_togglesDone_andRestoresCancelled() {
        val open = untimed(1, mon)
        assertEquals(TaskStatus.DONE, tapCheckbox(open).status)
        assertEquals(TaskStatus.OPEN, tapCheckbox(open.markDone()).status)
        assertEquals(TaskStatus.OPEN, tapCheckbox(open.cancel()).status)
    }

    @Test
    fun swipeLeft_togglesCancelled() {
        val open = untimed(1, mon)
        assertEquals(TaskStatus.CANCELLED, swipeLeft(open).status)
        assertEquals(TaskStatus.OPEN, swipeLeft(open.cancel()).status)
    }

    @Test
    fun swipeRight_onlyForOpenTasks() {
        val open = untimed(1, mon)
        assertTrue(canSwipeRight(open))
        assertFalse(canSwipeRight(open.markDone()))
        assertFalse(canSwipeRight(open.cancel()))
        assertFalse(canSwipeRight(open.moveTo(tue).old))
    }

    @Test
    fun nextDayTarget_isNextDay_butNotInThePast() {
        assertEquals(tue, nextDayTarget(untimed(1, mon), today = mon))
        assertEquals(tue.plusDays(1), nextDayTarget(untimed(1, tue), today = mon))
        // Overdue: two days ago goes to today, not to yesterday.
        assertEquals(mon, nextDayTarget(untimed(1, mon.minusDays(2)), today = mon))
    }

    @Test
    fun isValidMoveTarget_todayOrLater_notOwnDay() {
        val task = untimed(1, tue)
        assertTrue(isValidMoveTarget(mon, task, today = mon))
        assertFalse(isValidMoveTarget(tue, task, today = mon))
        assertFalse(isValidMoveTarget(mon.minusDays(1), task, today = mon))
    }

    @Test
    fun gestureAvailability() {
        val open = untimed(1, mon)
        val moved = open.moveTo(tue).old

        assertTrue(canTapCheckbox(open))
        assertTrue(canTapCheckbox(open.markDone()))
        assertTrue(canTapCheckbox(open.cancel()))
        assertFalse(canTapCheckbox(moved))

        assertTrue(canSwipeLeft(open))
        assertTrue(canSwipeLeft(open.cancel()))
        assertFalse(canSwipeLeft(open.markDone()))
        assertFalse(canSwipeLeft(moved))
    }

    @Test
    fun movedTask_staysOnItsOldDay() {
        val move = untimed(1, mon).moveTo(tue)
        val tasks = listOf(move.old, move.new.copy(id = 2))

        val monday = buildDayUiState(mon, mon, tasks).untimed
        assertEquals(listOf(TaskStatus.MOVED), monday.map { it.status })

        val tuesday = buildDayUiState(tue, mon, tasks).untimed
        assertEquals(listOf(2L), tuesday.map { it.id })
    }

    @Test
    fun isToday_andIsEmpty() {
        assertTrue(buildDayUiState(mon, mon, emptyList()).isToday)
        assertFalse(buildDayUiState(tue, mon, emptyList()).isToday)
        assertTrue(buildDayUiState(mon, mon, emptyList()).isEmpty)
        assertFalse(buildDayUiState(mon, mon, listOf(untimed(1, mon))).isEmpty)
    }
}
