package me.k3.organizer.ui.week

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.k3.organizer.R
import me.k3.organizer.data.sampleTasks
import me.k3.organizer.model.CalendarEvent
import me.k3.organizer.model.Task
import me.k3.organizer.model.TaskStatus
import me.k3.organizer.ui.common.isStruckThrough
import me.k3.organizer.ui.common.taskTitleColor
import me.k3.organizer.ui.day.DayUiState
import me.k3.organizer.ui.day.ScheduledItem
import me.k3.organizer.ui.day.relativeDayLabel
import me.k3.organizer.ui.day.sampleEvents
import me.k3.organizer.ui.day.timeSlots
import me.k3.organizer.ui.theme.LocalStatusColors
import me.k3.organizer.ui.theme.MyOrganizerTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val weekDayFormat = DateTimeFormatter.ofPattern("EEE, d MMM")

/** Week screen bound to its ViewModel. Tapping a day opens it on the Day screen. */
@Composable
fun WeekScreen(viewModel: WeekViewModel, onOpenDay: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose {}
    }
    WeekContent(
        state = state,
        onPreviousWeek = viewModel::previousWeek,
        onNextWeek = viewModel::nextWeek,
        onThisWeek = viewModel::thisWeek,
        onOpenDay = onOpenDay,
        modifier = modifier,
    )
}

/** Stateless week screen: seven day cards under a header with week navigation. */
@Composable
fun WeekContent(
    state: WeekUiState,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onThisWeek: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { WeekHeader(state, onPreviousWeek, onNextWeek, onThisWeek) }
        items(state.days, key = { it.day.toEpochDay() }) { day ->
            WeekDayCard(day, onOpen = { onOpenDay(day.day) })
        }
    }
}

@Composable
private fun WeekHeader(state: WeekUiState, onPrevious: () -> Unit, onNext: () -> Unit, onThisWeek: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onPrevious) {
                Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = stringResource(R.string.week_previous))
            }
            Text(
                // The configuration's locale, not Locale.getDefault(): Compose recomposes when it changes.
                text = weekRangeLabel(state.weekStart, state.weekEnd, state.today.year, LocalConfiguration.current.locales[0]),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (!state.containsToday) {
                TextButton(onClick = onThisWeek) { Text(stringResource(R.string.week_this)) }
            }
            IconButton(onClick = onNext) {
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = stringResource(R.string.week_next))
            }
        }
        HorizontalDivider()
    }
}

/** One day of the week: a tappable card listing what is planned, in the Day screen's order. */
@Composable
private fun WeekDayCard(day: DayUiState, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onOpen,
        color = if (day.isToday) colors.secondaryContainer else colors.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = day.day.format(weekDayFormat),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (day.isToday) FontWeight.Bold else null,
                )
                if (day.isToday || day.day == day.today.plusDays(1) || day.day == day.today.minusDays(1)) {
                    Text(
                        " · ${relativeDayLabel(day.day, day.today)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                val open = day.openCount()
                if (open > 0) {
                    Text(pluralStringResource(R.plurals.week_open, open, open), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                }
            }
            if (day.isEmpty) {
                Text(stringResource(R.string.week_nothing), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            day.allDayEvents.forEach { EventLine(it, day.day, time = null) }
            // As on the Day screen: a start time shared by several lines in a row is written once.
            val slots = timeSlots(day.scheduled, day.day)
            day.scheduled.forEachIndexed { index, item ->
                when (item) {
                    is ScheduledItem.TaskItem -> TaskLine(item.task, time = slots[index])
                    is ScheduledItem.EventItem -> EventLine(item.event, day.day, time = slots[index])
                }
            }
            day.untimed.forEach { TaskLine(it, time = null) }
        }
    }
}

/** A task in one line: [time] (blank if null), a small status mark, the title styled as on the Day screen. */
@Composable
private fun TaskLine(task: Task, time: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LineTime(time)
        StatusMark(task.status)
        Spacer(Modifier.width(8.dp))
        Text(
            task.title,
            style = MaterialTheme.typography.bodyMedium,
            color = taskTitleColor(task.status),
            textDecoration = if (isStruckThrough(task.status)) TextDecoration.LineThrough else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A calendar event in one line: [time] (blank if null, e.g. all-day), a bar in its color, the title. */
@Composable
private fun EventLine(event: CalendarEvent, day: LocalDate, time: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LineTime(time)
        Box(Modifier.size(width = 14.dp, height = 14.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(14.dp)
                    .background(event.color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            event.title.ifEmpty { stringResource(R.string.event_no_title) },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Fixed-width time slot at the start of a line; empty for untimed lines, so the marks line up. */
@Composable
private fun LineTime(time: String?) {
    Box(Modifier.width(44.dp)) {
        time?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** A small, non-interactive version of the Day screen's checkbox. */
@Composable
private fun StatusMark(status: TaskStatus) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(3.dp)
    when (status) {
        TaskStatus.OPEN -> Box(Modifier.size(14.dp).border(1.5.dp, colors.onSurfaceVariant, shape))
        TaskStatus.DONE -> Icon(painterResource(R.drawable.ic_check), null, tint = LocalStatusColors.current.done, modifier = Modifier.size(14.dp))
        TaskStatus.CANCELLED -> Icon(painterResource(R.drawable.ic_close), null, tint = colors.outline, modifier = Modifier.size(14.dp))
        TaskStatus.MOVED -> Icon(painterResource(R.drawable.ic_arrow_forward), null, tint = colors.outline, modifier = Modifier.size(14.dp))
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun WeekContentPreview() {
    val today = LocalDate.of(2026, 10, 15)
    val start = weekStartOf(today, DayOfWeek.MONDAY)
    MyOrganizerTheme {
        WeekContent(
            state = buildWeekUiState(start, today, sampleTasks(today), mapOf(today to sampleEvents(today))),
            onPreviousWeek = {}, onNextWeek = {}, onThisWeek = {}, onOpenDay = {},
        )
    }
}
