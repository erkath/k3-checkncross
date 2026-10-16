package me.k3.organizer.ui.day

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
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
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.k3.organizer.R
import me.k3.organizer.data.sampleRoutineMarks
import me.k3.organizer.data.sampleRoutines
import me.k3.organizer.data.sampleTasks
import me.k3.organizer.model.CalendarEvent
import me.k3.organizer.model.Frequency
import me.k3.organizer.model.Placement
import me.k3.organizer.model.Task
import me.k3.organizer.model.TaskStatus
import me.k3.organizer.ui.common.isStruckThrough
import me.k3.organizer.ui.common.taskTitleColor
import me.k3.organizer.ui.theme.LocalStatusColors
import me.k3.organizer.ui.theme.MyOrganizerTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val dayFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM")

/** Day screen bound to its ViewModel. */
@Composable
fun DayScreen(modifier: Modifier = Modifier, viewModel: DayViewModel = viewModel(factory = DayViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Asks Android for calendar access; whatever the answer, re-read (the prompt hides once granted).
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.reloadEvents()
    }
    // "Allow" on the banner and "Connect" at the bottom: ask, or go to Settings once Android stopped asking.
    val connectCalendar: () -> Unit = {
        val activity = context.findActivity()
        val action = calendarConnectAction(
            granted = viewModel.calendarAccess,
            requestedBefore = viewModel.calendarPermissionRequested,
            showRationale = activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_CALENDAR),
        )
        when (action) {
            CalendarConnectAction.REQUEST -> {
                viewModel.onCalendarPermissionRequested()
                calendarPermission.launch(Manifest.permission.READ_CALENDAR)
            }
            CalendarConnectAction.OPEN_SETTINGS -> openAppSettings(context)
            CalendarConnectAction.NONE -> viewModel.reloadEvents()
        }
    }
    // Back in the app (or the screen turned on again): the date, the calendar or its permission
    // may have changed meanwhile.
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshToday()
        viewModel.reloadEvents()
        onPauseOrDispose {}
    }
    DayContent(
        state = state,
        onPreviousDay = viewModel::previousDay,
        onNextDay = viewModel::nextDay,
        onToday = viewModel::goToToday,
        taskCallbacks = TaskCallbacks(
            onOpen = viewModel::openTask,
            onCheckboxTap = viewModel::onCheckboxTap,
            onSwipeLeft = viewModel::onSwipeLeft,
            onSwipeRight = viewModel::onSwipeRight,
        ),
        routineCallbacks = RoutineCallbacks(
            onTap = viewModel::onRoutineTap,
            onSwipeLeft = viewModel::onRoutineSwipeLeft,
        ),
        onAddTask = viewModel::startNewTask,
        onOpenBacklog = viewModel::openBacklog,
        onOpenReview = viewModel::openReview,
        onToggleScheduledSort = viewModel::toggleScheduledClosedLast,
        calendarPrompt = if (viewModel.calendarPromptVisible) {
            CalendarPromptActions(
                onAllow = connectCalendar,
                onDismiss = viewModel::dismissCalendarPrompt,
            )
        } else {
            null
        },
        eventCallbacks = EventCallbacks(
            onOpen = { openInCalendar(context, it) },
            onCheckboxTap = viewModel::onEventCheckboxTap,
            onSwipeLeft = viewModel::onEventSwipeLeft,
        ),
        // The banner already offers it; the quiet row is for after "Not now".
        onConnectCalendar = connectCalendar.takeIf { !viewModel.calendarAccess && !viewModel.calendarPromptVisible },
        modifier = modifier,
    )
    viewModel.sheet?.let { sheet ->
        TaskSheet(
            sheet = sheet,
            day = state.day,
            today = state.today,
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
            today = state.today,
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
            target = backlogTarget(state.day, state.today),
            today = state.today,
            actions = BacklogActions(
                onAdd = viewModel::addToBacklog,
                onSchedule = viewModel::scheduleFromBacklog,
                onOpen = viewModel::openBacklogTask,
                onDismiss = viewModel::closeBacklog,
            ),
        )
    }
}

/** What can be done to a calendar event in the day list. */
class EventCallbacks(
    val onOpen: (CalendarEvent) -> Unit,
    val onCheckboxTap: (CalendarEvent) -> Unit,
    val onSwipeLeft: (CalendarEvent) -> Unit,
) {
    companion object {
        val None = EventCallbacks({}, {}, {})
    }
}

