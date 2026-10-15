package me.k3.organizer.ui.day

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.k3.organizer.R
import me.k3.organizer.model.Task
import me.k3.organizer.model.TaskStatus
import me.k3.organizer.ui.theme.MyOrganizerTheme
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val sheetDayFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM")
private val chipDayFormat = DateTimeFormatter.ofPattern("EEE, d MMM")

/** Callbacks of the task sheet. [onMove] gets the target day, or null for the backlog. */
class TaskSheetActions(
    val onChange: (TaskForm) -> Unit,
    val onTimeEnabled: (Boolean) -> Unit,
    val onSave: () -> Unit,
    val onMove: (LocalDate?) -> Unit,
    val onUndoMove: () -> Unit,
    val onDelete: () -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * Bottom sheet to create a task on [day] or to edit, move or delete an existing one.
 * Slides away before reporting any final action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskSheet(sheet: TaskSheetState, day: LocalDate, today: LocalDate, actions: TaskSheetActions) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun hideThen(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { action() }
    }

    ModalBottomSheet(onDismissRequest = actions.onDismiss, sheetState = sheetState) {
        TaskSheetContent(
            sheet = sheet,
            day = day,
            today = today,
            actions = TaskSheetActions(
                onChange = actions.onChange,
                onTimeEnabled = actions.onTimeEnabled,
                onSave = { if (sheet.form.canSave) hideThen(actions.onSave) },
                onMove = { target -> hideThen { actions.onMove(target) } },
                onUndoMove = { hideThen(actions.onUndoMove) },
                onDelete = { hideThen(actions.onDelete) },
                onDismiss = { hideThen(actions.onDismiss) },
            ),
            autoFocus = sheet.isNew,
        )
    }
}

/** The sheet's content without the sheet itself, so it can be previewed. */
@Composable
private fun TaskSheetContent(
    sheet: TaskSheetState,
    day: LocalDate,
    today: LocalDate,
    actions: TaskSheetActions,
    autoFocus: Boolean = false,
) {
    val form = sheet.form
    val task = sheet.task
    val titleFocus = remember { FocusRequester() }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
    ) {
        Text(
            stringResource(if (sheet.isNew) R.string.new_task_header else R.string.edit_task_header),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            sheetSubtitle(task, day),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = form.title,
            onValueChange = { actions.onChange(form.copy(title = it)) },
            label = { Text(stringResource(R.string.new_task_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().focusRequester(titleFocus),
        )
        OutlinedTextField(
            value = form.note,
            onValueChange = { actions.onChange(form.copy(note = it)) },
            label = { Text(stringResource(R.string.new_task_note)) },
            maxLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )

        if (sheet.canEditTime) {
            TimeSection(form, actions, onPickTime = { pickingTime = true })
        }

        if (sheet.canMove && task != null) {
            MoveSection(task, today, actions, onPickDate = { pickingDate = true })
        }

        if (sheet.canUndoMove) {
            OutlinedButton(onClick = actions.onUndoMove) {
                Icon(painterResource(R.drawable.ic_undo), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.task_undo_move))
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (!sheet.isNew) {
                TextButton(onClick = { confirmingDelete = true }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = actions.onDismiss) { Text(stringResource(R.string.action_cancel)) }
            Spacer(Modifier.size(8.dp))
            Button(onClick = actions.onSave, enabled = form.canSave) {
                Text(stringResource(if (sheet.isNew) R.string.action_add else R.string.action_save))
            }
        }
    }

    form.startTime?.let { start ->
        if (pickingTime) {
            StartTimeDialog(
                initial = start,
                onConfirm = { actions.onChange(form.copy(startTime = it)); pickingTime = false },
                onDismiss = { pickingTime = false },
            )
        }
    }
    if (pickingDate && task != null) {
        MoveDateDialog(
            task = task,
            today = today,
            onConfirm = { pickingDate = false; actions.onMove(it) },
            onDismiss = { pickingDate = false },
        )
    }
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.delete_task_title)) },
            text = { Text(stringResource(R.string.delete_task_text, form.title.ifBlank { task?.title.orEmpty() })) },
            confirmButton = {
                TextButton(onClick = { confirmingDelete = false; actions.onDelete() }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (autoFocus) {
        // Open the keyboard on the title right away.
        LaunchedEffect(Unit) { titleFocus.requestFocus() }
    }
}

/** The task's day, "Backlog" for a task without one, or where a MOVED task went. */
@Composable
private fun sheetSubtitle(task: Task?, day: LocalDate): String {
    if (task?.status == TaskStatus.MOVED) {
        return task.movedTo?.let { stringResource(R.string.task_moved_to, it.format(chipDayFormat)) }
            ?: stringResource(R.string.task_moved_to_backlog)
    }
    if (task != null && task.day == null) return stringResource(R.string.backlog_title)
    return (task?.day ?: day).format(sheetDayFormat)
}

@Composable
private fun TimeSection(form: TaskForm, actions: TaskSheetActions, onPickTime: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.new_task_time), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = form.startTime != null, onCheckedChange = actions.onTimeEnabled)
    }
    val start = form.startTime ?: return
    AssistChip(onClick = onPickTime, label = { Text("$start – ${start.plus(form.duration)}") })
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        for (choice in TaskForm.DURATION_CHOICES) {
            FilterChip(
                selected = form.duration == choice,
                onClick = { actions.onChange(form.copy(duration = choice)) },
                label = { Text(durationLabel(choice)) },
            )
        }
    }
}

