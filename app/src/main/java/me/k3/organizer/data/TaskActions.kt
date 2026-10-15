package me.k3.organizer.data

import me.k3.organizer.model.Task
import me.k3.organizer.model.TaskMove
import me.k3.organizer.model.TaskStatus
import java.time.LocalDate

/**
 * Operations that touch more than one task. Each runs in one transaction, so a failure
 * halfway (say, after marking the old task MOVED) leaves the database unchanged.
 */
class TaskActions(private val repository: TaskRepository) {

    suspend fun find(id: Long): Task =
        repository.all().firstOrNull { it.id == id } ?: throw IllegalArgumentException("no task with id $id")

    /** Postpones a task to [newDay], keeping its time. Returns the new copy. */
    suspend fun move(id: Long, newDay: LocalDate): Task = repository.inTransaction {
        val task = find(id)
        apply(task.moveTo(newDay, task.startTime, task.duration))
    }

    /** Sends a dated task back to the backlog. Returns the new copy. */
    suspend fun toBacklog(id: Long): Task = repository.inTransaction { apply(find(id).toBacklog()) }

    /** Reverts a move: the MOVED task is open again and its copy is deleted. */
    suspend fun undoMove(id: Long) = repository.inTransaction {
        val all = repository.all()
        val task = all.firstOrNull { it.id == id } ?: throw IllegalArgumentException("no task with id $id")
        val copy = movedCopy(all, task)
        check(copy != null && copy.status == TaskStatus.OPEN) { "move of task $id cannot be undone" }
        repository.delete(copy.id)
        repository.update(id) { it.reopen() }
    }

    suspend fun delete(id: Long) = repository.delete(id)

    private suspend fun apply(move: TaskMove): Task {
        repository.update(move.old.id) { move.old }
        return repository.add(move.new)
    }

    companion object {
        private fun movedCopy(all: List<Task>, task: Task): Task? =
            if (task.status == TaskStatus.MOVED) all.firstOrNull { it.movedFrom == task.id } else null

        /** Undo is safe only while the copy is still untouched: open and not moved further. */
        fun canUndoMove(all: List<Task>, task: Task): Boolean =
            movedCopy(all, task)?.status == TaskStatus.OPEN
    }
}
