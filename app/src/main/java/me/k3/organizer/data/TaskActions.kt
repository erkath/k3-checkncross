package me.k3.organizer.data

import me.k3.organizer.model.Task
import me.k3.organizer.model.TaskStatus
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/**
 * Operations that touch more than one task. Each runs in one transaction, so a failure
 * halfway (say, after marking the old task MOVED) leaves the database unchanged.
 *
 * Moves form a chain: each MOVED task is followed by the copy whose [Task.movedFrom] points
 * at it. The last task of a chain (its head) is the one that is still live.
 */
class TaskActions(private val repository: TaskRepository) {

    suspend fun find(id: Long): Task = find(repository.all(), id)

    /**
     * Postpones a task to [newDay], keeping its time. If the task has been on [newDay] before,
     * it returns there instead of creating a duplicate: see [revertTo]. Returns the live task.
     */
    suspend fun move(id: Long, newDay: LocalDate): Task = repository.inTransaction {
        val all = repository.all()
        val task = find(all, id)
        check(task.status == TaskStatus.OPEN) { "task $id is ${task.status}, not OPEN" }
        val earlier = chainBefore(all, task).lastOrNull { it.day == newDay }
        if (earlier != null) {
            revertTo(all, earlier, task.startTime, task.duration)
        } else {
            val move = task.moveTo(newDay, task.startTime, task.duration)
            repository.update(id) { move.old }
            repository.add(move.new)
        }
    }

    /** Moves several tasks to [newDay] at once (all or nothing), as [move] does for one. */
    suspend fun moveAll(ids: List<Long>, newDay: LocalDate) = repository.inTransaction {
        ids.forEach { move(it, newDay) }
    }

    /**
     * Plans a backlog task for [day], without a time. Leaves no trace, unless the task came
     * to the backlog from [day] earlier: then that entry is live again instead of a duplicate.
     * Returns the live task.
     */
    suspend fun schedule(id: Long, day: LocalDate): Task = repository.inTransaction {
        val all = repository.all()
        val task = find(all, id)
        check(task.day == null) { "task $id is already on ${task.day}" }
        check(task.status == TaskStatus.OPEN) { "task $id is ${task.status}, not OPEN" }
        val earlier = chainBefore(all, task).lastOrNull { it.day == day }
        if (earlier != null) {
            revertTo(all, earlier, earlier.startTime, earlier.duration)
        } else {
            repository.update(id) { it.schedule(day) }
            find(id)
        }
    }

    /** Sends a dated task back to the backlog. Returns the new copy. */
    suspend fun toBacklog(id: Long): Task = repository.inTransaction {
        val move = find(id).toBacklog()
        repository.update(id) { move.old }
        repository.add(move.new)
    }

    /** Reverts all moves made after the MOVED task [id]: it is open again, with its own time. */
    suspend fun undoMove(id: Long): Task = repository.inTransaction {
        val all = repository.all()
        val task = find(all, id)
        check(canUndoMove(all, task)) { "move of task $id cannot be undone" }
        revertTo(all, task, task.startTime, task.duration)
    }

    suspend fun delete(id: Long) = repository.delete(id)

    /**
     * Makes [target] (a MOVED task earlier in a chain) live again: deletes every later task
     * of the chain and reopens [target] with the head's title and note and the given time.
     * The head must still be OPEN, so no check, cross or further move is lost.
     */
    private suspend fun revertTo(all: List<Task>, target: Task, startTime: LocalTime?, duration: Duration?): Task {
        val later = chainAfter(all, target)
        val head = later.last()
        check(head.status == TaskStatus.OPEN) { "chain head ${head.id} is ${head.status}" }
        later.forEach { repository.delete(it.id) }
        repository.update(target.id) {
            it.reopen()
                .copy(title = head.title, note = head.note)
                .schedule(checkNotNull(it.day), startTime, duration)
        }
        return find(target.id)
    }

    companion object {
        private fun find(all: List<Task>, id: Long): Task =
            all.firstOrNull { it.id == id } ?: throw IllegalArgumentException("no task with id $id")

        /** Tasks this one was moved from, oldest first (not including [task]). */
        fun chainBefore(all: List<Task>, task: Task): List<Task> {
            val byId = all.associateBy { it.id }
            val result = ArrayDeque<Task>()
            var current = task.movedFrom?.let(byId::get)
            while (current != null && result.size < all.size) {
                result.addFirst(current)
                current = current.movedFrom?.let(byId::get)
            }
            return result
        }

        /** Copies made from this task onwards, in order (not including [task]); the last one is the head. */
        fun chainAfter(all: List<Task>, task: Task): List<Task> {
            val next = all.filter { it.movedFrom != null }.associateBy { it.movedFrom }
            val result = mutableListOf<Task>()
            var current = next[task.id]
            while (current != null && result.size < all.size) {
                result += current
                current = next[current.id]
            }
            return result
        }

        /** Undo is safe while the chain's head is still open: no check, cross or edit of status would be lost. */
        fun canUndoMove(all: List<Task>, task: Task): Boolean =
            task.status == TaskStatus.MOVED && chainAfter(all, task).lastOrNull()?.status == TaskStatus.OPEN
    }
}