/** What can be done to a routine in the day list. */
class RoutineCallbacks(val onTap: (RoutineItem) -> Unit, val onSwipeLeft: (RoutineItem) -> Unit) {
    companion object {
        val None = RoutineCallbacks({}, {})
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
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit,
    taskCallbacks: TaskCallbacks,
    routineCallbacks: RoutineCallbacks,
    onAddTask: () -> Unit,
    onOpenBacklog: () -> Unit,
    onOpenReview: () -> Unit,
    onToggleScheduledSort: () -> Unit,
    calendarPrompt: CalendarPromptActions?,
    eventCallbacks: EventCallbacks,
    /** Shows a "Connect calendar" row at the end of the list; null hides it. */
    onConnectCalendar: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        // System bars are handled by AppRoot's Scaffold around this screen.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            // The one brick-red accent of the "Paper" palette.
            FloatingActionButton(
                onClick = onAddTask,
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
            ) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.day_add_task))
            }
        },
    ) { innerPadding ->
        DayList(
            state, state.today, onPreviousDay, onNextDay, onToday, onOpenBacklog, onOpenReview, onToggleScheduledSort,
            calendarPrompt, eventCallbacks, onConnectCalendar, taskCallbacks, routineCallbacks, Modifier.padding(innerPadding),
        )
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
    onToggleScheduledSort: () -> Unit,
    calendarPrompt: CalendarPromptActions?,
    eventCallbacks: EventCallbacks,
    /** Shows a "Connect calendar" row at the end of the list; null hides it. */
    onConnectCalendar: (() -> Unit)?,
    taskCallbacks: TaskCallbacks,
    routineCallbacks: RoutineCallbacks,
    modifier: Modifier = Modifier,
) {
    // Bottom padding keeps the last task clear of the floating "+" button.
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
        item { DayHeader(state, onPreviousDay, onNextDay, onToday, onOpenBacklog, onOpenReview) }

        // Overdue tasks are about today: other days are for planning, so they stay quiet.
        if (state.isToday && state.overdue.isNotEmpty()) {
            item(key = "overdue") { OverdueBanner(state.overdue.size, onOpenReview, Modifier.animateItem()) }
        }
        calendarPrompt?.let { actions ->
            item(key = "calendar-prompt") { CalendarPromptBanner(actions, Modifier.animateItem()) }
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

        if (state.allDayEvents.isNotEmpty()) {
            item(key = R.string.day_section_all_day) { SectionTitle(stringResource(R.string.day_section_all_day), Modifier.animateItem()) }
            items(state.allDayEvents, key = ::eventKey) { event ->
                EventSwipeRow(event, state.day, eventCallbacks, Modifier.animateItem())
            }
        }

        if (state.scheduled.isNotEmpty()) {
            item(key = R.string.day_section_timed) {
                SectionTitle(stringResource(R.string.day_section_timed), Modifier.animateItem()) {
                    ScheduledSortToggle(state.scheduledClosedLast, onToggleScheduledSort)
                }
            }
            // The start time is written once for a run of rows that begin together.
            val slots = timeSlots(state.scheduled, state.day)
            itemsIndexed(
                state.scheduled,
                key = { _, it ->
                    when (it) {
                        is ScheduledItem.TaskItem -> it.task.id
                        is ScheduledItem.EventItem -> eventKey(it.event)
                    }
                },
            ) { index, item ->
                val showTime = slots[index] != null
                when (item) {
                    is ScheduledItem.TaskItem -> TaskRow(item.task, today, taskCallbacks, Modifier.animateItem(), showTime, state.moveCounts[item.task.id] ?: 0)
                    is ScheduledItem.EventItem ->
                        EventSwipeRow(item.event, state.day, eventCallbacks, Modifier.animateItem(), showTimeLabel = showTime)
                }
            }
        }

        if (state.untimed.isNotEmpty()) {
            item(key = R.string.day_section_untimed) { SectionTitle(stringResource(R.string.day_section_untimed), Modifier.animateItem()) }
            items(state.untimed, key = { it.id }) { task ->
                // animateItem: animates the move when a task sinks to the bottom after being closed.
                TaskRow(task, today, taskCallbacks, Modifier.animateItem(), moveCount = state.moveCounts[task.id] ?: 0)
            }
        }

        if (state.routines.isNotEmpty()) {
            item(key = R.string.day_section_routines) { SectionTitle(stringResource(R.string.day_section_routines), Modifier.animateItem()) }
            items(state.routines, key = { "routine-${it.routine.id}" }) { item ->
                RoutineRow(
                    item = item,
                    onTap = { routineCallbacks.onTap(item) },
                    onSwipeLeft = { routineCallbacks.onSwipeLeft(item) },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        onConnectCalendar?.let { connect ->
            item(key = "calendar-connect") { CalendarConnectRow(connect, Modifier.animateItem()) }
        }
    }
}

/** List key of an event occurrence: a repeating event has one row per occurrence. Tasks use their Long id. */
private fun eventKey(event: CalendarEvent): String = "event-${event.eventId}-${event.beginMillis}"

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
private fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.Bottom, modifier = modifier.fillMaxWidth().padding(end = 4.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f).padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        trailing?.invoke()
    }
}

/** "Done tasks at the bottom" for the Scheduled section; highlighted while on. */
@Composable
private fun ScheduledSortToggle(closedLast: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    IconToggleButton(checked = closedLast, onCheckedChange = { onToggle() }) {
        Icon(
            painterResource(R.drawable.ic_vertical_align_bottom),
            contentDescription = stringResource(R.string.scheduled_closed_last),
            tint = if (closedLast) colors.primary else colors.outline,
        )
    }
}

/** A task row: tap opens the task; swipe left crosses it out (or restores); swipe right moves it to the next day. */
@Composable
private fun TaskRow(
    task: Task,
    today: LocalDate,
    callbacks: TaskCallbacks,
    modifier: Modifier = Modifier,
    /** False for a timed task whose start repeats the row above: its time column stays blank. */
    showTime: Boolean = true,
    /** How many times the task was postponed; shown in its subtitle when above 0. */
    moveCount: Int = 0,
) {
    SwipeableRow(
        allowLeft = canSwipeLeft(task),
        allowRight = canSwipeRight(task),
        leftRestores = task.status == TaskStatus.CANCELLED,
        onSwipeLeft = { callbacks.onSwipeLeft(task.id) },
        onSwipeRight = { callbacks.onSwipeRight(task.id) },
        modifier = modifier,
    ) {
        TaskRowContent(
            task = task,
            today = today,
            onOpen = { callbacks.onOpen(task.id) },
            onCheckboxTap = { callbacks.onCheckboxTap(task.id) },
            showTime = showTime,
            moveCount = moveCount,
        )
    }
}

/** A calendar event row: tap opens it in the calendar app; checkbox and swipe left set the app's own mark. */
@Composable
private fun EventSwipeRow(event: CalendarEvent, day: LocalDate, callbacks: EventCallbacks, modifier: Modifier = Modifier, showTimeLabel: Boolean = true) {
    SwipeableRow(
        allowLeft = canSwipeLeft(event),
        allowRight = false,
        leftRestores = event.status == TaskStatus.CANCELLED,
        onSwipeLeft = { callbacks.onSwipeLeft(event) },
        onSwipeRight = {},
        modifier = modifier,
    ) {
        EventRow(
            event = event,
            day = day,
            onOpen = { callbacks.onOpen(event) },
            onCheckboxTap = { callbacks.onCheckboxTap(event) },
            showTimeLabel = showTimeLabel,
        )
    }
}

/** A routine row: checkbox marks it done; swipe left skips it this time (or un-skips). */
@Composable
private fun RoutineRow(item: RoutineItem, onTap: () -> Unit, onSwipeLeft: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val textColor by animateColorAsState(taskTitleColor(item.status), label = "routineTextColor")
    val frequency = stringResource(
        if (item.routine.frequency == Frequency.DAILY) R.string.routine_daily else R.string.routine_this_week,
    )
    SwipeableRow(
        allowLeft = canSwipeLeft(item),
        allowRight = false,
        leftRestores = item.status == TaskStatus.CANCELLED,
        onSwipeLeft = onSwipeLeft,
        onSwipeRight = {},
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().background(colors.surface).padding(start = 4.dp, end = 16.dp),
        ) {
            StatusCheckbox(item.status, enabled = item.canMark, onTap = onTap)
            Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    item.routine.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor,
                    textDecoration = if (isStruckThrough(item.status)) TextDecoration.LineThrough else null,
                )
                Text(
                    listOfNotNull(frequency, item.routine.note).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.status == TaskStatus.OPEN) colors.onSurfaceVariant else textColor,
                )
            }
        }
    }
}

