package me.k3.organizer.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import me.k3.organizer.model.Task

/** Source of tasks for the UI. In-memory for now; Room will implement the same interface. */
interface TaskRepository {
    /** All tasks; emits a new list on every change. */
    val tasks: Flow<List<Task>>

    /** Saves a new task (id 0) and returns it with its assigned id. */
    suspend fun add(task: Task): Task

    /** Replaces the task with [id] by [transform] applied to it. Unknown id is an error. */
    suspend fun update(id: Long, transform: (Task) -> Task)
}

class InMemoryTaskRepository(initial: List<Task> = emptyList()) : TaskRepository {
    private val state = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1

    override val tasks: StateFlow<List<Task>> = state.asStateFlow()

    override suspend fun add(task: Task): Task {
        require(task.id == 0L) { "task is already saved with id ${task.id}" }
        val saved = task.copy(id = nextId++)
        state.update { it + saved }
        return saved
    }

    override suspend fun update(id: Long, transform: (Task) -> Task) {
        state.update { list ->
            require(list.any { it.id == id }) { "no task with id $id" }
            list.map { if (it.id == id) transform(it).also { new -> check(new.id == id) } else it }
        }
    }
}
