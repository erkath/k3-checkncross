package me.k3.organizer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class EventMarkTest {
    private val day = LocalDate.of(2026, 10, 15)

    private fun event(id: Long, begin: Long) =
        CalendarEvent(id, "e$id", false, day.atTime(9, 0), day.atTime(10, 0), null, null, beginMillis = begin, endMillis = begin + 1)

    @Test
    fun onlyDoneOrCancelled() {
        EventMark(1, 100, TaskStatus.DONE)
        EventMark(1, 100, TaskStatus.CANCELLED)
        assertThrows(IllegalArgumentException::class.java) { EventMark(1, 100, TaskStatus.OPEN) }
        assertThrows(IllegalArgumentException::class.java) { EventMark(1, 100, TaskStatus.MOVED) }
    }

    @Test
    fun applyMarks_matchesByEventAndOccurrence() {
        // Event 1 repeats: two occurrences, only the first one is checked.
        val events = listOf(event(1, 100), event(1, 200), event(2, 100))
        val marks = listOf(EventMark(1, 100, TaskStatus.DONE), EventMark(2, 100, TaskStatus.CANCELLED), EventMark(3, 100, TaskStatus.DONE))

        assertEquals(
            listOf(TaskStatus.DONE, TaskStatus.OPEN, TaskStatus.CANCELLED),
            applyEventMarks(events, marks).map { it.status },
        )
    }

    @Test
    fun applyMarks_noMarks_sameList() {
        val events = listOf(event(1, 100))
        assertSame(events, applyEventMarks(events, emptyList()))
    }
}
