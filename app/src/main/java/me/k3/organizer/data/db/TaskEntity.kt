package me.k3.organizer.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import me.k3.organizer.model.Task
import me.k3.organizer.model.TaskStatus
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/**
 * A row of the `tasks` table. Plain SQLite-friendly columns; [Task] is the model the app works with.
 * Dates are stored as epoch days (sortable, easy to query by day), times in whole minutes.
 */
@Entity(tableName = "tasks", indices = [Index("day"), Index("movedFrom")])
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String?,
    /** [TaskStatus] name; names, not ordinals, so reordering the enum cannot corrupt data. */
    val status: String,
    /** [LocalDate.toEpochDay]; null: backlog. */
    val day: Long?,
    /** Minutes since midnight. */
    val startMinute: Int?,
    val durationMinutes: Long?,
    /** [LocalDate.toEpochDay] of the day a MOVED task went to; null: backlog or not moved. */
    val movedTo: Long?,
    /** Id of the MOVED task this one was copied from. */
    val movedFrom: Long?,
)

fun TaskEntity.toTask() = Task(
    id = id,
    title = title,
    note = note,
    status = TaskStatus.valueOf(status),
    day = day?.let(LocalDate::ofEpochDay),
    startTime = startMinute?.let { LocalTime.ofSecondOfDay(it * 60L) },
    duration = durationMinutes?.let(Duration::ofMinutes),
    movedTo = movedTo?.let(LocalDate::ofEpochDay),
    movedFrom = movedFrom,
)

/** Seconds of [Task.startTime] and [Task.duration] are dropped: the app works in whole minutes. */
fun Task.toEntity() = TaskEntity(
    id = id,
    title = title,
    note = note,
    status = status.name,
    day = day?.toEpochDay(),
    startMinute = startTime?.let { it.hour * 60 + it.minute },
    durationMinutes = duration?.toMinutes(),
    movedTo = movedTo?.toEpochDay(),
    movedFrom = movedFrom,
)