@Composable
private fun MoveSection(task: Task, today: LocalDate, actions: TaskSheetActions, onPickDate: () -> Unit) {
    val nextDay = nextDayTarget(task, today)
    Text(stringResource(R.string.move_header), style = MaterialTheme.typography.bodyLarge)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        AssistChip(
            onClick = { actions.onMove(nextDay) },
            label = { Text(relativeDayLabel(nextDay, today)) },
            leadingIcon = {
                Icon(painterResource(R.drawable.ic_arrow_forward), contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
            },
        )
        AssistChip(onClick = onPickDate, label = { Text(stringResource(R.string.move_pick_date)) })
        AssistChip(onClick = { actions.onMove(null) }, label = { Text(stringResource(R.string.move_backlog)) })
    }
}

@Composable
internal fun relativeDayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.day_today)
    today.plusDays(1) -> stringResource(R.string.day_tomorrow)
    else -> date.format(chipDayFormat)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartTimeDialog(initial: LocalTime, onConfirm: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        text = { TimePicker(state = state) },
    )
}

/** Date picker for moving [task]; offers only valid targets. The picker works in UTC millis. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveDateDialog(task: Task, today: LocalDate, onConfirm: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    fun toDate(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = nextDayTarget(task, today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = isValidMoveTarget(toDate(utcTimeMillis), task, today)
            override fun isSelectableYear(year: Int) = year >= today.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onConfirm(toDate(it)) } },
                enabled = state.selectedDateMillis != null,
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

@Composable
private fun durationLabel(duration: Duration): String {
    val hours = duration.toHours().toInt()
    val minutes = (duration.toMinutes() % 60).toInt()
    return when {
        hours == 0 -> stringResource(R.string.duration_minutes, minutes)
        minutes == 0 -> stringResource(R.string.duration_hours, hours)
        else -> stringResource(R.string.duration_hours_minutes, hours, minutes)
    }
}

private val previewDay = LocalDate.of(2026, 10, 15)
private val previewActions = TaskSheetActions({}, {}, {}, {}, {}, {}, {})

@Preview(showBackground = true)
@Composable
private fun TaskSheetNewPreview() {
    MyOrganizerTheme {
        Surface { TaskSheetContent(TaskSheetState(TaskForm()), previewDay, previewDay, previewActions) }
    }
}

@Preview(showBackground = true)
@Composable
private fun TaskSheetEditPreview() {
    val task = Task(id = 2, title = "Dentist", note = "Bring the insurance card", day = previewDay,
        startTime = LocalTime.of(14, 0), duration = Duration.ofHours(1))
    MyOrganizerTheme {
        Surface { TaskSheetContent(TaskSheetState(TaskForm.from(task), task), previewDay, previewDay, previewActions) }
    }
}

@Preview(showBackground = true)
@Composable
private fun TaskSheetMovedPreview() {
    val task = Task(id = 3, title = "Call the bank", status = TaskStatus.MOVED, day = previewDay, movedTo = previewDay.plusDays(1))
    MyOrganizerTheme {
        Surface {
            TaskSheetContent(TaskSheetState(TaskForm.from(task), task, canUndoMove = true), previewDay, previewDay, previewActions)
        }
    }
}
