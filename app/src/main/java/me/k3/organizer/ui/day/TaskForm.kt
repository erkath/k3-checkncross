package me.k3.organizer.ui.day

import me.k3.organizer.model.Task
import me.k3.organizer.model.TaskStatus
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Editable fields of the task sheet, for a new task or an existing one. */
data class TaskForm(
    val title: String = "",
    val note: String = "",
    /** null: the task goes into the untimed list of the day. */
    val startTime: LocalTime? = null,
    /** Used only when [startTime] is set; kept when time is switched off and on again. */
    val duration: Duration = DEFAULT_DURATION,
) {
    val canSave: Boolean get() = title.isNotBlank()

    /** Switches the time on (with a sensible default start) or off. */
    fun withTimeEnabled(enabled: Boolean, day: LocalDate, now: LocalDateTime): TaskForm =
        copy(startTime = if (enabled) startTime ?: defaultStartTime(day, now) else null)

    fun toTask(day: LocalDate): Task {
        check(canSave) { "title is blank" }
        return Task(
            title = title.trim(),
            note = note.trim().ifEmpty { null },
            day = day,
            startTime = startTime,
            duration = startTime?.let { duration },
        )
    }

    /**
     * Applies the edits to an existing [task]. Title and note can change in any status;
     * the time only for an OPEN task on a day (closed tasks keep their time).
     */
    fun applyTo(task: Task): Task {
        check(canSave) { "title is blank" }
        val edited = task.copy(title = title.trim(), note = note.trim().ifEmpty { null })
        val day = task.day
        return if (task.status == TaskStatus.OPEN && day != null) {
            edited.schedule(day, startTime, startTime?.let { duration })
        } else {
            edited
        }
    }

    companion object {
        /** The form pre-filled with [task]'s fields. */
        fun from(task: Task) = TaskForm(
            title = task.title,
            note = task.note.orEmpty(),
            startTime = task.startTime,
            duration = task.duration ?: DEFAULT_DURATION,
        )

        val DEFAULT_DURATION: Duration = Duration.ofHours(1)
        val DURATION_CHOICES: List<Duration> = listOf(15L, 30L, 60L, 90L, 120L).map { Duration.ofMinutes(it) }

        /** Today: the next full hour (at most 23:00). Any other day: 9:00. */
        fun defaultStartTime(day: LocalDate, now: LocalDateTime): LocalTime {
            if (day != now.toLocalDate()) return LocalTime.of(9, 0)
            if (now.hour >= 23) return LocalTime.of(23, 0)
            return now.toLocalTime().truncatedTo(ChronoUnit.HOURS).plusHours(1)
        }
    }
}

/** The open task sheet: a new task when [task] is null, otherwise editing [task]. */
data class TaskSheetState(
    val form: TaskForm,
    val task: Task? = null,
    /** For a MOVED task: whether its move can still be reverted. */
    val canUndoMove: Boolean = false,
) {
    val isNew: Boolean get() = task == null
    /** Time belongs to a day: not for closed tasks, and not for backlog ones. */
    val canEditTime: Boolean get() = task == null || (task.status == TaskStatus.OPEN && task.day != null)
    val canMove: Boolean get() = task != null && canSwipeRight(task)
}
