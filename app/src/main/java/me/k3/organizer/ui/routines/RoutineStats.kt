package me.k3.organizer.ui.routines

import me.k3.organizer.model.Frequency
import me.k3.organizer.model.Routine
import me.k3.organizer.model.RoutineMark
import me.k3.organizer.model.TaskStatus
import java.time.DayOfWeek
import java.time.LocalDate

/** How a routine has been going: [done] of the last [outOf] periods (days or weeks), and the current [streak]. */
data class RoutineStats(val done: Int, val outOf: Int, val streak: Int)

/** Periods looked at for "done X of last N": a week of days, or four weeks. */
fun statsWindow(frequency: Frequency): Int = when (frequency) {
    Frequency.DAILY -> 7
    Frequency.WEEKLY -> 4
}

/**
 * Stats of [routine] as of [today]. A period is a day (daily) or a week (weekly).
 * - The current period counts only once marked: an unticked today is not a miss yet.
 * - A crossed-out period (skipped on purpose) is neutral: not counted, and it does not break the streak.
 * - An unmarked past period counts as missed and ends the streak.
 * - Periods before the routine was created do not count at all.
 */
fun routineStats(routine: Routine, marks: List<RoutineMark>, today: LocalDate, firstDayOfWeek: DayOfWeek): RoutineStats {
    val statusOf = marks.filter { it.routineId == routine.id }.associate { it.periodStart to it.status }
    val current = routine.periodStart(today, firstDayOfWeek)
    val first = routine.periodStart(routine.createdOn, firstDayOfWeek)
    fun previous(period: LocalDate) = when (routine.frequency) {
        Frequency.DAILY -> period.minusDays(1)
        Frequency.WEEKLY -> period.minusWeeks(1)
    }

    var done = 0
    var outOf = 0
    var period = current
    repeat(statsWindow(routine.frequency)) {
        if (period < first) return@repeat
        when (statusOf[period]) {
            TaskStatus.DONE -> { done++; outOf++ }
            TaskStatus.CANCELLED -> {}
            else -> if (period != current) outOf++
        }
        period = previous(period)
    }

    var streak = 0
    period = if (statusOf[current] == null) previous(current) else current
    while (period >= first) {
        when (statusOf[period]) {
            TaskStatus.DONE -> streak++
            TaskStatus.CANCELLED -> {}
            else -> break
        }
        period = previous(period)
    }
    return RoutineStats(done, outOf, streak)
}

/** A row of the Routines list. */
data class RoutineListItem(val routine: Routine, val stats: RoutineStats)
