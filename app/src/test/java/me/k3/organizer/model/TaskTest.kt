package me.k3.organizer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

class TaskTest {
    private val mon = LocalDate.of(2026, 10, 12)
    private val tue = mon.plusDays(1)
    private val nine = LocalTime.of(9, 0)
    private val hour = Duration.ofHours(1)

    private fun task(day: LocalDate? = mon) = Task(id = 7, title = "Buy milk", day = day)

    // --- validation ---

    @Test
    fun blankTitle_rejected() {
        assertThrows(IllegalArgumentException::class.java) { Task(title = "  ") }
    }

    @Test
    fun timeWithoutDay_rejected() {
        assertThrows(IllegalArgumentException::class.java) {
            Task(title = "x", startTime = nine, duration = hour)
        }
    }

    @Test
    fun timeWithoutDuration_rejected() {
        assertThrows(IllegalArgumentException::class.java) {
            Task(title = "x", day = mon, startTime = nine)
        }
    }

    @Test
    fun nonPositiveDuration_rejected() {
        assertThrows(IllegalArgumentException::class.java) {
            Task(title = "x", day = mon, startTime = nine, duration = Duration.ZERO)
        }
    }

    @Test
    fun movedToWithoutMovedStatus_rejected() {
        assertThrows(IllegalArgumentException::class.java) {
            Task(title = "x", day = mon, movedTo = tue)
        }
    }

    @Test
    fun movedBacklogTask_rejected() {
        assertThrows(IllegalArgumentException::class.java) {
            Task(title = "x", status = TaskStatus.MOVED)
        }
    }

    @Test
    fun movedFromUnsavedId_rejected() {
        assertThrows(IllegalArgumentException::class.java) { Task(title = "x", movedFrom = 0) }
    }

    // --- placement ---

    @Test
    fun placement_followsDayAndTime() {
        assertEquals(Placement.Backlog, task(day = null).placement)
        assertEquals(Placement.Untimed(mon), task().placement)
        assertEquals(
            Placement.Timed(mon, nine, hour),
            task().schedule(mon, nine, hour).placement,
        )
    }

    // --- status transitions ---

    @Test
    fun markDone_andReopen() {
        val done = task().markDone()
        assertEquals(TaskStatus.DONE, done.status)
        assertEquals(TaskStatus.OPEN, done.reopen().status)
    }

    @Test
    fun cancel_andReopen() {
        val cancelled = task().cancel()
        assertEquals(TaskStatus.CANCELLED, cancelled.status)
        assertEquals(TaskStatus.OPEN, cancelled.reopen().status)
    }

    @Test
    fun closedTask_cannotBeClosedAgain() {
        assertThrows(IllegalStateException::class.java) { task().markDone().cancel() }
        assertThrows(IllegalStateException::class.java) { task().cancel().markDone() }
    }

    @Test
    fun openTask_cannotBeReopened() {
        assertThrows(IllegalStateException::class.java) { task().reopen() }
    }

    @Test
    fun movedTask_canOnlyBeReopened() {
        val old = task().moveTo(tue).old
        assertThrows(IllegalStateException::class.java) { old.markDone() }
        assertThrows(IllegalStateException::class.java) { old.moveTo(tue.plusDays(1)) }

        val reopened = old.reopen()
        assertEquals(TaskStatus.OPEN, reopened.status)
        assertEquals(mon, reopened.day)
        assertNull(reopened.movedTo)
    }

    @Test
    fun movedToBacklog_canBeReopened() {
        assertEquals(task(), task().toBacklog().old.reopen())
    }

    // --- scheduling ---

    @Test
    fun schedule_fromBacklog_keepsIdAndStatus() {
        val t = task(day = null).schedule(mon, nine, hour)
        assertEquals(7L, t.id)
        assertEquals(TaskStatus.OPEN, t.status)
        assertEquals(Placement.Timed(mon, nine, hour), t.placement)
    }

    @Test
    fun schedule_changesTimeWithinSameDay() {
        val t = task().schedule(mon, nine, hour).schedule(mon)
        assertEquals(Placement.Untimed(mon), t.placement)
    }

    @Test
    fun schedule_toAnotherDay_rejected() {
        assertThrows(IllegalStateException::class.java) { task().schedule(tue) }
    }

    // --- moving ---

    @Test
    fun moveTo_marksOldAsMoved_andCreatesLinkedCopy() {
        val move = task().schedule(mon, nine, hour).moveTo(tue)

        assertEquals(TaskStatus.MOVED, move.old.status)
        assertEquals(tue, move.old.movedTo)
        assertEquals(mon, move.old.day)
        assertEquals(7L, move.old.id)

        assertEquals(0L, move.new.id)
        assertEquals(7L, move.new.movedFrom)
        assertEquals(TaskStatus.OPEN, move.new.status)
        assertEquals(Placement.Untimed(tue), move.new.placement)
        assertNull(move.new.movedTo)
        assertEquals("Buy milk", move.new.title)
    }

    @Test
    fun moveTo_canSetNewTime() {
        val move = task().moveTo(tue, nine, hour)
        assertEquals(Placement.Timed(tue, nine, hour), move.new.placement)
    }

    @Test
    fun moveTo_rejectsBacklogAndSameDay() {
        assertThrows(IllegalStateException::class.java) { task(day = null).moveTo(tue) }
        assertThrows(IllegalStateException::class.java) { task().moveTo(mon) }
    }

    @Test
    fun toBacklog_leavesMovedTrace() {
        val move = task().schedule(mon, nine, hour).toBacklog()

        assertEquals(TaskStatus.MOVED, move.old.status)
        assertNull(move.old.movedTo)
        assertEquals(Placement.Timed(mon, nine, hour), move.old.placement)

        assertEquals(0L, move.new.id)
        assertEquals(7L, move.new.movedFrom)
        assertEquals(TaskStatus.OPEN, move.new.status)
        assertEquals(Placement.Backlog, move.new.placement)
    }

    @Test
    fun toBacklog_rejectsBacklogTask() {
        assertThrows(IllegalStateException::class.java) { task(day = null).toBacklog() }
    }

    @Test
    fun move_rejectsUnsavedTask() {
        val unsaved = Task(title = "x", day = mon)
        assertThrows(IllegalStateException::class.java) { unsaved.moveTo(tue) }
        assertThrows(IllegalStateException::class.java) { unsaved.toBacklog() }
    }

    // --- overdue ---

    @Test
    fun isOverdue_onlyForOpenTasksOnPastDays() {
        assertTrue(task().isOverdue(today = tue))
        assertFalse(task().isOverdue(today = mon))
        assertFalse(task(day = null).isOverdue(today = tue))
        assertFalse(task().markDone().isOverdue(today = tue))
        assertFalse(task().moveTo(tue).old.isOverdue(today = tue.plusDays(1)))
    }
}
