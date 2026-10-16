package me.k3.organizer.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import me.k3.organizer.model.Frequency
import me.k3.organizer.model.Routine
import me.k3.organizer.model.RoutineMark
import me.k3.organizer.model.TaskStatus
import java.time.LocalDate

/** A row of `routines`. Dates as epoch days, enums by name, as in [TaskEntity]. */
@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String?,
    val frequency: String,
    val createdOn: Long,
    val active: Boolean,
)

/** A row of `routine_marks`: at most one mark per routine and period. */
@Entity(tableName = "routine_marks", primaryKeys = ["routineId", "periodStart"], indices = [Index("routineId")])
data class RoutineMarkEntity(
    val routineId: Long,
    val periodStart: Long,
    val status: String,
    val markedOn: Long,
)

fun RoutineEntity.toRoutine() = Routine(
    id = id,
    title = title,
    note = note,
    frequency = Frequency.valueOf(frequency),
    createdOn = LocalDate.ofEpochDay(createdOn),
    active = active,
)

fun Routine.toEntity() = RoutineEntity(
    id = id,
    title = title,
    note = note,
    frequency = frequency.name,
    createdOn = createdOn.toEpochDay(),
    active = active,
)

fun RoutineMarkEntity.toMark() = RoutineMark(
    routineId = routineId,
    periodStart = LocalDate.ofEpochDay(periodStart),
    status = TaskStatus.valueOf(status),
    markedOn = LocalDate.ofEpochDay(markedOn),
)

fun RoutineMark.toEntity() = RoutineMarkEntity(
    routineId = routineId,
    periodStart = periodStart.toEpochDay(),
    status = status.name,
    markedOn = markedOn.toEpochDay(),
)
