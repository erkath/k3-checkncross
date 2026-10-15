package me.k3.organizer

import android.app.Application
import me.k3.organizer.data.RoomTaskRepository
import me.k3.organizer.data.TaskRepository
import me.k3.organizer.data.db.OrganizerDatabase

/** Lives as long as the process; holds app-wide objects shared by all screens. */
class OrganizerApp : Application() {
    val database: OrganizerDatabase by lazy { OrganizerDatabase.build(this) }
    val taskRepository: TaskRepository by lazy { RoomTaskRepository(database) }
}