/**
 * Horizontal swipe on a list row: left (cross out / restore) and right (move on). The row slides
 * back first and only then the action fires, so the slide-back and the row moving within the
 * list do not run at the same time. TalkBack gets the same actions in its menu.
 */
@Composable
private fun SwipeableRow(
    allowLeft: Boolean,
    allowRight: Boolean,
    /** Left swipe restores a crossed-out row instead of crossing it out: changes icon and label. */
    leftRestores: Boolean,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val limits = remember(density) {
        with(density) {
            SwipeLimits(max = 120.dp.toPx(), threshold = 72.dp.toPx(), minFling = 24.dp.toPx(), flingVelocity = 1000.dp.toPx())
        }
    }
    var offset by remember { mutableFloatStateOf(0f) }
    val currentOnLeft by rememberUpdatedState(onSwipeLeft)
    val currentOnRight by rememberUpdatedState(onSwipeRight)
    val dragState = rememberDraggableState { delta ->
        val wasArmed = limits.armedDirection(offset)
        offset = limits.clamp(offset + delta, allowLeft, allowRight)
        val armed = limits.armedDirection(offset)
        if (armed != null && armed != wasArmed) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    }
    val leftLabel = stringResource(if (leftRestores) R.string.task_restore else R.string.task_cancel)
    val rightLabel = stringResource(R.string.task_move_next_day)

    Box(
        modifier = modifier.semantics {
            // Swipes are invisible to TalkBack, so offer the same actions in its menu.
            customActions = buildList {
                if (allowLeft) add(CustomAccessibilityAction(leftLabel) { onSwipeLeft(); true })
                if (allowRight) add(CustomAccessibilityAction(rightLabel) { onSwipeRight(); true })
            }
        },
    ) {
        if (offset != 0f) {
            SwipeBackground(
                direction = if (offset < 0f) SwipeDirection.LEFT else SwipeDirection.RIGHT,
                restore = leftRestores,
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
                            SwipeDirection.LEFT -> currentOnLeft()
                            SwipeDirection.RIGHT -> currentOnRight()
                            null -> {}
                        }
                    },
                ),
        ) {
            content()
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
private fun TaskRowContent(
    task: Task,
    today: LocalDate,
    onOpen: () -> Unit,
    onCheckboxTap: () -> Unit,
    showTime: Boolean = true,
    moveCount: Int = 0,
) {
    val colors = MaterialTheme.colorScheme
    val textColor by animateColorAsState(taskTitleColor(task.status), label = "taskTextColor")
    val struck = isStruckThrough(task.status)
    val timed = task.placement as? Placement.Timed

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        // Opaque, so the swipe background shows only where the row has slid away.
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .clickable(onClick = onOpen)
            .padding(start = if (timed != null) 16.dp else 4.dp, end = 16.dp),
    ) {
        // Timed tasks read as a timeline: time, checkbox, title.
        timed?.let {
            TimeColumn(
                start = it.start.toString().takeIf { showTime },
                end = it.start.plus(it.duration).toString().takeIf { showTime },
                dimmed = task.status != TaskStatus.OPEN,
            )
        }
        StatusCheckbox(task.status, enabled = canTapCheckbox(task), onTap = onCheckboxTap)
        Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
                textDecoration = if (struck) TextDecoration.LineThrough else null,
            )
            taskSubtitle(task, today, moveCount)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (task.status == TaskStatus.OPEN) colors.onSurfaceVariant else textColor,
                )
            }
        }
    }
}

