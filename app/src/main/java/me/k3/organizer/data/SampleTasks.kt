package me.k3.organizer.data

import me.k3.organizer.model.Task
import me.k3.organizer.model.TaskStatus
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/** Demo data around [today], until tasks can be created in the UI. Also used by previews. */
fun sampleTasks(today: LocalDate): List<Task> {
    val yesterday = today.minusDays(1)
    return listOf(
        Task(id = 1, title = "Morning run", day = today, startTime = LocalTime.of(7, 30), duration = Duration.ofMinutes(45)),
        Task(id = 2, title = "Dentist", note = "Bring the insurance card", day = today,
            startTime = LocalTime.of(14, 0), duration = Duration.ofHours(1)),
        Task(id = 3, title = "Call the bank", status = TaskStatus.MOVED, day = yesterday, movedTo = today),
        Task(id = 4, title = "Call the bank", day = today, movedFrom = 3),
        Task(id = 5, title = "Buy milk", status = TaskStatus.DONE, day = today),
        Task(id = 6, title = "Fix the bike", status = TaskStatus.CANCELLED, day = today),
        Task(id = 7, title = "Pay rent", day = yesterday),
        Task(id = 8, title = "Read Kotlin docs"),
    )
}
