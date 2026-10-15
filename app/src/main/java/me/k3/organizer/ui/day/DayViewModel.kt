package me.k3.organizer.ui.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.k3.organizer.OrganizerApp
import me.k3.organizer.data.TaskRepository
import me.k3.organizer.model.Task
import java.time.LocalDate

class DayViewModel(
    private val repository: TaskRepository,
    private val today: LocalDate,
) : ViewModel() {
    private val day = MutableStateFlow(today)

    val uiState: StateFlow<DayUiState> =
        combine(day, repository.tasks) { day, tasks -> buildDayUiState(day, today, tasks) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayUiState(today, isToday = true))

    fun previousDay() = day.update { it.minusDays(1) }
    fun nextDay() = day.update { it.plusDays(1) }
    fun goToToday() = day.update { today }

    fun onCheckboxTap(taskId: Long) = updateTask(taskId, ::tapCheckbox)
    fun onSwipeLeft(taskId: Long) = updateTask(taskId, ::swipeLeft)

    private fun updateTask(taskId: Long, transform: (Task) -> Task) {
        viewModelScope.launch { repository.update(taskId, transform) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as OrganizerApp
                DayViewModel(app.taskRepository, LocalDate.now())
            }
        }
    }
}
