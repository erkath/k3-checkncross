package me.k3.organizer.data

import kotlinx.coroutines.runBlocking
import me.k3.organizer.model.Placement
import me.k3.organizer.model.Task
import me.k3.organizer.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

class TaskActionsTest {
    private val mon = LocalDate.of(2026, 10, 12)
    private val tue = mon.plusDays(1)
    private val wed = mon.plusDays(2)
    private val nine = LocalTime.of(9, 0)
    private val hour = Duration.ofHours(1)

    private val repo = InMemoryTaskRepository(
        listOf(
            Task(id = 1, title = "Dentist", day = mon, startTime = nine, duration = hour),
            Task(id = 2, title = "Buy milk", day = mon),
        ),
    )
    private val actions = TaskActions(repo)
    private fun task(id: Long) = repo.tasks.value.first { it.id == id }

    @Test
    fun move_keepsTime_andLinksCopy() = runBlocking {
        val copy = actions.move(1, tue)

        assertEquals(TaskStatus.MOVED, task(1).status)
        assertEquals(tue, task(1).movedTo)
        assertEquals(3L, copy.id)
        assertEquals(1L, copy.movedFrom)
        assertEquals(Placement.Timed(tue, nine, hour), task(3).placement)
    }

    @Test
    fun toBacklog_leavesTrace() = runBlocking {
        val copy = actions.toBacklog(2)
        assertEquals(TaskStatus.MOVED, task(2).status)
        assertNull(task(2).movedTo)
        assertEquals(Placement.Backlog, task(copy.id).placement)
    }

    @Test
    fun undoMove_reopensAndDeletesCopy() = runBlocking {
        actions.move(1, tue)
        assertTrue(TaskActions.canUndoMove(repo.tasks.value, task(1)))

        actions.undoMove(1)

        assertEquals(listOf(1L, 2L), repo.tasks.value.map { it.id })
        assertEquals(TaskStatus.OPEN, task(1).status)
        assertEquals(Placement.Timed(mon, nine, hour), task(1).placement)
    }

    @Test
    fun undoMove_notAllowed_whenCopyIsDone() = runBlocking<Unit> {
        val copy = actions.move(1, tue)
        repo.update(copy.id) { it.markDone() }

        assertFalse(TaskActions.canUndoMove(repo.tasks.value, task(1)))
        assertThrows(IllegalStateException::class.java) { runBlocking { actions.undoMove(1) } }
    }

    @Test
    fun undoMove_revertsWholeChain() = runBlocking {
        val copy = actions.move(1, tue)
        actions.move(copy.id, wed)
        assertTrue(TaskActions.canUndoMove(repo.tasks.value, task(1)))

        actions.undoMove(1)

        assertEquals(listOf(1L, 2L), repo.tasks.value.map { it.id })
        assertEquals(Placement.Timed(mon, nine, hour), task(1).placement)
        assertEquals(TaskStatus.OPEN, task(1).status)
    }

    @Test
    fun undoMove_notAllowed_whenChainHeadIsClosed() = runBlocking {
        val copy = actions.move(1, tue)
        val head = actions.move(copy.id, wed)
        repo.update(head.id) { it.cancel() }
        assertFalse(TaskActions.canUndoMove(repo.tasks.value, task(1)))
        assertFalse(TaskActions.canUndoMove(repo.tasks.value, task(copy.id)))
    }

    // --- moving back to a day the task has been on ---

    @Test
    fun moveBack_reusesOriginal_insteadOfDuplicate() = runBlocking {
        val copy = actions.move(2, tue)
        val back = actions.move(copy.id, mon)

        assertEquals(2L, back.id)
        assertEquals(listOf(1L, 2L), repo.tasks.value.map { it.id })
        assertEquals(TaskStatus.OPEN, task(2).status)
        assertNull(task(2).movedTo)
        assertEquals(mon, task(2).day)
    }

