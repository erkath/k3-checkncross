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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.k3.organizer.OrganizerApp
import me.k3.organizer.data.AppPreferences
import me.k3.organizer.data.RoutineRepository
import me.k3.organizer.data.TaskActions
import me.k3.organizer.data.TaskRepository
import me.k3.organizer.data.calendar.CalendarSource
import me.k3.organizer.model.CalendarEvent
import me.k3.organizer.model.Task
import me.k3.organizer.ui.common.TodayTracker
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.WeekFields
import java.util.Locale

class DayViewModel(
    private val repository: TaskRepository,
    private val routineRepository: RoutineRepository,
    private val calendar: CalendarSource,
    private val preferences: AppPreferences,
    /** For weekly routines: which day a week starts on (from the phone's locale). */
    private val firstDayOfWeek: DayOfWeek,
    /** Source of the current local time; replaceable in tests. */
    private val now: () -> LocalDateTime = LocalDateTime::now,
) : ViewModel() {
    private val actions = TaskActions(repository)
    /** The current date: follows midnight and is re-read whenever the app comes back. */
    private val todayTracker = TodayTracker(now, viewModelScope, ::onNewDay)
    private val today: LocalDate get() = todayTracker.value
    private val day = MutableStateFlow(today)

    /** Bumped to re-read the calendar: the app is not told when events change elsewhere. */
    private val eventsReload = MutableStateFlow(0)

    private class DayEvents(val day: LocalDate, val events: List<CalendarEvent>)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val events = combine(day, eventsReload) { day, _ -> day }
        // mapLatest: when the day changes again before the read finishes, the old read is cancelled.
        .mapLatest { day -> DayEvents(day, loadEvents(day)) }
        // Tasks must not wait for the calendar: start with "no events for any day".
        .onStart { emit(DayEvents(LocalDate.MIN, emptyList())) }

    /** A failing calendar provider must not break the day screen: log it and show no events. */
    private suspend fun loadEvents(day: LocalDate): List<CalendarEvent> =
        try {
            calendar.eventsOn(day)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Calendar read failed", e)
            emptyList()
        }

    /** Routines with their marks; one flow, since combine takes at most five. */
    private val routineData = combine(routineRepository.routines, routineRepository.marks) { routines, marks -> routines to marks }

    val uiState: StateFlow<DayUiState> =
        combine(day, todayTracker.flow, repository.tasks, events, routineData) { day, today, tasks, events, (routines, marks) ->
            buildDayUiState(
                day,
                today,
                tasks,
                // Events still loading for a newly shown day: show none rather than the previous day's.
                if (events.day == day) events.events else emptyList(),
                routinesOn(day, today, routines, marks, firstDayOfWeek),
            )
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayUiState(day = today, today = today))

    fun previousDay() = day.update { it.minusDays(1) }
    fun nextDay() = day.update { it.plusDays(1) }
    fun goToToday() = day.update { today }

    /** The day currently shown, e.g. to open the Week screen on its week. */
    val shownDay: LocalDate get() = day.value

    /** Shows [date], e.g. after picking it on the Week screen. */
    fun showDay(date: LocalDate) {
        day.value = date
    }

    fun onCheckboxTap(taskId: Long) = updateTask(taskId, ::tapCheckbox)
    fun onSwipeLeft(taskId: Long) = updateTask(taskId, ::swipeLeft)

    /** Checkbox on a routine: done for this day (or week) / not done again. */
    fun onRoutineTap(item: RoutineItem) = applyRoutineChange(routineCheckboxTap(item, day.value))

    /** Swipe left on a routine: skip it this time / un-skip. */
    fun onRoutineSwipeLeft(item: RoutineItem) = applyRoutineChange(routineSwipeLeft(item, day.value))

    private fun applyRoutineChange(change: RoutineChange?) {
        change ?: return
        launchAction {
            when (change) {
                is RoutineChange.Set -> routineRepository.setMark(change.mark)
                is RoutineChange.Clear -> routineRepository.clearMark(change.routineId, change.periodStart)
            }
        }
    }

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

    /**
     * Whether the overdue review is shown. Opens by itself on start and on a new day when there are
     * open tasks on past days; closes by itself when the last one has been handled.
     */
    var reviewOpen: Boolean by mutableStateOf(false)
        private set

    init {
        openReviewIfOverdue()
    }

    /** Re-reads the date; call when the screen is resumed (midnight itself is noticed by [todayTracker]). */
    fun refreshToday() = todayTracker.refresh()

    /** On a new day the screen follows if it was showing today, and the overdue review opens if needed. */
    private fun onNewDay(old: LocalDate, new: LocalDate) {
        day.update { shownDayAfterTodayChange(it, old, new) }
        openReviewIfOverdue()
    }

    private fun openReviewIfOverdue() {
        launchAction {
            if (repository.all().any { it.isOverdue(today) }) reviewOpen = true
        }
    }

    fun openReview() {
        reviewOpen = true
    }

    fun closeReview() {
        reviewOpen = false
    }

    fun reviewDone(taskId: Long) = reviewAction { repository.update(taskId, Task::markDone) }
    fun reviewCancel(taskId: Long) = reviewAction { repository.update(taskId, Task::cancel) }
    fun reviewToToday(taskId: Long) = reviewAction { actions.move(taskId, today) }
    fun reviewToBacklog(taskId: Long) = reviewAction { actions.toBacklog(taskId) }
    fun reviewAllToToday(taskIds: List<Long>) = reviewAction { actions.moveAll(taskIds, today) }

    private fun reviewAction(block: suspend () -> Unit) = launchAction {
        block()
        if (repository.all().none { it.isOverdue(today) }) reviewOpen = false
    }

    /** Whether to offer showing calendar events: no access yet, and the user has not said "Not now". */
    var calendarPromptVisible: Boolean by mutableStateOf(false)
        private set

    /** Whether calendar events can be read. Without it the list ends with a "Connect" row. */
    var calendarAccess: Boolean by mutableStateOf(false)
        private set

    /** Re-reads the calendar, e.g. after access was granted or when the app comes back. */
    fun reloadEvents() {
        calendarAccess = calendar.hasAccess()
        calendarPromptVisible = !calendarAccess && !preferences.calendarPromptDismissed
        eventsReload.update { it + 1 }
    }

    /** Whether Android's permission dialog has been shown before; see [calendarConnectAction]. */
    val calendarPermissionRequested: Boolean get() = preferences.calendarPermissionRequested

    fun onCalendarPermissionRequested() {
        preferences.calendarPermissionRequested = true
    }

    fun dismissCalendarPrompt() {
        preferences.calendarPromptDismissed = true
        calendarPromptVisible = false
    }

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
        sheet = current.copy(form = current.form.withTimeEnabled(enabled, taskDay, now()))
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
                DayViewModel(
                    app.taskRepository,
                    app.routineRepository,
                    app.calendarSource,
                    app.preferences,
                    WeekFields.of(Locale.getDefault()).firstDayOfWeek,
                )
            }
        }
    }
}
