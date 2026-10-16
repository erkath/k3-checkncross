package me.k3.organizer.ui.routines

import me.k3.organizer.model.Frequency
import me.k3.organizer.model.Routine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RoutineFormTest {
    private val mon = LocalDate.of(2026, 10, 12)
    private val vacuum = Routine(id = 5, title = "Vacuum", note = "hallway too", frequency = Frequency.WEEKLY, createdOn = mon)

    @Test
    fun canSave_needsTitle() {
        assertFalse(RoutineForm().canSave)
        assertFalse(RoutineForm(title = "  ").canSave)
        assertTrue(RoutineForm(title = "Scales").canSave)
    }

    @Test
    fun toRoutine_trims_startsToday() {
        val r = RoutineForm(title = " Play scales ", note = " ", frequency = Frequency.DAILY).toRoutine(mon)
        assertEquals("Play scales", r.title)
        assertNull(r.note)
        assertEquals(mon, r.createdOn)
        assertEquals(0L, r.id)
        assertTrue(r.active)
    }

    @Test
    fun from_andApplyTo_keepIdAndCreation() {
        val form = RoutineForm.from(vacuum)
        assertEquals(RoutineForm("Vacuum", "hallway too", Frequency.WEEKLY, true), form)

        val edited = form.copy(title = "Vacuum + mop", frequency = Frequency.DAILY, active = false).applyTo(vacuum)
        assertEquals(5L, edited.id)
        assertEquals(mon, edited.createdOn)
        assertEquals("Vacuum + mop", edited.title)
        assertEquals(Frequency.DAILY, edited.frequency)
        assertFalse(edited.active)
    }

    @Test
    fun blankTitle_rejected() {
        assertThrows(IllegalStateException::class.java) { RoutineForm().toRoutine(mon) }
        assertThrows(IllegalStateException::class.java) { RoutineForm().applyTo(vacuum) }
    }

    @Test
    fun listOrder_activeDailyThenWeekly_pausedLast() {
        val routines = listOf(
            vacuum.copy(id = 1),
            Routine(id = 2, title = "Scales", frequency = Frequency.DAILY, createdOn = mon, active = false),
            Routine(id = 3, title = "Stretch", frequency = Frequency.DAILY, createdOn = mon),
            Routine(id = 4, title = "Walk", frequency = Frequency.DAILY, createdOn = mon),
        )
        assertEquals(listOf(3L, 4L, 1L, 2L), routineListOrder(routines).map { it.id })
    }
}
