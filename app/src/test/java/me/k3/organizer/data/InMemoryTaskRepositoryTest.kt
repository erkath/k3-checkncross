package me.k3.organizer.data

import kotlinx.coroutines.runBlocking
import me.k3.organizer.model.Task
import me.k3.organizer.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class InMemoryTaskRepositoryTest {
    private val mon = LocalDate.of(2026, 10, 12)

    @Test
    fun add_assignsIdsAfterInitialOnes() = runBlocking {
        val repo = InMemoryTaskRepository(listOf(Task(id = 5, title = "a")))
        val saved = repo.add(Task(title = "b"))
        assertEquals(6L, saved.id)
        assertEquals(listOf(5L, 6L), repo.tasks.value.map { it.id })
    }

    @Test
    fun add_rejectsSavedTask() {
        val repo = InMemoryTaskRepository()
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.add(Task(id = 1, title = "a")) } }
    }

    @Test
    fun update_replacesOnlyThatTask() = runBlocking {
        val repo = InMemoryTaskRepository(listOf(Task(id = 1, title = "a", day = mon), Task(id = 2, title = "b", day = mon)))
        repo.update(2, Task::markDone)
        assertEquals(listOf(TaskStatus.OPEN, TaskStatus.DONE), repo.tasks.value.map { it.status })
    }

    @Test
    fun update_unknownId_rejected() {
        val repo = InMemoryTaskRepository()
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.update(1) { it } } }
    }

    @Test
    fun update_cannotChangeId() {
        val repo = InMemoryTaskRepository(listOf(Task(id = 1, title = "a")))
        assertThrows(IllegalStateException::class.java) { runBlocking { repo.update(1) { it.copy(id = 9) } } }
    }
}
