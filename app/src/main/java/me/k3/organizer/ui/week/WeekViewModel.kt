package me.k3.organizer.ui.week

import android.util.Log
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
import me.k3.organizer.OrganizerApp
import me.k3.organizer.data.AppPreferences
import me.k3.organizer.data.EventMarkRepository
import me.k3.organizer.data.TaskRepository
import me.k3.organizer.data.calendar.CalendarSource
import me.k3.organizer.model.CalendarEvent
import me.k3.organizer.ui.common.TodayTracker
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.WeekFields
import java.util.Locale

/** The Week screen: an overview of seven days. Read-only; changes happen on the Day screen. */
class WeekViewModel(
    repository: TaskRepository,
    private val calendar: CalendarSource,
    private val preferences: AppPreferences,
    private val eventMarkRepository: EventMarkRepository,
    private val firstDayOfWeek: DayOfWeek,
    now: () -> LocalDateTime = LocalDateTime::now,
) : ViewModel() {
    private val todayTracker = TodayTracker(now, viewModelScope, ::onNewDay)
    private val weekStart = MutableStateFlow(weekStartOf(todayTracker.value, firstDayOfWeek))

    /** Bumped to re-read the calendar: the app is not told when events change elsewhere. */
    private val eventsReload = MutableStateFlow(0)

    private class WeekEvents(val weekStart: LocalDate, val byDay: Map<LocalDate, List<CalendarEvent>>)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val events = combine(weekStart, eventsReload) { start, _ -> start }
        .mapLatest { start -> WeekEvents(start, (0L..6L).associate { start.plusDays(it) to loadEvents(start.plusDays(it)) }) }
        // Tasks must not wait for the calendar.
        .onStart { emit(WeekEvents(LocalDate.MIN, emptyMap())) }

    private suspend fun loadEvents(day: LocalDate): List<CalendarEvent> =
        try {
            calendar.eventsOn(day)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Calendar read failed", e)
            emptyList()
        }

    /** Event marks and the Scheduled sort option; one flow, since combine takes at most five. */
    private val marksAndSort = combine(eventMarkRepository.marks, preferences.scheduledClosedLast) { marks, closedLast -> marks to closedLast }

    val uiState: StateFlow<WeekUiState> =
        combine(weekStart, todayTracker.flow, repository.tasks, events, marksAndSort) { start, today, tasks, events, (marks, closedLast) ->
            buildWeekUiState(start, today, tasks, if (events.weekStart == start) events.byDay else emptyMap(), marks, closedLast)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            WeekUiState(weekStart.value, todayTracker.value),
        )

    fun previousWeek() = weekStart.update { it.minusWeeks(1) }
    fun nextWeek() = weekStart.update { it.plusWeeks(1) }
    fun thisWeek() {
        weekStart.value = weekStartOf(todayTracker.value, firstDayOfWeek)
    }

    /** Shows the week containing [date], e.g. the day open on the Day screen. */
    fun showWeekOf(date: LocalDate) {
        weekStart.value = weekStartOf(date, firstDayOfWeek)
    }

    /** Call when the screen is resumed: the date or the calendar may have changed meanwhile. */
    fun onResume() {
        todayTracker.refresh()
        eventsReload.update { it + 1 }
    }

    private fun onNewDay(old: LocalDate, new: LocalDate) {
        weekStart.update { shownWeekAfterTodayChange(it, old, new, firstDayOfWeek) }
    }

    companion object {
        private const val TAG = "WeekViewModel"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as OrganizerApp
                WeekViewModel(
                    app.taskRepository,
                    app.calendarSource,
                    app.preferences,
                    app.eventMarkRepository,
                    WeekFields.of(Locale.getDefault()).firstDayOfWeek,
                )
            }
        }
    }
}
