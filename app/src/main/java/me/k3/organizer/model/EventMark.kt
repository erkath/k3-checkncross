package me.k3.organizer.model

/**
 * The app's own check or cross on one occurrence of a calendar event. The calendar itself is never
 * changed. An occurrence is identified by its event id and start ([CalendarEvent.beginMillis]), so
 * each day of a repeating event has its own mark; if the event is moved in the calendar, the mark
 * no longer matches and the event shows as open again.
 */
data class EventMark(val eventId: Long, val beginMillis: Long, val status: TaskStatus) {
    init {
        require(status == TaskStatus.DONE || status == TaskStatus.CANCELLED) { "a mark is DONE or CANCELLED, not $status" }
    }
}

/** [events] with the status of their marks; events without a mark are OPEN. */
fun applyEventMarks(events: List<CalendarEvent>, marks: List<EventMark>): List<CalendarEvent> {
    if (marks.isEmpty()) return events
    val statusOf = marks.associate { (it.eventId to it.beginMillis) to it.status }
    return events.map { it.copy(status = statusOf[it.eventId to it.beginMillis] ?: TaskStatus.OPEN) }
}
