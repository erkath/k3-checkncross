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
    fun undoMove_notAllowed_whenCopyMovedAgain() = runBlocking {
        val copy = actions.move(1, tue)
        actions.move(copy.id, tue.plusDays(1))
        assertFalse(TaskActions.canUndoMove(repo.tasks.value, task(1)))
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
