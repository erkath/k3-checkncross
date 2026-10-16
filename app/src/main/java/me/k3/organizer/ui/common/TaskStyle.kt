package me.k3.organizer.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import me.k3.organizer.model.TaskStatus
import me.k3.organizer.ui.theme.LocalStatusColors

/**
 * Title color of a task, the same on every screen: DONE green, CANCELLED and MOVED grey.
 * MOVED is grey but not struck through: the task lives on elsewhere.
 */
@Composable
fun taskTitleColor(status: TaskStatus): Color = when (status) {
    TaskStatus.OPEN -> MaterialTheme.colorScheme.onSurface
    TaskStatus.DONE -> LocalStatusColors.current.done
    TaskStatus.CANCELLED, TaskStatus.MOVED -> MaterialTheme.colorScheme.outline
}

/** Done and cancelled tasks are struck through. */
fun isStruckThrough(status: TaskStatus): Boolean = status == TaskStatus.DONE || status == TaskStatus.CANCELLED
