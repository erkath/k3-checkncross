package me.k3.organizer.ui.day

import me.k3.organizer.model.Placement
import me.k3.organizer.model.Task
import me.k3.organizer.model.TaskStatus
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

data class DayUiState(
    val day: LocalDate,
    /** The current date; changes at midnight. */
    val today: LocalDate,
    /** Tasks with a start time: open, then done, then cancelled/moved; each group by start time. */
    val timed: List<Task> = emptyList(),
    /** Tasks on this day without a time: open, then done, then cancelled/moved; each group in creation order. */
    val untimed: List<Task> = emptyList(),
    /** Open tasks without a day, in creation order. Shown in the backlog sheet, counted on its button. */
    val backlog: List<Task> = emptyList(),
    /** Open tasks left on past days, oldest first; the same for any day shown. */
    val overdue: List<Task> = emptyList(),
) {
    val isToday: Boolean get() = day == today
    val isEmpty: Boolean get() = timed.isEmpty() && untimed.isEmpty()
}

/** Order of status groups within a section: open, then done (green), then cancelled and moved (grey). */
private fun statusRank(status: TaskStatus): Int = when (status) {
    TaskStatus.OPEN -> 0
    TaskStatus.DONE -> 1
    TaskStatus.CANCELLED, TaskStatus.MOVED -> 2
}

/** Picks the tasks of [day] out of [tasks], ordered by status group within each section, plus the backlog and overdue lists. */
fun buildDayUiState(day: LocalDate, today: LocalDate, tasks: List<Task>): DayUiState {
    val onDay = tasks.filter { it.day == day }
    val closedLast = compareBy<Task> { statusRank(it.status) }
    return DayUiState(
        day = day,
        today = today,
        timed = onDay
            .filter { it.placement is Placement.Timed }
            .sortedWith(closedLast.thenBy { it.startTime }.thenBy { it.id }),
        untimed = onDay
            .filter { it.placement is Placement.Untimed }
            .sortedWith(closedLast.thenBy { it.id }),
        backlog = tasks
            .filter { it.day == null && it.status == TaskStatus.OPEN }
            .sortedBy { it.id },
        overdue = overdueTasks(tasks, today),
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

/** The day a backlog task goes to from the backlog sheet: the day shown, but never a past one. */
fun backlogTarget(shownDay: LocalDate, today: LocalDate): LocalDate = maxOf(shownDay, today)

/** Open tasks on days before [today], for the overdue review: by day, then time (untimed first), then id. */
fun overdueTasks(tasks: List<Task>, today: LocalDate): List<Task> =
    tasks.filter { it.isOverdue(today) }
        .sortedWith(compareBy<Task>({ it.day }, { it.startTime }, { it.id }))

/** Time left until the next local midnight after [now]. */
fun untilNextMidnight(now: LocalDateTime): Duration =
    Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay())

/** The day to show once "today" changes: a screen that was on today follows it; one browsing another day stays. */
fun shownDayAfterTodayChange(shown: LocalDate, oldToday: LocalDate, newToday: LocalDate): LocalDate =
    if (shown == oldToday) newToday else shown
