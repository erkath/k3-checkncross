package me.k3.organizer.ui.day

import me.k3.organizer.model.CalendarEvent
import me.k3.organizer.model.Placement
import me.k3.organizer.model.Task
import me.k3.organizer.model.TaskStatus
import java.time.LocalDate
import java.time.LocalTime

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
    /** Calendar events lasting all of [day] (or longer). */
    val allDayEvents: List<CalendarEvent> = emptyList(),
    /** The "Scheduled" section: timed tasks and timed calendar events together. */
    val scheduled: List<ScheduledItem> = emptyList(),
    /** Routines due on [day]; see [routinesOn]. */
    val routines: List<RoutineItem> = emptyList(),
    /** How "Scheduled" is sorted; see [buildDayUiState]. Shown on the section's toggle. */
    val scheduledClosedLast: Boolean = false,
) {
    val isToday: Boolean get() = day == today
    val isEmpty: Boolean
        get() = timed.isEmpty() && untimed.isEmpty() && allDayEvents.isEmpty() && scheduled.isEmpty() && routines.isEmpty()
}

/** A row of the "Scheduled" section: a task with a time, or a calendar event. */
sealed interface ScheduledItem {
    fun sortTimeOn(day: LocalDate): LocalTime

    /** What the time column shows as the start on [day]. */
    fun startLabelOn(day: LocalDate): String

    data class TaskItem(val task: Task) : ScheduledItem {
        override fun sortTimeOn(day: LocalDate): LocalTime = checkNotNull(task.startTime)
        override fun startLabelOn(day: LocalDate): String = checkNotNull(task.startTime).toString()
    }

    data class EventItem(val event: CalendarEvent) : ScheduledItem {
        override fun sortTimeOn(day: LocalDate): LocalTime = event.sortTimeOn(day)
        override fun startLabelOn(day: LocalDate): String = event.startLabelOn(day)
    }
}

/** Order of status groups within a section: open, then done (green), then cancelled and moved (grey). */
private fun statusRank(status: TaskStatus): Int = when (status) {
    TaskStatus.OPEN -> 0
    TaskStatus.DONE -> 1
    TaskStatus.CANCELLED, TaskStatus.MOVED -> 2
}

/**
 * Picks the tasks of [day] out of [tasks], ordered by status group within each section, plus the
 * backlog and overdue lists. [events] are the calendar events of [day]; timed ones join "Scheduled".
 * [routines] are already picked for [day] by [routinesOn].
 */
fun buildDayUiState(
    day: LocalDate,
    today: LocalDate,
    tasks: List<Task>,
    events: List<CalendarEvent> = emptyList(),
    routines: List<RoutineItem> = emptyList(),
    /** "Done to the bottom" for "Scheduled": closed tasks leave the timeline and go last. */
    scheduledClosedLast: Boolean = false,
): DayUiState {
    val onDay = tasks.filter { it.day == day }
    val closedLast = compareBy<Task> { statusRank(it.status) }
    val timed = onDay
        .filter { it.placement is Placement.Timed }
        .sortedWith(closedLast.thenBy { it.startTime }.thenBy { it.id })
    return DayUiState(
        day = day,
        today = today,
        timed = timed,
        untimed = onDay
            .filter { it.placement is Placement.Untimed }
            .sortedWith(closedLast.thenBy { it.id }),
        backlog = tasks
            .filter { it.day == null && it.status == TaskStatus.OPEN }
            .sortedBy { it.id },
        overdue = overdueTasks(tasks, today),
        allDayEvents = events.filter { it.allDay },
        scheduled = scheduledItems(day, timed, events.filter { !it.allDay }, scheduledClosedLast),
        scheduledClosedLast = scheduledClosedLast,
        routines = routines,
    )
}

/**
 * The "Scheduled" section. By default a timeline: timed tasks, whatever their status, and timed
 * events, all by start time. With [closedLast], closed tasks leave the timeline and go last (in
 * their usual order: done, then grey); events have no status and stay with the open work.
 * On a tie a task comes before an event, and tasks keep their order (open before closed).
 */
private fun scheduledItems(day: LocalDate, timed: List<Task>, events: List<CalendarEvent>, closedLast: Boolean): List<ScheduledItem> {
    val (live, closed) = if (closedLast) timed.partition { it.status == TaskStatus.OPEN } else timed to emptyList()
    // sortedBy is stable, so ties keep the order of this list.
    val timeline = (live.map { ScheduledItem.TaskItem(it) } + events.map { ScheduledItem.EventItem(it) }).sortedBy { it.sortTimeOn(day) }
    return timeline + closed.map { ScheduledItem.TaskItem(it) }
}

/**
 * The start shown in each row's time column, or null where it repeats the row above:
 * several things at 9:00 show "9:00" once. The blank column keeps its width, so rows line up.
 */
fun timeSlots(items: List<ScheduledItem>, day: LocalDate): List<String?> {
    var previous: String? = null
    return items.map { item ->
        val label = item.startLabelOn(day)
        if (label == previous) null else label.also { previous = it }
    }
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

/** The day to show once "today" changes: a screen that was on today follows it; one browsing another day stays. */
fun shownDayAfterTodayChange(shown: LocalDate, oldToday: LocalDate, newToday: LocalDate): LocalDate =
    if (shown == oldToday) newToday else shown
