package me.k3.organizer.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** How often a routine comes back. */
enum class Frequency {
    /** Every day; a missed day is simply left unmarked. */
    DAILY,

    /** Once a week, on any day of it: open all week until marked, like a weekly backlog. */
    WEEKLY,
}

/**
 * A recurring thing to do ("play scales every day", "vacuum once a week"). It never creates
 * tasks; instead each period (a day or a week) can carry a [RoutineMark]. No mark means not done yet.
 */
data class Routine(
    val id: Long = 0,
    val title: String,
    val note: String? = null,
    val frequency: Frequency,
    /** Not shown on days before this one. */
    val createdOn: LocalDate,
    /** A paused routine is not shown anywhere; its marks are kept. */
    val active: Boolean = true,
) {
    init {
        require(title.isNotBlank()) { "title must not be blank" }
    }

    /** The period [day] belongs to: the day itself, or the first day of its week. */
    fun periodStart(day: LocalDate, firstDayOfWeek: DayOfWeek): LocalDate = when (frequency) {
        Frequency.DAILY -> day
        Frequency.WEEKLY -> day.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    }
}

/** A routine checked ([TaskStatus.DONE]) or crossed out ([TaskStatus.CANCELLED]) for one period. */
data class RoutineMark(
    val routineId: Long,
    val periodStart: LocalDate,
    val status: TaskStatus,
    /** The day the mark was made; for a weekly routine, the day it is shown on. */
    val markedOn: LocalDate,
) {
    init {
        require(routineId > 0) { "routineId must be a saved routine id" }
        require(status == TaskStatus.DONE || status == TaskStatus.CANCELLED) { "a mark is DONE or CANCELLED, not $status" }
        require(markedOn >= periodStart) { "marked on $markedOn, before its period $periodStart" }
    }
}