    @Test
    fun moveBack_overSeveralDays_dropsTheDetour() = runBlocking {
        val tueCopy = actions.move(2, tue)
        val wedCopy = actions.move(tueCopy.id, wed)
        val back = actions.move(wedCopy.id, tue)

        // Back on Tuesday: the Tuesday entry is live again, Wednesday's copy is gone, Monday keeps its trace.
        assertEquals(tueCopy.id, back.id)
        assertEquals(listOf(1L, 2L, tueCopy.id), repo.tasks.value.map { it.id })
        assertEquals(TaskStatus.MOVED, task(2).status)
        assertEquals(TaskStatus.OPEN, task(tueCopy.id).status)
        assertEquals(listOf(task(2)), TaskActions.chainBefore(repo.tasks.value, task(tueCopy.id)))
    }

    @Test
    fun moveBack_keepsLatestTitleNoteAndTime() = runBlocking {
        val copy = actions.move(1, tue)
        repo.update(copy.id) {
            it.copy(title = "Dentist (new clinic)", note = "3rd floor").schedule(tue, LocalTime.of(15, 0), hour)
        }

        actions.move(copy.id, mon)

        assertEquals("Dentist (new clinic)", task(1).title)
        assertEquals("3rd floor", task(1).note)
        assertEquals(Placement.Timed(mon, LocalTime.of(15, 0), hour), task(1).placement)
    }

    @Test
    fun move_closedTask_rejected() = runBlocking<Unit> {
        repo.update(2) { it.markDone() }
        assertThrows(IllegalStateException::class.java) { runBlocking { actions.move(2, tue) } }
    }

    // --- planning from the backlog ---

    @Test
    fun schedule_backlogTask_keepsIdAndLeavesNoTrace() = runBlocking {
        val idea = repo.add(Task(title = "Idea"))
        val planned = actions.schedule(idea.id, tue)

        assertEquals(idea.id, planned.id)
        assertEquals(Placement.Untimed(tue), task(idea.id).placement)
        assertEquals(3, repo.tasks.value.size)
    }

    @Test
    fun schedule_backToDayItCameFrom_revertsInsteadOfDuplicate() = runBlocking {
        val inBacklog = actions.toBacklog(1)
        val back = actions.schedule(inBacklog.id, mon)

        assertEquals(1L, back.id)
        assertEquals(listOf(1L, 2L), repo.tasks.value.map { it.id })
        assertEquals(TaskStatus.OPEN, task(1).status)
        // It returns to its own slot in the time grid.
        assertEquals(Placement.Timed(mon, nine, hour), task(1).placement)
    }

    @Test
    fun schedule_toAnotherDay_keepsTheTrace() = runBlocking {
        val inBacklog = actions.toBacklog(1)
        actions.schedule(inBacklog.id, tue)

        assertEquals(TaskStatus.MOVED, task(1).status)
        assertEquals(Placement.Untimed(tue), task(inBacklog.id).placement)
    }

    @Test
    fun schedule_datedOrClosedTask_rejected() = runBlocking<Unit> {
        assertThrows(IllegalStateException::class.java) { runBlocking { actions.schedule(2, tue) } }
        val idea = repo.add(Task(title = "Idea"))
        repo.update(idea.id) { it.cancel() }
        assertThrows(IllegalStateException::class.java) { runBlocking { actions.schedule(idea.id, tue) } }
    }

    @Test
    fun chains_followLinksBothWays() = runBlocking {
        val tueCopy = actions.move(2, tue)
        val wedCopy = actions.move(tueCopy.id, wed)
        val all = repo.tasks.value

        assertEquals(listOf(2L, tueCopy.id), TaskActions.chainBefore(all, wedCopy).map { it.id })
        assertEquals(listOf(tueCopy.id, wedCopy.id), TaskActions.chainAfter(all, task(2)).map { it.id })
        assertEquals(emptyList<Task>(), TaskActions.chainAfter(all, wedCopy))
    }

    @Test
    fun canUndoMove_falseForOpenTask() {
        assertFalse(TaskActions.canUndoMove(repo.tasks.value, task(1)))
    }

    @Test
    fun delete_removesTask_andUnlinksCopy() = runBlocking {
        val copy = actions.move(1, tue)
        actions.delete(1)

        assertEquals(listOf(2L, copy.id), repo.tasks.value.map { it.id })
        assertNull(task(copy.id).movedFrom)
    }

    @Test
    fun unknownId_rejected() {
        assertThrows(IllegalArgumentException::class.java) { runBlocking { actions.move(99, tue) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { actions.delete(99) } }
    }
}
