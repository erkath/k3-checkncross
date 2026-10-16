package me.k3.organizer

import android.app.Application
import me.k3.organizer.data.AppPreferences
import me.k3.organizer.data.EventMarkRepository
import me.k3.organizer.data.RoomEventMarkRepository
import me.k3.organizer.data.RoomRoutineRepository
import me.k3.organizer.data.RoomTaskRepository
import me.k3.organizer.data.RoutineRepository
import me.k3.organizer.data.TaskRepository
import me.k3.organizer.data.calendar.AndroidCalendarSource
import me.k3.organizer.data.calendar.CalendarSource
import me.k3.organizer.data.db.OrganizerDatabase

/** Lives as long as the process; holds app-wide objects shared by all screens. */
class OrganizerApp : Application() {
    val database: OrganizerDatabase by lazy { OrganizerDatabase.build(this) }
    val taskRepository: TaskRepository by lazy { RoomTaskRepository(database) }
    val routineRepository: RoutineRepository by lazy { RoomRoutineRepository(database) }
    val eventMarkRepository: EventMarkRepository by lazy { RoomEventMarkRepository(database) }
    val calendarSource: CalendarSource by lazy { AndroidCalendarSource(this) }
    val preferences: AppPreferences by lazy { AppPreferences(this) }
}
