package me.k3.organizer.ui.day

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.k3.organizer.OrganizerApp
import me.k3.organizer.data.TaskActions
import me.k3.organizer.data.TaskRepository
import me.k3.organizer.model.Task
import java.time.LocalDate
import java.time.LocalDateTime

class DayViewModel(
    private val repository: TaskRepository,
    val today: LocalDate,
) : ViewModel() {
    private val actions = TaskActions(repository)
    private val day = MutableStateFlow(today)

    val uiState: StateFlow<DayUiState> =
        combine(day, repository.tasks) { day, tasks -> buildDayUiState(day, today, tasks) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayUiState(today, isToday = true))

    fun previousDay() = day.update { it.minusDays(1) }
    fun nextDay() = day.update { it.plusDays(1) }
    fun goToToday() = day.update { today }

    fun onCheckboxTap(taskId: Long) = updateTask(taskId, ::tapCheckbox)
    fun onSwipeLeft(taskId: Long) = updateTask(taskId, ::swipeLeft)

    fun onSwipeRight(taskId: Long) {
        launchAction {
            val task = actions.find(taskId)
            actions.move(taskId, nextDayTarget(task, today))
        }
    }

    /**
     * The open task sheet, or null when it is closed. Compose state rather than a
     * StateFlow: text fields must see their own edits immediately, or typing can lose characters.
     */
    var sheet: TaskSheetState? by mutableStateOf(null)
        private set

    /** Whether the backlog sheet is shown. */
    var backlogOpen: Boolean by mutableStateOf(false)
        private set

    fun openBacklog() {
        backlogOpen = true
    }

    fun closeBacklog() {
        backlogOpen = false
    }

    /** Quick add from the backlog sheet: a task with just a title and no day. Blank titles are ignored. */
    fun addToBacklog(title: String) {
        val trimmed = title.trim().ifEmpty { return }
        launchAction { repository.add(Task(title = trimmed)) }
    }

    /** Plans a backlog task for the day shown (or today, if a past day is shown). The sheet stays open. */
    fun scheduleFromBacklog(taskId: Long) {
        val target = backlogTarget(day.value, today)
        launchAction { actions.schedule(taskId, target) }
    }

    /** Opens a backlog task in the task sheet; the two sheets are not stacked. */
    fun openBacklogTask(taskId: Long) {
        backlogOpen = false
        openTask(taskId)
    }

    fun startNewTask() {
        sheet = TaskSheetState(TaskForm())
    }

    fun openTask(taskId: Long) {
        launchAction {
            val all = repository.all()
            val task = all.firstOrNull { it.id == taskId } ?: return@launchAction
            sheet = TaskSheetState(TaskForm.from(task), task, TaskActions.canUndoMove(all, task))
        }
    }

    fun editSheetForm(form: TaskForm) {
        sheet = sheet?.copy(form = form)
    }

    fun setSheetTimeEnabled(enabled: Boolean) {
        val current = sheet?.takeIf { it.canEditTime } ?: return
        val taskDay = current.task?.day ?: day.value
        sheet = current.copy(form = current.form.withTimeEnabled(enabled, taskDay, LocalDateTime.now()))
    }

    fun closeSheet() {
        sheet = null
    }

    /** Adds the new task to the day being shown, or saves edits. Ignored while the title is blank. */
    fun saveSheet() {
        val current = sheet?.takeIf { it.form.canSave } ?: return
        val shownDay = day.value
        sheet = null
        launchAction {
            val task = current.task
            if (task == null) repository.add(current.form.toTask(shownDay)) else repository.update(task.id, current.form::applyTo)
        }
    }

    /** Saves edits, then moves the task to [target], or to the backlog when [target] is null. */
    fun moveFromSheet(target: LocalDate?) {
        val current = sheet?.takeIf { it.canMove && it.form.canSave } ?: return
        val id = current.task!!.id
        sheet = null
        launchAction {
            repository.update(id, current.form::applyTo)
            if (target == null) actions.toBacklog(id) else actions.move(id, target)
        }
    }

    fun undoMoveFromSheet() {
        val current = sheet?.takeIf { it.canUndoMove } ?: return
        sheet = null
        launchAction { actions.undoMove(current.task!!.id) }
    }

    fun deleteFromSheet() {
        val id = sheet?.task?.id ?: return
        sheet = null
        launchAction { actions.delete(id) }
    }

    private fun updateTask(taskId: Long, transform: (Task) -> Task) {
        launchAction { repository.update(taskId, transform) }
    }

    /**
     * Runs a repository action. One based on a stale screen (a double swipe, a task changed
     * meanwhile) fails its checks; it is dropped and logged instead of crashing the app.
     */
    private fun launchAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e // a subclass of IllegalStateException; must reach the coroutine machinery
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Dropped stale action", e)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Dropped stale action", e)
            }
        }
    }

    companion object {
        private const val TAG = "DayViewModel"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as OrganizerApp
                DayViewModel(app.taskRepository, LocalDate.now())
            }
        }
    }
}
