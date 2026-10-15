package me.k3.organizer.ui.day

import me.k3.organizer.model.Placement
import me.k3.organizer.model.Task
import me.k3.organizer.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

class TaskFormTest {
    private val thu = LocalDate.of(2026, 10, 15)
    private val fri = thu.plusDays(1)
    private val nine = LocalTime.of(9, 0)

    @Test
    fun canSave_needsNonBlankTitle() {
        assertFalse(TaskForm().canSave)
        assertFalse(TaskForm(title = "   ").canSave)
        assertTrue(TaskForm(title = "Buy milk").canSave)
    }

    @Test
    fun toTask_untimed_trimsAndDropsEmptyNote() {
        val task = TaskForm(title = "  Buy milk ", note = "  ").toTask(thu)
        assertEquals("Buy milk", task.title)
        assertNull(task.note)
        assertEquals(0L, task.id)
        assertEquals(TaskStatus.OPEN, task.status)
        assertEquals(Placement.Untimed(thu), task.placement)
    }

    @Test
    fun toTask_timed_usesDuration() {
        val task = TaskForm(title = "Dentist", note = " card ", startTime = nine, duration = Duration.ofMinutes(30))
            .toTask(thu)
        assertEquals("card", task.note)
        assertEquals(Placement.Timed(thu, nine, Duration.ofMinutes(30)), task.placement)
    }

    @Test
    fun toTask_blankTitle_rejected() {
        assertThrows(IllegalStateException::class.java) { TaskForm(title = " ").toTask(thu) }
    }

    @Test
    fun defaultStartTime_today_isNextFullHour() {
        assertEquals(LocalTime.of(15, 0), TaskForm.defaultStartTime(thu, thu.atTime(14, 20)))
        assertEquals(LocalTime.of(15, 0), TaskForm.defaultStartTime(thu, thu.atTime(14, 0)))
    }

    @Test
    fun defaultStartTime_lateEvening_staysOnSameDay() {
        assertEquals(LocalTime.of(23, 0), TaskForm.defaultStartTime(thu, thu.atTime(23, 40)))
    }

    @Test
    fun defaultStartTime_otherDay_isNine() {
        assertEquals(nine, TaskForm.defaultStartTime(fri, thu.atTime(14, 20)))
    }

    @Test
    fun withTimeEnabled_setsDefault_andKeepsChosenTime() {
        val now = thu.atTime(14, 20)
        val on = TaskForm(title = "x").withTimeEnabled(true, thu, now)
        assertEquals(LocalTime.of(15, 0), on.startTime)

        val chosen = on.copy(startTime = nine)
        assertEquals(nine, chosen.withTimeEnabled(true, thu, now).startTime)
    }

    @Test
    fun withTimeEnabled_off_clearsTime_butKeepsDuration() {
        val form = TaskForm(title = "x", startTime = nine, duration = Duration.ofMinutes(90))
        val off = form.withTimeEnabled(false, thu, thu.atTime(8, 0))
        assertNull(off.startTime)
        assertEquals(Duration.ofMinutes(90), off.duration)
        assertEquals(Placement.Untimed(thu), off.toTask(thu).placement)
    }

    // --- editing ---

    private val dentist = Task(
        id = 7, title = "Dentist", note = "card", day = thu, startTime = nine, duration = Duration.ofMinutes(30),
    )

    @Test
    fun from_copiesFields() {
        assertEquals(TaskForm("Dentist", "card", nine, Duration.ofMinutes(30)), TaskForm.from(dentist))
        assertEquals(TaskForm("x", "", null, TaskForm.DEFAULT_DURATION), TaskForm.from(Task(title = "x")))
    }

    @Test
    fun applyTo_openTask_changesTextAndTime_keepsIdAndDay() {
        val edited = TaskForm.from(dentist).copy(title = " Dentist 2 ", note = "", startTime = null).applyTo(dentist)
        assertEquals(7L, edited.id)
        assertEquals("Dentist 2", edited.title)
        assertNull(edited.note)
        assertEquals(Placement.Untimed(thu), edited.placement)
    }

    @Test
    fun applyTo_closedTask_changesTextOnly() {
        val done = dentist.markDone()
        val edited = TaskForm.from(done).copy(title = "Dentist!", startTime = LocalTime.of(15, 0)).applyTo(done)
        assertEquals("Dentist!", edited.title)
        assertEquals(TaskStatus.DONE, edited.status)
        assertEquals(Placement.Timed(thu, nine, Duration.ofMinutes(30)), edited.placement)
    }

    @Test
    fun applyTo_blankTitle_rejected() {
        assertThrows(IllegalStateException::class.java) { TaskForm.from(dentist).copy(title = "").applyTo(dentist) }
    }

    // --- sheet state ---

    @Test
    fun sheetState_whatCanBeEdited() {
        val newTask = TaskSheetState(TaskForm())
        assertTrue(newTask.isNew)
        assertTrue(newTask.canEditTime)
        assertFalse(newTask.canMove)

        val open = TaskSheetState(TaskForm.from(dentist), dentist)
        assertTrue(open.canEditTime)
        assertTrue(open.canMove)

        val done = TaskSheetState(TaskForm.from(dentist), dentist.markDone())
        assertFalse(done.canEditTime)
        assertFalse(done.canMove)

        val backlog = Task(id = 8, title = "Idea")
        val inBacklog = TaskSheetState(TaskForm.from(backlog), backlog)
        assertFalse(inBacklog.canEditTime)
        assertFalse(inBacklog.canMove)
    }
}
