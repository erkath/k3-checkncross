package me.k3.organizer.ui.day

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.k3.organizer.R
import me.k3.organizer.model.Task
import me.k3.organizer.ui.theme.MyOrganizerTheme
import java.time.LocalDate

/** Callbacks of the backlog sheet; task callbacks get the task id. */
class BacklogActions(
    val onAdd: (String) -> Unit,
    val onSchedule: (Long) -> Unit,
    val onOpen: (Long) -> Unit,
    val onDismiss: () -> Unit,
)

/** Bottom sheet listing tasks without a day; each can be planned for [target] in one tap. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BacklogSheet(tasks: List<Task>, target: LocalDate, today: LocalDate, actions: BacklogActions) {
    ModalBottomSheet(
        onDismissRequest = actions.onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        BacklogContent(tasks, target, today, actions)
    }
}

@Composable
private fun BacklogContent(tasks: List<Task>, target: LocalDate, today: LocalDate, actions: BacklogActions) {
    // Only the sheet edits this text and it is not needed elsewhere, so it lives here.
    var newTitle by rememberSaveable { mutableStateOf("") }
    fun add() {
        if (newTitle.isNotBlank()) {
            actions.onAdd(newTitle)
            newTitle = ""
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
    ) {
        Text(
            stringResource(R.string.backlog_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 24.dp, end = 12.dp)) {
            OutlinedTextField(
                value = newTitle,
                onValueChange = { newTitle = it },
                label = { Text(stringResource(R.string.backlog_add_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                // Done adds the task and keeps the keyboard open for the next one.
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = ::add, enabled = newTitle.isNotBlank()) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.action_add))
            }
        }

        if (tasks.isEmpty()) {
            Text(
                stringResource(R.string.backlog_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        } else {
            val targetLabel = relativeDayLabel(target, today)
            // Bounded height: the sheet must not grow past the screen with a long backlog.
            LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
                items(tasks, key = { it.id }) { task ->
                    BacklogRow(
                        task = task,
                        targetLabel = targetLabel,
                        onSchedule = { actions.onSchedule(task.id) },
                        onOpen = { actions.onOpen(task.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun BacklogRow(task: Task, targetLabel: String, onSchedule: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val planFor = stringResource(R.string.backlog_plan_for, targetLabel)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 24.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(task.title, style = MaterialTheme.typography.bodyLarge)
            task.note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AssistChip(
            onClick = onSchedule,
            label = { Text(targetLabel) },
            leadingIcon = {
                Icon(painterResource(R.drawable.ic_arrow_forward), contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
            },
            modifier = Modifier.semantics { contentDescription = planFor },
        )
    }
}

private val previewDay = LocalDate.of(2026, 10, 15)
private val previewActions = BacklogActions({}, {}, {}, {})

@Preview(showBackground = true)
@Composable
private fun BacklogPreview() {
    val tasks = listOf(
        Task(id = 8, title = "Read Kotlin docs"),
        Task(id = 9, title = "Fix the bike", note = "Rear brake"),
        Task(id = 10, title = "Call grandma"),
    )
    MyOrganizerTheme { Surface { BacklogContent(tasks, previewDay, previewDay, previewActions) } }
}

@Preview(showBackground = true)
@Composable
private fun BacklogEmptyPreview() {
    MyOrganizerTheme { Surface { BacklogContent(emptyList(), previewDay.plusDays(1), previewDay, previewActions) } }
}
