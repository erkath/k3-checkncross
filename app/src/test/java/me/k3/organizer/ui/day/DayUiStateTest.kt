package me.k3.organizer.ui.day

import me.k3.organizer.model.CalendarEvent
import me.k3.organizer.model.Task
import me.k3.organizer.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
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

    // --- backlog ---

    @Test
    fun backlog_openUndatedTasks_inCreationOrder_sameForAnyDay() {
        val tasks = listOf(untimed(3, null), untimed(1, null), untimed(2, null).cancel(), untimed(4, mon))
        assertEquals(listOf(1L, 3L), buildDayUiState(mon, mon, tasks).backlog.map { it.id })
        assertEquals(listOf(1L, 3L), buildDayUiState(tue, mon, tasks).backlog.map { it.id })
    }

    @Test
    fun backlog_doesNotCountForEmptyDay() {
        assertTrue(buildDayUiState(mon, mon, listOf(untimed(1, null))).isEmpty)
    }

    @Test
    fun backlogTarget_isShownDay_butNotInThePast() {
        assertEquals(tue, backlogTarget(shownDay = tue, today = mon))
        assertEquals(mon, backlogTarget(shownDay = mon, today = mon))
        assertEquals(mon, backlogTarget(shownDay = mon.minusDays(3), today = mon))
    }

    // --- overdue ---

    @Test
    fun overdue_openTasksBeforeToday_byDayThenTime() {
        val sat = mon.minusDays(2)
        val sun = mon.minusDays(1)
        val tasks = listOf(
            timed(1, sun, 14),
            untimed(2, sun),
            timed(3, sat, 9),
            untimed(4, sun).markDone(),
            untimed(5, sun).cancel(),
            untimed(6, mon),
            untimed(7, null),
        )
        assertEquals(listOf(3L, 2L, 1L), overdueTasks(tasks, today = mon).map { it.id })
    }

    @Test
    fun overdue_movedTraceIsNotOverdue() {
        val moved = untimed(1, mon.minusDays(1)).moveTo(mon).old
        assertEquals(emptyList<Task>(), overdueTasks(listOf(moved), today = mon))
    }

    @Test
    fun overdue_sameForAnyShownDay() {
        val tasks = listOf(untimed(1, mon.minusDays(1)))
        assertEquals(listOf(1L), buildDayUiState(tue, mon, tasks).overdue.map { it.id })
        assertEquals(listOf(1L), buildDayUiState(mon.minusDays(1), mon, tasks).overdue.map { it.id })
    }

    @Test
    fun scheduled_sameTime_openTaskThenClosedTaskThenEvent() {
        val tasks = listOf(timed(1, mon, 9).markDone(), timed(2, mon, 9))
        val events = listOf(event(10, mon.atTime(9, 0), mon.atTime(10, 0)))
        assertEquals(listOf("t2", "t1", "e10"), ids(buildDayUiState(mon, mon, tasks, events).scheduled))
    }

    // --- calendar events ---

    private fun event(id: Long, start: LocalDateTime, end: LocalDateTime, allDay: Boolean = false) =
        CalendarEvent(id, "e$id", allDay, start, end, color = null, location = null, beginMillis = 0, endMillis = 0)

    private fun ids(items: List<ScheduledItem>) = items.map {
        when (it) {
            is ScheduledItem.TaskItem -> "t${it.task.id}"
            is ScheduledItem.EventItem -> "e${it.event.eventId}"
        }
    }

    @Test
    fun scheduled_isATimeline_closedTasksStayInPlace() {
        val tasks = listOf(timed(1, mon, 9), timed(2, mon, 14), timed(3, mon, 8).markDone())
        val events = listOf(event(10, mon.atTime(11, 0), mon.atTime(12, 0)), event(11, mon.atTime(7, 0), mon.atTime(7, 30)))

        val state = buildDayUiState(mon, mon, tasks, events)

        // The done 8:00 task keeps its place in time instead of sinking to the bottom.
        assertEquals(listOf("e11", "t3", "t1", "e10", "t2"), ids(state.scheduled))
        // The task-only list keeps its own order: closed tasks last.
        assertEquals(listOf(1L, 2L, 3L), state.timed.map { it.id })
    }

    @Test
    fun scheduled_taskBeforeEventOnTie_eventFromYesterdayFirst() {
        val events = listOf(
            event(10, mon.atTime(9, 0), mon.atTime(10, 0)),
            event(11, mon.minusDays(1).atTime(22, 0), mon.atTime(1, 0)),
        )
        assertEquals(listOf("e11", "t1", "e10"), ids(buildDayUiState(mon, mon, listOf(timed(1, mon, 9)), events).scheduled))
    }

    @Test
    fun allDayEvents_separate_andCountForEmptyDay() {
        val holiday = event(20, mon.atStartOfDay(), tue.atStartOfDay(), allDay = true)
        val state = buildDayUiState(mon, mon, emptyList(), listOf(holiday))

        assertEquals(listOf(holiday), state.allDayEvents)
        assertTrue(state.scheduled.isEmpty())
        assertFalse(state.isEmpty)
    }

    // --- the date changing ---

    @Test
    fun shownDay_followsToday_onlyIfItWasToday() {
        assertEquals(tue, shownDayAfterTodayChange(shown = mon, oldToday = mon, newToday = tue))
        val friday = mon.plusDays(4)
        assertEquals(friday, shownDayAfterTodayChange(shown = friday, oldToday = mon, newToday = tue))
        assertEquals(mon.minusDays(3), shownDayAfterTodayChange(shown = mon.minusDays(3), oldToday = mon, newToday = tue))
    }

    @Test
    fun newDay_turnsYesterdaysOpenTasksOverdue() {
        val tasks = listOf(untimed(1, mon))
        assertTrue(buildDayUiState(mon, today = mon, tasks).overdue.isEmpty())
        assertEquals(listOf(1L), buildDayUiState(tue, today = tue, tasks).overdue.map { it.id })
        assertTrue(buildDayUiState(tue, today = tue, tasks).isToday)
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
