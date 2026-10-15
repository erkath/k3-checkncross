package me.k3.organizer.ui.day

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import me.k3.organizer.R
import me.k3.organizer.data.sampleTasks
import me.k3.organizer.model.Placement
import me.k3.organizer.model.Task
import me.k3.organizer.model.TaskStatus
import me.k3.organizer.ui.theme.LocalStatusColors
import me.k3.organizer.ui.theme.MyOrganizerTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dayFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM")
private val shortDayFormat = DateTimeFormatter.ofPattern("EEE, d MMM")

/** Day screen bound to its ViewModel. */
@Composable
fun DayScreen(modifier: Modifier = Modifier, viewModel: DayViewModel = viewModel(factory = DayViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DayContent(
        state = state,
        onPreviousDay = viewModel::previousDay,
        onNextDay = viewModel::nextDay,
        onToday = viewModel::goToToday,
        onCheckboxTap = viewModel::onCheckboxTap,
        onSwipeLeft = viewModel::onSwipeLeft,
        modifier = modifier,
    )
}

/** Stateless day screen: draws [state] and reports user actions. */
@Composable
fun DayContent(
    state: DayUiState,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit,
    onCheckboxTap: (Long) -> Unit,
    onSwipeLeft: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        item { DayHeader(state, onPreviousDay, onNextDay, onToday) }

        if (state.isEmpty) {
            item {
                Text(
                    text = stringResource(R.string.day_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        for ((title, tasks) in listOf(R.string.day_section_timed to state.timed, R.string.day_section_untimed to state.untimed)) {
            if (tasks.isEmpty()) continue
            item(key = title) { SectionTitle(stringResource(title), Modifier.animateItem()) }
            items(tasks, key = { it.id }) { task ->
                TaskRow(
                    task = task,
                    onCheckboxTap = { onCheckboxTap(task.id) },
                    onSwipeLeft = { onSwipeLeft(task.id) },
                    // Animates the move when a task sinks to the bottom after being closed.
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun DayHeader(state: DayUiState, onPreviousDay: () -> Unit, onNextDay: () -> Unit, onToday: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onPreviousDay) {
                Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = stringResource(R.string.day_previous))
            }
            Text(
                text = state.day.format(dayFormat),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (!state.isToday) {
                TextButton(onClick = onToday) { Text(stringResource(R.string.day_today)) }
            }
            IconButton(onClick = onNextDay) {
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = stringResource(R.string.day_next))
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** A task row; swiping it left crosses the task out (or restores a crossed one). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskRow(task: Task, onCheckboxTap: () -> Unit, onSwipeLeft: () -> Unit, modifier: Modifier = Modifier) {
    val swipeState = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    val swipeLabel = stringResource(if (task.status == TaskStatus.CANCELLED) R.string.task_restore else R.string.task_cancel)

    SwipeToDismissBox(
        state = swipeState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = canSwipeLeft(task),
        // The row is not removed: apply the action, then slide the row back in place.
        onDismiss = {
            onSwipeLeft()
            scope.launch { swipeState.reset() }
        },
        backgroundContent = { SwipeBackground(restore = task.status == TaskStatus.CANCELLED) },
        modifier = modifier.semantics {
            // Swipes are invisible to TalkBack, so offer the same action in its menu.
            if (canSwipeLeft(task)) {
                customActions = listOf(CustomAccessibilityAction(swipeLabel) { onSwipeLeft(); true })
            }
        },
    ) {
        TaskRowContent(task, onCheckboxTap)
    }
}

@Composable
private fun SwipeBackground(restore: Boolean) {
    Box(
        contentAlignment = Alignment.CenterEnd,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 24.dp),
    ) {
        Icon(
            painterResource(if (restore) R.drawable.ic_undo else R.drawable.ic_close),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TaskRowContent(task: Task, onCheckboxTap: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    // DONE: green, CANCELLED: grey; both struck through. MOVED: grey, not struck (it lives on elsewhere).
    val targetColor = when (task.status) {
        TaskStatus.OPEN -> colors.onSurface
        TaskStatus.DONE -> LocalStatusColors.current.done
        TaskStatus.CANCELLED, TaskStatus.MOVED -> colors.outline
    }
    val textColor by animateColorAsState(targetColor, label = "taskTextColor")
    val struck = task.status == TaskStatus.DONE || task.status == TaskStatus.CANCELLED

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        // Opaque, so the swipe background shows only where the row has slid away.
        modifier = Modifier.fillMaxWidth().background(colors.surface).padding(start = 4.dp, end = 16.dp),
    ) {
        TaskCheckbox(task, onCheckboxTap)
        Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
                textDecoration = if (struck) TextDecoration.LineThrough else null,
            )
            taskSubtitle(task)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (task.status == TaskStatus.OPEN) colors.onSurfaceVariant else textColor,
                )
            }
        }
    }
}

/** Square box: empty when open, green ✓ when done, grey ✕ when cancelled, → when moved. */
@Composable
private fun TaskCheckbox(task: Task, onTap: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val done = LocalStatusColors.current.done
    val fill by animateColorAsState(
        when (task.status) {
            TaskStatus.DONE -> done
            TaskStatus.CANCELLED -> colors.outline
            TaskStatus.OPEN, TaskStatus.MOVED -> Color.Transparent
        },
        label = "checkboxFill",
    )
    val border = when (task.status) {
        TaskStatus.OPEN -> colors.onSurfaceVariant
        TaskStatus.DONE -> done
        TaskStatus.CANCELLED -> colors.outline
        TaskStatus.MOVED -> Color.Transparent
    }
    val icon = when (task.status) {
        TaskStatus.OPEN -> null
        TaskStatus.DONE -> R.drawable.ic_check
        TaskStatus.CANCELLED -> R.drawable.ic_close
        TaskStatus.MOVED -> R.drawable.ic_arrow_forward
    }
    val state = stringResource(
        when (task.status) {
            TaskStatus.OPEN -> R.string.task_state_open
            TaskStatus.DONE -> R.string.task_state_done
            TaskStatus.CANCELLED -> R.string.task_state_cancelled
            TaskStatus.MOVED -> R.string.task_state_moved
        },
    )
    val action = stringResource(
        when (task.status) {
            TaskStatus.DONE -> R.string.task_mark_not_done
            TaskStatus.CANCELLED -> R.string.task_restore
            else -> R.string.task_mark_done
        },
    )
    val shape = RoundedCornerShape(4.dp)

    Box(
        contentAlignment = Alignment.Center,
        // 48dp is the minimum touch target; the visible box is smaller.
        modifier = Modifier
            .size(48.dp)
            .clickable(enabled = canTapCheckbox(task), role = Role.Checkbox, onClickLabel = action, onClick = onTap)
            .semantics { stateDescription = state },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(22.dp).clip(shape).background(fill).border(2.dp, border, shape),
        ) {
            icon?.let {
                Icon(
                    painterResource(it),
                    contentDescription = null,
                    tint = if (task.status == TaskStatus.MOVED) colors.outline else colors.surface,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Time range and/or where a MOVED task went; null when there is nothing to add. */
@Composable
private fun taskSubtitle(task: Task): String? {
    val parts = buildList {
        (task.placement as? Placement.Timed)?.let { add("${it.start}–${it.start.plus(it.duration)}") }
        if (task.status == TaskStatus.MOVED) {
            add(
                task.movedTo?.let { stringResource(R.string.task_moved_to, it.format(shortDayFormat)) }
                    ?: stringResource(R.string.task_moved_to_backlog),
            )
        }
        task.note?.let { add(it) }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Preview(showBackground = true)
@Composable
private fun DayContentPreview() {
    val today = LocalDate.of(2026, 10, 15)
    MyOrganizerTheme {
        DayContent(
            state = buildDayUiState(today, today, sampleTasks(today)),
            onPreviousDay = {}, onNextDay = {}, onToday = {}, onCheckboxTap = {}, onSwipeLeft = {},
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DayContentDarkPreview() {
    val today = LocalDate.of(2026, 10, 15)
    MyOrganizerTheme {
        DayContent(
            state = buildDayUiState(today, today, sampleTasks(today)),
            onPreviousDay = {}, onNextDay = {}, onToday = {}, onCheckboxTap = {}, onSwipeLeft = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DayContentYesterdayPreview() {
    val today = LocalDate.of(2026, 10, 15)
    MyOrganizerTheme {
        DayContent(
            state = buildDayUiState(today.minusDays(1), today, sampleTasks(today)),
            onPreviousDay = {}, onNextDay = {}, onToday = {}, onCheckboxTap = {}, onSwipeLeft = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DayContentEmptyPreview() {
    val today = LocalDate.of(2026, 10, 15)
    MyOrganizerTheme {
        DayContent(
            state = buildDayUiState(today.plusDays(1), today, emptyList()),
            onPreviousDay = {}, onNextDay = {}, onToday = {}, onCheckboxTap = {}, onSwipeLeft = {},
        )
    }
}
