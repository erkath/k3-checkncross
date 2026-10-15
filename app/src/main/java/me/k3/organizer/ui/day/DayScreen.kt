package me.k3.organizer.ui.day

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.k3.organizer.R
import me.k3.organizer.data.sampleTasks
import me.k3.organizer.model.Placement
import me.k3.organizer.model.Task
import me.k3.organizer.model.TaskStatus
import me.k3.organizer.ui.theme.LocalStatusColors
import me.k3.organizer.ui.theme.MyOrganizerTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val dayFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM")

/** Day screen bound to its ViewModel. */
@Composable
fun DayScreen(modifier: Modifier = Modifier, viewModel: DayViewModel = viewModel(factory = DayViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DayContent(
        state = state,
        today = viewModel.today,
        onPreviousDay = viewModel::previousDay,
        onNextDay = viewModel::nextDay,
        onToday = viewModel::goToToday,
        taskCallbacks = TaskCallbacks(
            onOpen = viewModel::openTask,
            onCheckboxTap = viewModel::onCheckboxTap,
            onSwipeLeft = viewModel::onSwipeLeft,
            onSwipeRight = viewModel::onSwipeRight,
        ),
        onAddTask = viewModel::startNewTask,
        onOpenBacklog = viewModel::openBacklog,
        onOpenReview = viewModel::openReview,
        modifier = modifier,
    )
    viewModel.sheet?.let { sheet ->
        TaskSheet(
            sheet = sheet,
            day = state.day,
            today = viewModel.today,
            actions = TaskSheetActions(
                onChange = viewModel::editSheetForm,
                onTimeEnabled = viewModel::setSheetTimeEnabled,
                onSave = viewModel::saveSheet,
                onMove = viewModel::moveFromSheet,
                onUndoMove = viewModel::undoMoveFromSheet,
                onDelete = viewModel::deleteFromSheet,
                onDismiss = viewModel::closeSheet,
            ),
        )
    }
    if (viewModel.reviewOpen && state.overdue.isNotEmpty()) {
        ReviewSheet(
            tasks = state.overdue,
            today = viewModel.today,
            actions = ReviewActions(
                onDone = viewModel::reviewDone,
                onCancel = viewModel::reviewCancel,
                onToday = viewModel::reviewToToday,
                onBacklog = viewModel::reviewToBacklog,
                onAllToToday = viewModel::reviewAllToToday,
                onDismiss = viewModel::closeReview,
            ),
        )
    }
    if (viewModel.backlogOpen) {
        BacklogSheet(
            tasks = state.backlog,
            target = backlogTarget(state.day, viewModel.today),
            today = viewModel.today,
            actions = BacklogActions(
                onAdd = viewModel::addToBacklog,
                onSchedule = viewModel::scheduleFromBacklog,
                onOpen = viewModel::openBacklogTask,
                onDismiss = viewModel::closeBacklog,
            ),
        )
    }
}

/** What can be done to a task in the list; each callback gets the task id. */
class TaskCallbacks(
    val onOpen: (Long) -> Unit,
    val onCheckboxTap: (Long) -> Unit,
    val onSwipeLeft: (Long) -> Unit,
    val onSwipeRight: (Long) -> Unit,
) {
    companion object {
        val None = TaskCallbacks({}, {}, {}, {})
    }
}

/** Stateless day screen: draws [state] and reports user actions. */
@Composable
fun DayContent(
    state: DayUiState,
    today: LocalDate,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit,
    taskCallbacks: TaskCallbacks,
    onAddTask: () -> Unit,
    onOpenBacklog: () -> Unit,
    onOpenReview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTask) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.day_add_task))
            }
        },
    ) { innerPadding ->
        DayList(state, today, onPreviousDay, onNextDay, onToday, onOpenBacklog, onOpenReview, taskCallbacks, Modifier.padding(innerPadding))
    }
}

