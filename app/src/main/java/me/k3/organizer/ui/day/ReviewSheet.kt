package me.k3.organizer.ui.day

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.k3.organizer.R
import me.k3.organizer.model.Placement
import me.k3.organizer.model.Task
import me.k3.organizer.ui.theme.LocalStatusColors
import me.k3.organizer.ui.theme.MyOrganizerTheme
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/** Callbacks of the overdue review; task callbacks get the task id. */
class ReviewActions(
    val onDone: (Long) -> Unit,
    val onCancel: (Long) -> Unit,
    val onToday: (Long) -> Unit,
    val onBacklog: (Long) -> Unit,
    val onAllToToday: (List<Long>) -> Unit,
    val onDismiss: () -> Unit,
)

/** Bottom sheet that asks what to do with each open task left on a past day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewSheet(tasks: List<Task>, today: LocalDate, actions: ReviewActions) {
    ModalBottomSheet(
        onDismissRequest = actions.onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        ReviewContent(tasks, today, actions)
    }
}

@Composable
private fun ReviewContent(tasks: List<Task>, today: LocalDate, actions: ReviewActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(stringResource(R.string.review_title), style = MaterialTheme.typography.titleLarge)
            Text(
                pluralStringResource(R.plurals.review_subtitle, tasks.size, tasks.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Bounded height: the sheet must not grow past the screen when many tasks are overdue.
        LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
            items(tasks, key = { it.id }) { task ->
                Column(modifier = Modifier.animateItem()) {
                    ReviewRow(task, today, actions)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            TextButton(onClick = actions.onDismiss) { Text(stringResource(R.string.review_later)) }
            Spacer(Modifier.weight(1f))
            Button(onClick = { actions.onAllToToday(tasks.map { it.id }) }, enabled = tasks.isNotEmpty()) {
                Text(stringResource(R.string.review_all_to_today))
            }
        }
    }
}

@Composable
private fun ReviewRow(task: Task, today: LocalDate, actions: ReviewActions) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(task.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                reviewSubtitle(task, today),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        ) {
            ReviewChip(R.drawable.ic_check, R.string.review_done, LocalStatusColors.current.done) { actions.onDone(task.id) }
            ReviewChip(R.drawable.ic_close, R.string.review_cross, MaterialTheme.colorScheme.outline) { actions.onCancel(task.id) }
            ReviewChip(R.drawable.ic_arrow_forward, R.string.day_today, MaterialTheme.colorScheme.secondary) { actions.onToday(task.id) }
            ReviewChip(R.drawable.ic_inbox, R.string.move_backlog, MaterialTheme.colorScheme.onSurfaceVariant) { actions.onBacklog(task.id) }
        }
    }
}

@Composable
private fun ReviewChip(icon: Int, label: Int, iconColor: Color, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(stringResource(label)) },
        leadingIcon = {
            Icon(painterResource(icon), contentDescription = null, tint = iconColor, modifier = Modifier.size(AssistChipDefaults.IconSize))
        },
    )
}

/** "Yesterday · 14:00–15:00" or "Mon, 12 Oct". */
@Composable
private fun reviewSubtitle(task: Task, today: LocalDate): String {
    val day = relativeDayLabel(checkNotNull(task.day), today)
    val time = (task.placement as? Placement.Timed)?.let { "${it.start}–${it.start.plus(it.duration)}" }
    return listOfNotNull(day, time).joinToString(" · ")
}

/** Shown on today's list while overdue tasks wait, so a dismissed review can be reopened. */
@Composable
fun OverdueBanner(count: Int, onReview: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 8.dp)) {
            Icon(
                painterResource(R.drawable.ic_warning),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(12.dp))
            Text(
                pluralStringResource(R.plurals.review_banner, count, count),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onReview) { Text(stringResource(R.string.review_open)) }
        }
    }
}

private val previewToday = LocalDate.of(2026, 10, 15)

@Preview(showBackground = true)
@Composable
private fun ReviewPreview() {
    val tasks = listOf(
        Task(id = 7, title = "Pay rent", day = previewToday.minusDays(3)),
        Task(id = 3, title = "Call the bank", day = previewToday.minusDays(1)),
        Task(id = 2, title = "Dentist", day = previewToday.minusDays(1), startTime = LocalTime.of(14, 0), duration = Duration.ofHours(1)),
    )
    MyOrganizerTheme {
        Surface { ReviewContent(tasks, previewToday, ReviewActions({}, {}, {}, {}, {}, {})) }
    }
}

@Preview(showBackground = true)
@Composable
private fun OverdueBannerPreview() {
    MyOrganizerTheme { Surface { OverdueBanner(count = 3, onReview = {}) } }
}
