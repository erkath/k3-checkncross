package me.k3.organizer.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.k3.organizer.data.db.OrganizerDatabase
import me.k3.organizer.data.db.toEntity
import me.k3.organizer.data.db.toTask
import me.k3.organizer.model.Task

/** [TaskRepository] stored in SQLite through Room. */
class RoomTaskRepository(private val db: OrganizerDatabase) : TaskRepository {
    private val dao = db.taskDao()

    override val tasks: Flow<List<Task>> = dao.observeAll().map { rows -> rows.map { it.toTask() } }

    override suspend fun all(): List<Task> = dao.getAll().map { it.toTask() }

    override suspend fun add(task: Task): Task {
        require(task.id == 0L) { "task is already saved with id ${task.id}" }
        return task.copy(id = dao.insert(task.toEntity()))
    }

    override suspend fun update(id: Long, transform: (Task) -> Task) = db.withTransaction {
        val current = dao.get(id)?.toTask() ?: throw IllegalArgumentException("no task with id $id")
        val new = transform(current)
        check(new.id == id)
        dao.update(new.toEntity())
        Unit
    }

    override suspend fun delete(id: Long) = db.withTransaction {
        require(dao.delete(id) == 1) { "no task with id $id" }
        dao.unlinkMovedFrom(id)
    }

    override suspend fun <R> inTransaction(block: suspend () -> R): R = db.withTransaction(block)
}