@Composable
private fun DayList(
    state: DayUiState,
    today: LocalDate,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit,
    onOpenBacklog: () -> Unit,
    onOpenReview: () -> Unit,
    taskCallbacks: TaskCallbacks,
    modifier: Modifier = Modifier,
) {
    // Bottom padding keeps the last task clear of the floating "+" button.
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
        item { DayHeader(state, onPreviousDay, onNextDay, onToday, onOpenBacklog, onOpenReview) }

        // Overdue tasks are about today: other days are for planning, so they stay quiet.
        if (state.isToday && state.overdue.isNotEmpty()) {
            item(key = "overdue") { OverdueBanner(state.overdue.size, onOpenReview, Modifier.animateItem()) }
        }

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
                    today = today,
                    callbacks = taskCallbacks,
                    // Animates the move when a task sinks to the bottom after being closed.
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun DayHeader(
    state: DayUiState,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit,
    onOpenBacklog: () -> Unit,
    onOpenReview: () -> Unit,
) {
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
            if (state.overdue.isNotEmpty()) OverdueButton(count = state.overdue.size, onClick = onOpenReview)
            BacklogButton(count = state.backlog.size, onClick = onOpenBacklog)
        }
        HorizontalDivider()
    }
}

/** Opens the overdue review; shown only while open tasks are left on past days. */
@Composable
private fun OverdueButton(count: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        BadgedBox(badge = { Badge { Text(count.toString()) } }) {
            Icon(
                painterResource(R.drawable.ic_warning),
                contentDescription = stringResource(R.string.review_open_overdue),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Opens the backlog sheet; a badge shows how many tasks wait there. */
@Composable
private fun BacklogButton(count: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        BadgedBox(badge = { if (count > 0) Badge { Text(count.toString()) } }) {
            Icon(painterResource(R.drawable.ic_inbox), contentDescription = stringResource(R.string.backlog_open))
        }
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

/**
 * A task row. Tap opens the task; swipe left crosses it out (or restores a crossed one);
 * swipe right moves it to the next day. The row slides back first and only then the action
 * fires, so the slide-back and the task moving within the list do not run at the same time.
 */
@Composable
private fun TaskRow(task: Task, today: LocalDate, callbacks: TaskCallbacks, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val limits = remember(density) {
        with(density) {
            SwipeLimits(max = 120.dp.toPx(), threshold = 72.dp.toPx(), minFling = 24.dp.toPx(), flingVelocity = 1000.dp.toPx())
        }
    }
    val allowLeft = canSwipeLeft(task)
    val allowRight = canSwipeRight(task)
    var offset by remember { mutableFloatStateOf(0f) }
    val currentTask by rememberUpdatedState(task)
    val currentCallbacks by rememberUpdatedState(callbacks)
    val dragState = rememberDraggableState { delta ->
        val wasArmed = limits.armedDirection(offset)
        offset = limits.clamp(offset + delta, allowLeft, allowRight)
        val armed = limits.armedDirection(offset)
        if (armed != null && armed != wasArmed) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    }
    val leftLabel = stringResource(if (task.status == TaskStatus.CANCELLED) R.string.task_restore else R.string.task_cancel)
    val rightLabel = stringResource(R.string.task_move_next_day)

    Box(
        modifier = modifier.semantics {
            // Swipes are invisible to TalkBack, so offer the same actions in its menu.
            customActions = buildList {
                if (allowLeft) add(CustomAccessibilityAction(leftLabel) { callbacks.onSwipeLeft(task.id); true })
                if (allowRight) add(CustomAccessibilityAction(rightLabel) { callbacks.onSwipeRight(task.id); true })
            }
        },
    ) {
        if (offset != 0f) {
            SwipeBackground(
                direction = if (offset < 0f) SwipeDirection.LEFT else SwipeDirection.RIGHT,
                restore = task.status == TaskStatus.CANCELLED,
                armed = limits.armedDirection(offset) != null,
                modifier = Modifier.matchParentSize(),
            )
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(offset.roundToInt(), 0) }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    enabled = allowLeft || allowRight,
                    onDragStopped = { velocity ->
                        val fired = limits.release(offset, velocity)
                        animate(offset, 0f, animationSpec = tween(200)) { value, _ -> offset = value }
                        when (fired) {
                            SwipeDirection.LEFT -> currentCallbacks.onSwipeLeft(currentTask.id)
                            SwipeDirection.RIGHT -> currentCallbacks.onSwipeRight(currentTask.id)
                            null -> {}
                        }
                    },
                ),
        ) {
            TaskRowContent(
                task = task,
                today = today,
                onOpen = { callbacks.onOpen(task.id) },
                onCheckboxTap = { callbacks.onCheckboxTap(task.id) },
            )
        }
    }
}

/** What shows under the row while it is dragged; gets stronger once releasing would act. */
@Composable
private fun SwipeBackground(direction: SwipeDirection, restore: Boolean, armed: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    // Left (cross out) is grey like a crossed task; right (move) uses the theme's accent.
    val (idle, active, onActive) = when (direction) {
        SwipeDirection.LEFT -> Triple(colors.surfaceContainerHighest, colors.outline, colors.surface)
        SwipeDirection.RIGHT -> Triple(colors.secondaryContainer, colors.secondary, colors.onSecondary)
    }
    val idleTint = if (direction == SwipeDirection.LEFT) colors.onSurfaceVariant else colors.onSecondaryContainer
    val background by animateColorAsState(if (armed) active else idle, label = "swipeBackground")
    val tint by animateColorAsState(if (armed) onActive else idleTint, label = "swipeIcon")
    val scale by animateFloatAsState(if (armed) 1.2f else 1f, label = "swipeIconScale")
    val icon = when {
        direction == SwipeDirection.RIGHT -> R.drawable.ic_arrow_forward
        restore -> R.drawable.ic_undo
        else -> R.drawable.ic_close
    }
    Box(
        // The icon sits on the side the row uncovers.
        contentAlignment = if (direction == SwipeDirection.LEFT) Alignment.CenterEnd else Alignment.CenterStart,
        modifier = modifier.background(background).padding(horizontal = 24.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.scale(scale))
    }
}

@Composable
private fun TaskRowContent(task: Task, today: LocalDate, onOpen: () -> Unit, onCheckboxTap: () -> Unit) {
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
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .clickable(onClick = onOpen)
            .padding(start = 4.dp, end = 16.dp),
    ) {
        TaskCheckbox(task, onCheckboxTap)
        Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
                textDecoration = if (struck) TextDecoration.LineThrough else null,
            )
            taskSubtitle(task, today)?.let {
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
private fun taskSubtitle(task: Task, today: LocalDate): String? {
    val parts = buildList {
        (task.placement as? Placement.Timed)?.let { add("${it.start}–${it.start.plus(it.duration)}") }
        if (task.status == TaskStatus.MOVED) {
            add(
                task.movedTo?.let { stringResource(R.string.task_moved_to, relativeDayLabel(it, today)) }
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
            today = today,
            onPreviousDay = {}, onNextDay = {}, onToday = {}, taskCallbacks = TaskCallbacks.None, onAddTask = {}, onOpenBacklog = {}, onOpenReview = {},
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
            today = today,
            onPreviousDay = {}, onNextDay = {}, onToday = {}, taskCallbacks = TaskCallbacks.None, onAddTask = {}, onOpenBacklog = {}, onOpenReview = {},
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
            today = today,
            onPreviousDay = {}, onNextDay = {}, onToday = {}, taskCallbacks = TaskCallbacks.None, onAddTask = {}, onOpenBacklog = {}, onOpenReview = {},
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
            today = today,
            onPreviousDay = {}, onNextDay = {}, onToday = {}, taskCallbacks = TaskCallbacks.None, onAddTask = {}, onOpenBacklog = {}, onOpenReview = {},
        )
    }
}
