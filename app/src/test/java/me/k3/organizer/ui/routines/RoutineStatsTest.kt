package me.k3.organizer.ui.routines

import me.k3.organizer.model.Frequency
import me.k3.organizer.model.Routine
import me.k3.organizer.model.RoutineMark
import me.k3.organizer.model.TaskStatus
import me.k3.organizer.model.TaskStatus.CANCELLED
import me.k3.organizer.model.TaskStatus.DONE
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class RoutineStatsTest {
    private val today = LocalDate.of(2026, 10, 15) // Thursday
    private val monday = DayOfWeek.MONDAY
    private val scales = Routine(id = 1, title = "Play scales", frequency = Frequency.DAILY, createdOn = today.minusDays(60))
    private val vacuum = Routine(id = 2, title = "Vacuum", frequency = Frequency.WEEKLY, createdOn = today.minusDays(60))
    private val thisWeek = LocalDate.of(2026, 10, 12)

    /** Marks of [scales] for "days ago" → status. */
    private fun daily(vararg daysAgo: Pair<Int, TaskStatus>) =
        daysAgo.map { (ago, status) -> RoutineMark(1, today.minusDays(ago.toLong()), status, today.minusDays(ago.toLong())) }

    private fun weekly(vararg weeksAgo: Pair<Int, TaskStatus>) =
        weeksAgo.map { (ago, status) -> RoutineMark(2, thisWeek.minusWeeks(ago.toLong()), status, thisWeek.minusWeeks(ago.toLong())) }

    private fun stats(routine: Routine, marks: List<RoutineMark>) = routineStats(routine, marks, today, monday)

    @Test
    fun daily_everyDayDone() {
        val marks = daily(*(0..9).map { it to DONE }.toTypedArray())
        assertEquals(RoutineStats(done = 7, outOf = 7, streak = 10), stats(scales, marks))
    }

    @Test
    fun daily_todayNotTickedYet_isNotAMiss() {
        val marks = daily(1 to DONE, 2 to DONE)
        // Window: today pending (not counted), yesterday and the day before done, four missed days.
        assertEquals(RoutineStats(done = 2, outOf = 6, streak = 2), stats(scales, marks))
    }

    @Test
    fun daily_missedDayEndsStreak() {
        val marks = daily(0 to DONE, 1 to DONE, 3 to DONE, 4 to DONE)
        assertEquals(2, stats(scales, marks).streak)
    }

    @Test
    fun daily_skippedDay_neutral() {
        val marks = daily(0 to DONE, 1 to CANCELLED, 2 to DONE, 3 to DONE)
        val s = stats(scales, marks)
        assertEquals(3, s.streak)
        assertEquals(3, s.done)
        assertEquals(6, s.outOf) // the skipped day is left out
    }

    @Test
    fun daily_newRoutine_onlyDaysSinceCreation() {
        val fresh = scales.copy(createdOn = today.minusDays(1))
        assertEquals(RoutineStats(done = 1, outOf = 1, streak = 1), stats(fresh, daily(1 to DONE)))
        assertEquals(RoutineStats(done = 0, outOf = 0, streak = 0), stats(fresh.copy(createdOn = today), emptyList()))
    }

    @Test
    fun daily_marksOfOtherRoutinesIgnored() {
        val other = listOf(RoutineMark(9, today.minusDays(1), DONE, today.minusDays(1)))
        assertEquals(RoutineStats(done = 0, outOf = 6, streak = 0), stats(scales, other))
    }

    @Test
    fun weekly_lastFourWeeks_andStreakInWeeks() {
        // This week not done yet; the three before all done; four weeks ago missed.
        val marks = weekly(1 to DONE, 2 to DONE, 3 to DONE)
        assertEquals(RoutineStats(done = 3, outOf = 3, streak = 3), stats(vacuum, marks))
    }

    @Test
    fun weekly_markedThisWeek_counts() {
        val marks = weekly(0 to DONE, 1 to CANCELLED, 2 to DONE)
        assertEquals(RoutineStats(done = 2, outOf = 3, streak = 2), stats(vacuum, marks))
    }

    @Test
    fun weekly_createdMidweek_countsThatWeek() {
        val fresh = vacuum.copy(createdOn = today) // Thursday of this week
        assertEquals(RoutineStats(done = 1, outOf = 1, streak = 1), stats(fresh, weekly(0 to DONE)))
    }
}
