package me.k3.organizer.ui.day

import me.k3.organizer.model.Placement
import me.k3.organizer.model.Task
import me.k3.organizer.model.TaskStatus
import java.time.LocalDate

data class DayUiState(
    val day: LocalDate,
    val isToday: Boolean,
    /** Tasks with a start time: open ones first, each group by start time. */
    val timed: List<Task> = emptyList(),
    /** Tasks on this day without a time: open ones first, each group in creation order. */
    val untimed: List<Task> = emptyList(),
) {
    val isEmpty: Boolean get() = timed.isEmpty() && untimed.isEmpty()
}

/** Picks the tasks of [day] out of [tasks]. Closed and MOVED tasks sink to the bottom of their section. */
fun buildDayUiState(day: LocalDate, today: LocalDate, tasks: List<Task>): DayUiState {
    val onDay = tasks.filter { it.day == day }
    val closedLast = compareBy<Task> { it.status != TaskStatus.OPEN }
    return DayUiState(
        day = day,
        isToday = day == today,
        timed = onDay
            .filter { it.placement is Placement.Timed }
            .sortedWith(closedLast.thenBy { it.startTime }.thenBy { it.id }),
        untimed = onDay
            .filter { it.placement is Placement.Untimed }
            .sortedWith(closedLast.thenBy { it.id }),
    )
}

/** Tap on the checkbox: OPEN ↔ DONE; a cancelled task is restored to OPEN. */
fun tapCheckbox(task: Task): Task =
    if (task.status == TaskStatus.CANCELLED) task.reopen() else task.toggleDone()

/** Swipe left: OPEN ↔ CANCELLED. */
fun swipeLeft(task: Task): Task = task.toggleCancelled()

fun canTapCheckbox(task: Task): Boolean = task.status != TaskStatus.MOVED

fun canSwipeLeft(task: Task): Boolean =
    task.status == TaskStatus.OPEN || task.status == TaskStatus.CANCELLED
