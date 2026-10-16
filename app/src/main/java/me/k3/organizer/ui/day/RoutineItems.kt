package me.k3.organizer.ui.day

import me.k3.organizer.model.Frequency
import me.k3.organizer.model.Routine
import me.k3.organizer.model.RoutineMark
import me.k3.organizer.model.TaskStatus
import java.time.DayOfWeek
import java.time.LocalDate

/** A routine as shown on one day: its period there and how it stands. */
data class RoutineItem(
    val routine: Routine,
    val periodStart: LocalDate,
    /** OPEN (no mark), DONE or CANCELLED. */
    val status: TaskStatus,
    /** Marks can be set for today and past days, not ahead. */
    val canMark: Boolean,
)

/**
 * The routines to show on [day]: active ones created on or before it. A daily routine shows its
 * mark for that day. A weekly one stays open on every day of its week until marked, and then
 * shows only on the day it was marked. Open first, then done, then crossed; daily before weekly.
 */
fun routinesOn(
    day: LocalDate,
    today: LocalDate,
    routines: List<Routine>,
    marks: List<RoutineMark>,
    firstDayOfWeek: DayOfWeek,
): List<RoutineItem> {
    val markByPeriod = marks.associateBy { it.routineId to it.periodStart }
    return routines
        .filter { it.active && it.createdOn <= day }
        .mapNotNull { routine ->
            val period = routine.periodStart(day, firstDayOfWeek)
            val mark = markByPeriod[routine.id to period]
            if (routine.frequency == Frequency.WEEKLY && mark != null && mark.markedOn != day) return@mapNotNull null
            RoutineItem(routine, period, mark?.status ?: TaskStatus.OPEN, canMark = day <= today)
        }
        .sortedWith(compareBy({ statusOrder(it.status) }, { it.routine.frequency }, { it.routine.id }))
}

private fun statusOrder(status: TaskStatus) = when (status) {
    TaskStatus.OPEN -> 0
    TaskStatus.DONE -> 1
    else -> 2
}

/** What a gesture on a routine does to its marks. */
sealed interface RoutineChange {
    data class Set(val mark: RoutineMark) : RoutineChange
    data class Clear(val routineId: Long, val periodStart: LocalDate) : RoutineChange
}

/** Checkbox: open → done (marked on [day]); done or crossed → open again. */
fun routineCheckboxTap(item: RoutineItem, day: LocalDate): RoutineChange? {
    if (!item.canMark) return null
    return when (item.status) {
        TaskStatus.OPEN -> RoutineChange.Set(RoutineMark(item.routine.id, item.periodStart, TaskStatus.DONE, day))
        else -> RoutineChange.Clear(item.routine.id, item.periodStart)
    }
}

/** Swipe left: open → crossed (skipped this time); crossed → open again. Not on a done one. */
fun routineSwipeLeft(item: RoutineItem, day: LocalDate): RoutineChange? {
    if (!canSwipeLeft(item)) return null
    return when (item.status) {
        TaskStatus.OPEN -> RoutineChange.Set(RoutineMark(item.routine.id, item.periodStart, TaskStatus.CANCELLED, day))
        else -> RoutineChange.Clear(item.routine.id, item.periodStart)
    }
}

fun canSwipeLeft(item: RoutineItem): Boolean =
    item.canMark && (item.status == TaskStatus.OPEN || item.status == TaskStatus.CANCELLED)
