package me.k3.organizer.ui.routines

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.k3.organizer.OrganizerApp
import me.k3.organizer.data.RoutineRepository
import me.k3.organizer.model.Routine
import me.k3.organizer.ui.common.TodayTracker
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.WeekFields
import java.util.Locale

/** The Routines tab: the list of routines and the sheet to add or edit one. */
class RoutinesViewModel(
    private val repository: RoutineRepository,
    private val firstDayOfWeek: DayOfWeek,
    private val now: () -> LocalDateTime = LocalDateTime::now,
) : ViewModel() {
    /** Stats depend on the date; it follows midnight and is re-read on resume. */
    private val todayTracker = TodayTracker(now, viewModelScope)

    /** Every routine with its stats, in list order. */
    val items: StateFlow<List<RoutineListItem>> =
        combine(repository.routines, repository.marks, todayTracker.flow) { routines, marks, today ->
            routineListOrder(routines).map { RoutineListItem(it, routineStats(it, marks, today, firstDayOfWeek)) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Call when the screen is resumed: the date may have changed meanwhile. */
    fun onResume() = todayTracker.refresh()

    /** The open sheet, or null. Compose state so the text fields never lose typed characters. */
    var sheet: RoutineSheetState? by mutableStateOf(null)
        private set

    fun startNew() {
        sheet = RoutineSheetState(RoutineForm())
    }

    fun open(routine: Routine) {
        sheet = RoutineSheetState(RoutineForm.from(routine), routine)
    }

    fun edit(form: RoutineForm) {
        sheet = sheet?.copy(form = form)
    }

    fun close() {
        sheet = null
    }

    fun save() {
        val current = sheet?.takeIf { it.form.canSave } ?: return
        val today = now().toLocalDate()
        sheet = null
        launchAction {
            val routine = current.routine
            if (routine == null) repository.add(current.form.toRoutine(today)) else repository.update(current.form.applyTo(routine))
        }
    }

    fun delete() {
        val id = sheet?.routine?.id ?: return
        sheet = null
        launchAction { repository.delete(id) }
    }

    /** As in DayViewModel: an action on stale data is logged and dropped instead of crashing. */
    private fun launchAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Dropped stale action", e)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Dropped stale action", e)
            }
        }
    }

    companion object {
        private const val TAG = "RoutinesViewModel"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as OrganizerApp
                RoutinesViewModel(app.routineRepository, WeekFields.of(Locale.getDefault()).firstDayOfWeek)
            }
        }
    }
}
