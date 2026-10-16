package me.k3.organizer.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * One occurrence of an event from the phone's calendar. Read-only and never stored in our database;
 * only the app's own mark on it is (see [EventMark]), which becomes [status].
 * For an all-day event [start] and [end] are midnights; [end] is exclusive.
 */
data class CalendarEvent(
    val eventId: Long,
    val title: String,
    val allDay: Boolean,
    val start: LocalDateTime,
    val end: LocalDateTime,
    /** ARGB color of the event or its calendar. */
    val color: Int?,
    val location: String?,
    /** The occurrence as the calendar stores it, in epoch millis; needed to open it in the calendar app. */
    val beginMillis: Long,
    val endMillis: Long,
    /** OPEN, or DONE / CANCELLED from the app's [EventMark]. Never written back to the calendar. */
    val status: TaskStatus = TaskStatus.OPEN,
) {
    /** Where the event sorts on [day]: its start, or midnight if it began on an earlier day. */
    fun sortTimeOn(day: LocalDate): LocalTime =
        if (start.toLocalDate() < day) LocalTime.MIDNIGHT else start.toLocalTime()

    /** "09:00", or "…" if it began on an earlier day. */
    fun startLabelOn(day: LocalDate): String =
        if (start.toLocalDate() < day) "…" else start.toLocalTime().toString()

    /** "10:00", or "…" if it goes on past [day] (ending exactly at the next midnight shows "00:00"). */
    fun endLabelOn(day: LocalDate): String =
        if (end.toLocalDate() > day && end != day.plusDays(1).atStartOfDay()) "…" else end.toLocalTime().toString()

    /** "09:00–10:00"; "…–10:00" if it began on an earlier day; "22:00–…" if it goes on past [day]. */
    fun timeLabelOn(day: LocalDate): String = "${startLabelOn(day)}–${endLabelOn(day)}"
}

/** A row of `CalendarContract.Instances`, as read; [toEventOn] turns it into an event of one day. */
data class CalendarInstance(
    val eventId: Long,
    val title: String?,
    val beginMillis: Long,
    val endMillis: Long,
    /** OPEN, or DONE / CANCELLED from the app's [EventMark]. Never written back to the calendar. */
    val status: TaskStatus = TaskStatus.OPEN,
    val allDay: Boolean,
    val color: Int?,
    val location: String?,
) {
    /**
     * The event as seen on [day] in [zone], or null if it does not touch that day.
     * All-day events are stored at UTC midnights, so their dates are read in UTC, not in [zone].
     */
    fun toEventOn(day: LocalDate, zone: ZoneId): CalendarEvent? {
        val readZone = if (allDay) ZoneOffset.UTC else zone
        val start = Instant.ofEpochMilli(beginMillis).atZone(readZone).toLocalDateTime()
        val end = Instant.ofEpochMilli(endMillis).atZone(readZone).toLocalDateTime().coerceAtLeast(start)
        val dayStart = day.atStartOfDay()
        val dayEnd = day.plusDays(1).atStartOfDay()
        // A zero-length event (a reminder-like "at 9:00") still belongs to the day it is on.
        val touches = if (start == end) start >= dayStart && start < dayEnd else start < dayEnd && end > dayStart
        if (!touches) return null
        return CalendarEvent(
            eventId = eventId,
            title = title.orEmpty(),
            allDay = allDay,
            start = start,
            end = end,
            color = color,
            location = location?.takeIf { it.isNotBlank() },
            beginMillis = beginMillis,
            endMillis = endMillis,
        )
    }
}
