package me.k3.organizer.data

import kotlinx.coroutines.flow.first
import me.k3.organizer.model.Task
import me.k3.organizer.model.TaskMove
import me.k3.organizer.model.TaskStatus
import java.time.LocalDate

/**
 * Operations that touch more than one task. Built on [TaskRepository] primitives;
 * with Room these will run inside one transaction.
 */
class TaskActions(private val repository: TaskRepository) {

    suspend fun find(id: Long): Task =
        repository.tasks.first().firstOrNull { it.id == id } ?: throw IllegalArgumentException("no task with id $id")

    /** Postpones a task to [newDay], keeping its time. Returns the new copy. */
    suspend fun move(id: Long, newDay: LocalDate): Task {
        val task = find(id)
        return apply(task.moveTo(newDay, task.startTime, task.duration))
    }

    /** Sends a dated task back to the backlog. Returns the new copy. */
    suspend fun toBacklog(id: Long): Task = apply(find(id).toBacklog())

    /** Reverts a move: the MOVED task is open again and its copy is deleted. */
    suspend fun undoMove(id: Long) {
        val all = repository.tasks.first()
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
