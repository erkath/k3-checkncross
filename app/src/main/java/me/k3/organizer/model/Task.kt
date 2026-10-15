package me.k3.organizer.model

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

enum class TaskStatus {
    OPEN,

    /** Check: the task was done. */
    DONE,

    /** Cross: the task was dropped. */
    CANCELLED,

    /**
     * Postponed: a fresh OPEN copy lives on [Task.movedTo], or in the backlog when
     * [Task.movedTo] is null. The copy points back via [Task.movedFrom].
     */
    MOVED,
}

/** Where a task shows up, derived from [Task.day] and [Task.startTime]. */
sealed interface Placement {
    data object Backlog : Placement
    data class Untimed(val day: LocalDate) : Placement
    data class Timed(val day: LocalDate, val start: LocalTime, val duration: Duration) : Placement
}

/** Result of [Task.moveTo] / [Task.toBacklog]: the old task marked MOVED and its new copy. */
data class TaskMove(val old: Task, val new: Task)

/**
 * Immutable task. State changes return a new copy; invalid transitions throw
 * [IllegalStateException], invalid field combinations throw [IllegalArgumentException].
 *
 * [id] 0 means "not saved yet". [movedFrom] is the id of the MOVED task this one was copied from.
 */
data class Task(
    val id: Long = 0,
    val title: String,
    val note: String? = null,
    val status: TaskStatus = TaskStatus.OPEN,
    val day: LocalDate? = null,
    val startTime: LocalTime? = null,
    val duration: Duration? = null,
    val movedTo: LocalDate? = null,
    val movedFrom: Long? = null,
) {
    init {
        require(title.isNotBlank()) { "title must not be blank" }
        require(startTime == null || day != null) { "startTime requires day" }
        require((startTime == null) == (duration == null)) { "startTime and duration go together" }
        require(duration == null || (!duration.isNegative && !duration.isZero)) { "duration must be positive" }
        require(movedTo == null || status == TaskStatus.MOVED) { "movedTo is set only for MOVED" }
        require(status != TaskStatus.MOVED || day != null) { "only a dated task can be MOVED" }
        require(movedFrom == null || movedFrom > 0) { "movedFrom must be a saved task id" }
    }

    val placement: Placement
        get() = when {
            day == null -> Placement.Backlog
            startTime == null -> Placement.Untimed(day)
            else -> Placement.Timed(day, startTime, duration!!)
        }

    /** Open task left on a day before [today]: candidate for the review. */
    fun isOverdue(today: LocalDate): Boolean =
        status == TaskStatus.OPEN && day != null && day < today

    fun markDone(): Task {
        checkOpen()
        return copy(status = TaskStatus.DONE)
    }

    fun cancel(): Task {
        checkOpen()
        return copy(status = TaskStatus.CANCELLED)
    }

    /**
     * Undo a check, a cross or a move. When undoing a move, the caller must also
     * delete the copy that [moveTo] / [toBacklog] created.
     */
    fun reopen(): Task {
        check(status != TaskStatus.OPEN) { "task is already OPEN" }
        return copy(status = TaskStatus.OPEN, movedTo = null)
    }

    /**
     * Put a backlog task on a day, or change the time of a task within its own day.
     * Moving a dated task to another day goes through [moveTo] so it leaves a trace.
     */
    fun schedule(day: LocalDate, startTime: LocalTime? = null, duration: Duration? = null): Task {
        checkOpen()
        check(this.day == null || this.day == day) { "task is on ${this.day}: use moveTo()" }
        return copy(day = day, startTime = startTime, duration = duration)
    }

    /**
     * Postpone a dated task to another day. The old task stays as MOVED with a link
     * to [newDay], so repeated postponing is visible. The time is dropped unless given.
     */
    fun moveTo(newDay: LocalDate, startTime: LocalTime? = null, duration: Duration? = null): TaskMove {
        checkMovable()
        check(newDay != day) { "task is already on $newDay" }
        return TaskMove(
            old = copy(status = TaskStatus.MOVED, movedTo = newDay),
            new = copy(id = 0, day = newDay, startTime = startTime, duration = duration, movedFrom = id),
        )
    }

    /** Send a dated task back to the backlog; the old task stays as MOVED with no target day. */
    fun toBacklog(): TaskMove {
        checkMovable()
        return TaskMove(
            old = copy(status = TaskStatus.MOVED, movedTo = null),
            new = copy(id = 0, day = null, startTime = null, duration = null, movedFrom = id),
        )
    }

    private fun checkOpen() = check(status == TaskStatus.OPEN) { "task is $status, not OPEN" }

    private fun checkMovable() {
        checkOpen()
        checkNotNull(day) { "backlog task: use schedule() instead" }
        check(id != 0L) { "save the task before moving it" }
    }
}
