package me.k3.organizer.data

import kotlinx.coroutines.runBlocking
import me.k3.organizer.model.EventMark
import me.k3.organizer.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class InMemoryEventMarkRepositoryTest {
    private val repo = InMemoryEventMarkRepository()

    @Test
    fun set_replacesSameOccurrence_clearRemovesIt() = runBlocking {
        repo.set(EventMark(1, 100, TaskStatus.DONE))
        repo.set(EventMark(1, 200, TaskStatus.DONE))
        repo.set(EventMark(1, 100, TaskStatus.CANCELLED))
        assertEquals(setOf(EventMark(1, 200, TaskStatus.DONE), EventMark(1, 100, TaskStatus.CANCELLED)), repo.marks.value.toSet())

        repo.clear(1, 100)
        assertEquals(listOf(EventMark(1, 200, TaskStatus.DONE)), repo.marks.value)
    }
}
