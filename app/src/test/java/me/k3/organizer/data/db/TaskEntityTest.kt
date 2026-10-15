package me.k3.organizer.data.db

import me.k3.organizer.model.Task
import me.k3.organizer.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

class TaskEntityTest {
    private val mon = LocalDate.of(2026, 10, 12)

    private fun roundTrip(task: Task) = assertEquals(task, task.toEntity().toTask())

    @Test
    fun roundTrip_allShapes() {
        roundTrip(Task(id = 1, title = "Backlog"))
        roundTrip(Task(id = 2, title = "Untimed", note = "with note", day = mon))
        roundTrip(Task(id = 3, title = "Timed", day = mon, startTime = LocalTime.of(23, 45), duration = Duration.ofMinutes(90)))
        roundTrip(Task(id = 4, title = "Done", status = TaskStatus.DONE, day = mon))
        roundTrip(Task(id = 5, title = "Moved", status = TaskStatus.MOVED, day = mon, movedTo = mon.plusDays(3)))
        roundTrip(Task(id = 6, title = "To backlog", status = TaskStatus.MOVED, day = mon))
        roundTrip(Task(id = 7, title = "Copy", day = mon.plusDays(3), movedFrom = 5))
    }

    @Test
    fun columns_useStableEncodings() {
        val entity = Task(
            id = 9, title = "x", status = TaskStatus.CANCELLED, day = LocalDate.of(1970, 1, 2),
            startTime = LocalTime.of(9, 30), duration = Duration.ofHours(2),
        ).toEntity()

        assertEquals("CANCELLED", entity.status)
        assertEquals(1L, entity.day)
        assertEquals(570, entity.startMinute)
        assertEquals(120L, entity.durationMinutes)
    }

    @Test
    fun seconds_areDropped() {
        val task = Task(title = "x", day = mon, startTime = LocalTime.of(9, 30, 59), duration = Duration.ofSeconds(150))
        val back = task.toEntity().toTask()
        assertEquals(LocalTime.of(9, 30), back.startTime)
        assertEquals(Duration.ofMinutes(2), back.duration)
    }
}
