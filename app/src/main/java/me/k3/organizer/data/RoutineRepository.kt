package me.k3.organizer.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import me.k3.organizer.data.db.OrganizerDatabase
import me.k3.organizer.data.db.toEntity
import me.k3.organizer.data.db.toMark
import me.k3.organizer.data.db.toRoutine
import me.k3.organizer.model.Routine
import me.k3.organizer.model.RoutineMark
import java.time.LocalDate

/** Routines and their marks: [RoomRoutineRepository] in the app, [InMemoryRoutineRepository] in tests. */
interface RoutineRepository {
    /** All routines, paused ones included, by id. */
    val routines: Flow<List<Routine>>

    /** All marks of all routines. */
    val marks: Flow<List<RoutineMark>>

    /** Saves a new routine (id 0) and returns it with its assigned id. */
    suspend fun add(routine: Routine): Routine

    /** Replaces a saved routine. Unknown id is an error. */
    suspend fun update(routine: Routine)

    /** Removes the routine and all its marks. Unknown id is an error. */
    suspend fun delete(id: Long)

    /** Sets the mark of its routine and period, replacing any earlier one. */
    suspend fun setMark(mark: RoutineMark)

    /** Removes the mark of [routineId] for the period starting [periodStart]: "not done" again. */
    suspend fun clearMark(routineId: Long, periodStart: LocalDate)
}

class RoomRoutineRepository(private val db: OrganizerDatabase) : RoutineRepository {
    private val dao = db.routineDao()

    override val routines: Flow<List<Routine>> = dao.observeRoutines().map { rows -> rows.map { it.toRoutine() } }
    override val marks: Flow<List<RoutineMark>> = dao.observeMarks().map { rows -> rows.map { it.toMark() } }

    override suspend fun add(routine: Routine): Routine {
        require(routine.id == 0L) { "routine is already saved with id ${routine.id}" }
        return routine.copy(id = dao.insert(routine.toEntity()))
    }

    override suspend fun update(routine: Routine) {
        require(dao.update(routine.toEntity()) == 1) { "no routine with id ${routine.id}" }
    }

    override suspend fun delete(id: Long) = db.withTransaction {
        require(dao.delete(id) == 1) { "no routine with id $id" }
        dao.deleteMarks(id)
    }

    override suspend fun setMark(mark: RoutineMark) = dao.putMark(mark.toEntity())

    override suspend fun clearMark(routineId: Long, periodStart: LocalDate) =
        dao.clearMark(routineId, periodStart.toEpochDay())
}

class InMemoryRoutineRepository(
    initialRoutines: List<Routine> = emptyList(),
    initialMarks: List<RoutineMark> = emptyList(),
) : RoutineRepository {
    private val routineState = MutableStateFlow(initialRoutines)
    private val markState = MutableStateFlow(initialMarks)
    private var nextId = (initialRoutines.maxOfOrNull { it.id } ?: 0) + 1

    override val routines: StateFlow<List<Routine>> = routineState.asStateFlow()
    override val marks: StateFlow<List<RoutineMark>> = markState.asStateFlow()

    override suspend fun add(routine: Routine): Routine {
        require(routine.id == 0L) { "routine is already saved with id ${routine.id}" }
        val saved = routine.copy(id = nextId++)
        routineState.update { it + saved }
        return saved
    }

    override suspend fun update(routine: Routine) {
        routineState.update { list ->
            require(list.any { it.id == routine.id }) { "no routine with id ${routine.id}" }
            list.map { if (it.id == routine.id) routine else it }
        }
    }

    override suspend fun delete(id: Long) {
        routineState.update { list ->
            require(list.any { it.id == id }) { "no routine with id $id" }
            list.filter { it.id != id }
        }
        markState.update { marks -> marks.filter { it.routineId != id } }
    }

    override suspend fun setMark(mark: RoutineMark) {
        markState.update { marks -> marks.filterNot { it.samePeriod(mark.routineId, mark.periodStart) } + mark }
    }

    override suspend fun clearMark(routineId: Long, periodStart: LocalDate) {
        markState.update { marks -> marks.filterNot { it.samePeriod(routineId, periodStart) } }
    }

    private fun RoutineMark.samePeriod(routineId: Long, periodStart: LocalDate) =
        this.routineId == routineId && this.periodStart == periodStart
}