/** The time column of the "Scheduled" timeline: start above, end below and smaller; blank (same width) when null. */
@Composable
internal fun TimeColumn(start: String?, end: String?, dimmed: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = Modifier.width(44.dp)) {
        start?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = if (dimmed) colors.outline else colors.onSurface) }
        end?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (dimmed) colors.outline else colors.onSurfaceVariant) }
    }
}

/** Square box: empty when open, green ✓ when done, grey ✕ when cancelled, → when moved. */
@Composable
internal fun StatusCheckbox(status: TaskStatus, enabled: Boolean, onTap: () -> Unit, openColor: Color? = null) {
    val colors = MaterialTheme.colorScheme
    val done = LocalStatusColors.current.done
    val fill by animateColorAsState(
        when (status) {
            TaskStatus.DONE -> done
            TaskStatus.CANCELLED -> colors.outline
            TaskStatus.OPEN, TaskStatus.MOVED -> Color.Transparent
        },
        label = "checkboxFill",
    )
    val border = when (status) {
        // A box that cannot be ticked (a routine on a future day) is drawn fainter.
        // An open calendar event borrows its calendar's color, so it reads as an event.
        TaskStatus.OPEN -> if (enabled) openColor ?: colors.onSurfaceVariant else colors.outlineVariant
        TaskStatus.DONE -> done
        TaskStatus.CANCELLED -> colors.outline
        TaskStatus.MOVED -> Color.Transparent
    }
    val icon = when (status) {
        TaskStatus.OPEN -> null
        TaskStatus.DONE -> R.drawable.ic_check
        TaskStatus.CANCELLED -> R.drawable.ic_close
        TaskStatus.MOVED -> R.drawable.ic_arrow_forward
    }
    val state = stringResource(
        when (status) {
            TaskStatus.OPEN -> R.string.task_state_open
            TaskStatus.DONE -> R.string.task_state_done
            TaskStatus.CANCELLED -> R.string.task_state_cancelled
            TaskStatus.MOVED -> R.string.task_state_moved
        },
    )
    val action = stringResource(
        when (status) {
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
            .clickable(enabled = enabled, role = Role.Checkbox, onClickLabel = action, onClick = onTap)
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
                    tint = if (status == TaskStatus.MOVED) colors.outline else colors.surface,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Where a MOVED task went, how often the task was postponed, the note; null if none. The time has its own column. */
@Composable
private fun taskSubtitle(task: Task, today: LocalDate, moveCount: Int): String? {
    val parts = buildList {
        if (task.status == TaskStatus.MOVED) {
            add(
                task.movedTo?.let { stringResource(R.string.task_moved_to, relativeDayLabel(it, today)) }
                    ?: stringResource(R.string.task_moved_to_backlog),
            )
        }
        // A MOVED trace already says where it went; the count is for the task that is still around.
        if (moveCount > 0 && task.status != TaskStatus.MOVED) {
            add(pluralStringResource(R.plurals.task_postponed, moveCount, moveCount))
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
            state = buildDayUiState(
                today, today, sampleTasks(today), sampleEvents(today),
                routinesOn(today, today, sampleRoutines(today), sampleRoutineMarks(today), DayOfWeek.MONDAY),
            ),
            onPreviousDay = {}, onNextDay = {}, onToday = {}, taskCallbacks = TaskCallbacks.None, routineCallbacks = RoutineCallbacks.None,
            onAddTask = {}, onOpenBacklog = {}, onOpenReview = {}, onToggleScheduledSort = {},
            calendarPrompt = null, eventCallbacks = EventCallbacks.None, onConnectCalendar = null,
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DayContentDarkPreview() {
    val today = LocalDate.of(2026, 10, 15)
    MyOrganizerTheme {
        DayContent(
            state = buildDayUiState(
                today, today, sampleTasks(today), sampleEvents(today),
                routinesOn(today, today, sampleRoutines(today), sampleRoutineMarks(today), DayOfWeek.MONDAY),
            ),
            onPreviousDay = {}, onNextDay = {}, onToday = {}, taskCallbacks = TaskCallbacks.None, routineCallbacks = RoutineCallbacks.None,
            onAddTask = {}, onOpenBacklog = {}, onOpenReview = {}, onToggleScheduledSort = {},
            calendarPrompt = null, eventCallbacks = EventCallbacks.None, onConnectCalendar = null,
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
            onPreviousDay = {}, onNextDay = {}, onToday = {}, taskCallbacks = TaskCallbacks.None, routineCallbacks = RoutineCallbacks.None,
            onAddTask = {}, onOpenBacklog = {}, onOpenReview = {}, onToggleScheduledSort = {},
            calendarPrompt = null, eventCallbacks = EventCallbacks.None, onConnectCalendar = null,
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
            onPreviousDay = {}, onNextDay = {}, onToday = {}, taskCallbacks = TaskCallbacks.None, routineCallbacks = RoutineCallbacks.None,
            onAddTask = {}, onOpenBacklog = {}, onOpenReview = {}, onToggleScheduledSort = {},
            calendarPrompt = null, eventCallbacks = EventCallbacks.None, onConnectCalendar = null,
        )
    }
}
