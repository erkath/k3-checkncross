package me.k3.organizer

import android.app.Application
import me.k3.organizer.data.InMemoryTaskRepository
import me.k3.organizer.data.TaskRepository
import me.k3.organizer.data.sampleTasks
import java.time.LocalDate

/** Lives as long as the process; holds app-wide objects shared by all screens. */
class OrganizerApp : Application() {
    val taskRepository: TaskRepository by lazy { InMemoryTaskRepository(sampleTasks(LocalDate.now())) }
}
