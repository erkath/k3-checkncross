package me.k3.organizer.ui.routines

import me.k3.organizer.model.Frequency
import me.k3.organizer.model.Routine
import java.time.LocalDate

/** Editable fields of the routine sheet. */
data class RoutineForm(
    val title: String = "",
    val note: String = "",
    val frequency: Frequency = Frequency.DAILY,
    /** Unchecked = paused. Only offered when editing; a new routine starts active. */
    val active: Boolean = true,
) {
    val canSave: Boolean get() = title.isNotBlank()

    /** A new routine, shown from [today] on. */
    fun toRoutine(today: LocalDate): Routine {
        check(canSave) { "title is blank" }
        return Routine(title = title.trim(), note = note.trim().ifEmpty { null }, frequency = frequency, createdOn = today, active = active)
    }

    /** Edits of a saved routine; its id and creation date stay. */
    fun applyTo(routine: Routine): Routine {
        check(canSave) { "title is blank" }
        return routine.copy(title = title.trim(), note = note.trim().ifEmpty { null }, frequency = frequency, active = active)
    }

    companion object {
        fun from(routine: Routine) = RoutineForm(routine.title, routine.note.orEmpty(), routine.frequency, routine.active)
    }
}

/** The open routine sheet: a new routine when [routine] is null, otherwise editing it. */
data class RoutineSheetState(val form: RoutineForm, val routine: Routine? = null) {
    val isNew: Boolean get() = routine == null
}

/** The Routines list: active first (daily, then weekly), paused at the end; each group by creation. */
fun routineListOrder(routines: List<Routine>): List<Routine> =
    routines.sortedWith(compareBy({ !it.active }, { it.frequency }, { it.id }))
