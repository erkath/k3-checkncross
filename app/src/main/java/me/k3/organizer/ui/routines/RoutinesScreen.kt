package me.k3.organizer.ui.routines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.k3.organizer.R
import me.k3.organizer.data.sampleRoutines
import me.k3.organizer.model.Frequency
import me.k3.organizer.model.Routine
import me.k3.organizer.ui.theme.MyOrganizerTheme
import java.time.LocalDate

/** Routines tab bound to its ViewModel. */
@Composable
fun RoutinesScreen(viewModel: RoutinesViewModel, modifier: Modifier = Modifier) {
    val routines by viewModel.routines.collectAsStateWithLifecycle()
    RoutinesContent(routines, onAdd = viewModel::startNew, onOpen = viewModel::open, modifier = modifier)
    viewModel.sheet?.let { sheet ->
        RoutineSheet(
            sheet = sheet,
            onChange = viewModel::edit,
            onSave = viewModel::save,
            onDelete = viewModel::delete,
            onDismiss = viewModel::close,
        )
    }
}

/** Stateless list of routines with a "+" button. */
@Composable
fun RoutinesContent(routines: List<Routine>, onAdd: () -> Unit, onOpen: (Routine) -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        // System bars are handled by AppRoot's Scaffold around this screen.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.routines_add))
            }
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 88.dp)) {
            item {
                Column {
                    Text(
                        stringResource(R.string.routines_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    HorizontalDivider()
                }
            }
            if (routines.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.routines_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(routines, key = { it.id }) { routine ->
                RoutineListRow(routine, onOpen = { onOpen(routine) }, modifier = Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun RoutineListRow(routine: Routine, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val details = listOfNotNull(
        frequencyLabel(routine.frequency),
        routine.note,
        stringResource(R.string.routine_paused).takeUnless { routine.active },
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_repeat),
            contentDescription = null,
            tint = if (routine.active) colors.primary else colors.outline,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(16.dp))
        Column {
            Text(
                routine.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (routine.active) colors.onSurface else colors.outline,
            )
            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun frequencyLabel(frequency: Frequency): String = stringResource(
    when (frequency) {
        Frequency.DAILY -> R.string.routine_daily
        Frequency.WEEKLY -> R.string.routine_once_a_week
    },
)

/** Bottom sheet to add or edit a routine; slides away before reporting save, delete or cancel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineSheet(
    sheet: RoutineSheetState,
    onChange: (RoutineForm) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun hideThen(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { action() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        RoutineSheetContent(
            sheet = sheet,
            onChange = onChange,
            onSave = { if (sheet.form.canSave) hideThen(onSave) },
            onDelete = { hideThen(onDelete) },
            onCancel = { hideThen(onDismiss) },
            autoFocus = sheet.isNew,
        )
    }
}

@Composable
private fun RoutineSheetContent(
    sheet: RoutineSheetState,
    onChange: (RoutineForm) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    autoFocus: Boolean = false,
) {
    val form = sheet.form
    val titleFocus = remember { FocusRequester() }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
    ) {
        Text(
            stringResource(if (sheet.isNew) R.string.routine_new else R.string.routine_edit),
            style = MaterialTheme.typography.titleLarge,
        )
        OutlinedTextField(
            value = form.title,
            onValueChange = { onChange(form.copy(title = it)) },
            label = { Text(stringResource(R.string.routine_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().focusRequester(titleFocus),
        )
        OutlinedTextField(
            value = form.note,
            onValueChange = { onChange(form.copy(note = it)) },
            label = { Text(stringResource(R.string.routine_note)) },
            maxLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (frequency in Frequency.entries) {
                FilterChip(
                    selected = form.frequency == frequency,
                    onClick = { onChange(form.copy(frequency = frequency)) },
                    label = { Text(frequencyLabel(frequency)) },
                )
            }
        }
        if (!sheet.isNew) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.routine_active), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = form.active, onCheckedChange = { onChange(form.copy(active = it)) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (!sheet.isNew) {
                TextButton(onClick = { confirmingDelete = true }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
            Spacer(Modifier.size(8.dp))
            Button(onClick = onSave, enabled = form.canSave) {
                Text(stringResource(if (sheet.isNew) R.string.action_add else R.string.action_save))
            }
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.routine_delete_title)) },
            text = { Text(stringResource(R.string.routine_delete_text, sheet.routine?.title.orEmpty())) },
            confirmButton = {
                TextButton(onClick = { confirmingDelete = false; onDelete() }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (autoFocus) {
        LaunchedEffect(Unit) { titleFocus.requestFocus() }
    }
}

private val previewToday = LocalDate.of(2026, 10, 15)

@Preview(showBackground = true)
@Composable
private fun RoutinesPreview() {
    val routines = routineListOrder(sampleRoutines(previewToday) + Routine(id = 4, title = "Water plants", frequency = Frequency.WEEKLY, createdOn = previewToday, active = false))
    MyOrganizerTheme { RoutinesContent(routines, onAdd = {}, onOpen = {}) }
}

@Preview(showBackground = true)
@Composable
private fun RoutinesEmptyPreview() {
    MyOrganizerTheme { RoutinesContent(emptyList(), onAdd = {}, onOpen = {}) }
}

@Preview(showBackground = true)
@Composable
private fun RoutineSheetPreview() {
    val routine = sampleRoutines(previewToday).last()
    MyOrganizerTheme {
        Surface { RoutineSheetContent(RoutineSheetState(RoutineForm.from(routine), routine), {}, {}, {}, {}) }
    }
}
