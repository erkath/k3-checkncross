package me.k3.organizer.model

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * A reminder is either tied to a task or recurring on its own.
 * Times are local wall-clock times; conversion to an instant happens when the alarm is set.
 *
 * [id] 0 means "not saved yet".
 */
sealed interface Reminder {
    val id: Long
    val enabled: Boolean
}

/** Fires [offset] before the start of a timed task, e.g. 15 minutes before. */
data class TaskReminder(
    override val id: Long = 0,
    val taskId: Long,
    val offset: Duration,
    override val enabled: Boolean = true,
) : Reminder {
    init {
        require(taskId > 0) { "taskId must be a saved task id" }
        require(!offset.isNegative) { "offset must not be negative" }
    }

    /** When it fires for [task], or null if it should not fire at all. */
    fun fireTime(task: Task): LocalDateTime? {
        require(task.id == taskId) { "reminder belongs to task $taskId, not ${task.id}" }
        if (!enabled || task.status != TaskStatus.OPEN) return null
        val placement = task.placement as? Placement.Timed ?: return null
        return placement.day.atTime(placement.start).minus(offset)
    }

    /** Fire time for [task] if it is still ahead of [now], otherwise null. */
    fun nextFireAfter(now: LocalDateTime, task: Task): LocalDateTime? =
        fireTime(task)?.takeIf { it > now }
}

/** Fires at [time] on each of [days], e.g. "every weekday at 9:00 — plan the day". */
data class RecurringReminder(
    override val id: Long = 0,
    val message: String,
    val time: LocalTime,
    val days: Set<DayOfWeek>,
    override val enabled: Boolean = true,
) : Reminder {
    init {
        require(message.isNotBlank()) { "message must not be blank" }
        require(days.isNotEmpty()) { "days must not be empty" }
    }

    /** The first occurrence strictly after [now], or null if disabled. */
    fun nextFireAfter(now: LocalDateTime): LocalDateTime? {
        if (!enabled) return null
        // Today counts only if the time is still ahead; a week later the same weekday matches again.
        return (0L..7L)
            .map { now.toLocalDate().plusDays(it).atTime(time) }
            .first { it > now && it.dayOfWeek in days }
    }

    companion object {
        val WEEKDAYS: Set<DayOfWeek> = DayOfWeek.entries.toSet() - DayOfWeek.SATURDAY - DayOfWeek.SUNDAY
        val EVERY_DAY: Set<DayOfWeek> = DayOfWeek.entries.toSet()
    }
}
