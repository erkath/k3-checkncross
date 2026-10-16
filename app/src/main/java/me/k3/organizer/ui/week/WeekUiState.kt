package me.k3.organizer.ui.week

import me.k3.organizer.model.CalendarEvent
import me.k3.organizer.model.EventMark
import me.k3.organizer.model.Task
import me.k3.organizer.model.TaskStatus
import me.k3.organizer.ui.day.DayUiState
import me.k3.organizer.ui.day.buildDayUiState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

data class WeekUiState(
    val weekStart: LocalDate,
    val today: LocalDate,
    /** Seven days from [weekStart], each built exactly as the Day screen builds it. */
    val days: List<DayUiState> = emptyList(),
) {
    val weekEnd: LocalDate get() = weekStart.plusDays(6)
    val containsToday: Boolean get() = today in weekStart..weekEnd
}

/** The first day of the week containing [date]; [firstDayOfWeek] comes from the phone's locale. */
fun weekStartOf(date: LocalDate, firstDayOfWeek: DayOfWeek): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))

fun buildWeekUiState(
    weekStart: LocalDate,
    today: LocalDate,
    tasks: List<Task>,
    eventsByDay: Map<LocalDate, List<CalendarEvent>>,
    eventMarks: List<EventMark> = emptyList(),
    scheduledClosedLast: Boolean = false,
): WeekUiState = WeekUiState(
    weekStart = weekStart,
    today = today,
    days = (0L..6L).map { offset ->
        val day = weekStart.plusDays(offset)
        buildDayUiState(day, today, tasks, eventsByDay[day].orEmpty(), eventMarks = eventMarks, scheduledClosedLast = scheduledClosedLast)
    },
)

/** Open tasks of the day, timed or not: the number shown next to the day in the week. */
fun DayUiState.openCount(): Int = (timed + untimed).count { it.status == TaskStatus.OPEN }

/** The week to show once "today" changes: a screen that was on today's week follows it. */
fun shownWeekAfterTodayChange(shownStart: LocalDate, oldToday: LocalDate, newToday: LocalDate, firstDayOfWeek: DayOfWeek): LocalDate =
    if (shownStart == weekStartOf(oldToday, firstDayOfWeek)) weekStartOf(newToday, firstDayOfWeek) else shownStart

/**
 * "12–18 Oct" or "28 Sep – 4 Oct". Outside [currentYear] the year is added once at the end
 * ("12–18 Oct 2027"), or to both ends when the week spans two years ("29 Dec 2025 – 4 Jan 2026").
 * Month names follow [locale].
 */
fun weekRangeLabel(start: LocalDate, end: LocalDate, currentYear: Int, locale: Locale): String {
    fun format(pattern: String, date: LocalDate) = date.format(DateTimeFormatter.ofPattern(pattern, locale))
    if (start.year != end.year) return "${format("d MMM yyyy", start)} – ${format("d MMM yyyy", end)}"
    val endPattern = if (end.year == currentYear) "d MMM" else "d MMM yyyy"
    return if (start.month == end.month) {
        "${start.dayOfMonth}–${format(endPattern, end)}"
    } else {
        "${format("d MMM", start)} – ${format(endPattern, end)}"
    }
}
