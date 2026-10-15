package me.k3.organizer.ui.day

import me.k3.organizer.model.Placement
import me.k3.organizer.model.Task
import me.k3.organizer.model.TaskStatus
import java.time.LocalDate

data class DayUiState(
    val day: LocalDate,
    val isToday: Boolean,
    /** Tasks with a start time: open, then done, then cancelled/moved; each group by start time. */
    val timed: List<Task> = emptyList(),
    /** Tasks on this day without a time: open, then done, then cancelled/moved; each group in creation order. */
    val untimed: List<Task> = emptyList(),
) {
    val isEmpty: Boolean get() = timed.isEmpty() && untimed.isEmpty()
}

/** Order of status groups within a section: open, then done (green), then cancelled and moved (grey). */
private fun statusRank(status: TaskStatus): Int = when (status) {
    TaskStatus.OPEN -> 0
    TaskStatus.DONE -> 1
    TaskStatus.CANCELLED, TaskStatus.MOVED -> 2
}

/** Picks the tasks of [day] out of [tasks], ordered by status group within each section. */
fun buildDayUiState(day: LocalDate, today: LocalDate, tasks: List<Task>): DayUiState {
    val onDay = tasks.filter { it.day == day }
    val closedLast = compareBy<Task> { statusRank(it.status) }
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

/** Swipe right: move to the next day. Only an open task on a day can be moved. */
fun canSwipeRight(task: Task): Boolean = task.status == TaskStatus.OPEN && task.day != null

/** Where swipe right sends [task]: the day after its own, but never into the past. */
fun nextDayTarget(task: Task, today: LocalDate): LocalDate {
    val day = checkNotNull(task.day) { "backlog task has no next day" }
    return maxOf(day.plusDays(1), today)
}

/** A day the task can be moved to from the date picker: today or later, and not its own day. */
fun isValidMoveTarget(date: LocalDate, task: Task, today: LocalDate): Boolean =
    date >= today && date != task.day
