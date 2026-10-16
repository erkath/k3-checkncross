package me.k3.organizer.data

import kotlinx.coroutines.runBlocking
import me.k3.organizer.model.Frequency
import me.k3.organizer.model.Routine
import me.k3.organizer.model.RoutineMark
import me.k3.organizer.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class InMemoryRoutineRepositoryTest {
    private val mon = LocalDate.of(2026, 10, 12)
    private val repo = InMemoryRoutineRepository()

    private suspend fun scales() = repo.add(Routine(title = "Play scales", frequency = Frequency.DAILY, createdOn = mon))

    @Test
    fun add_assignsIds() = runBlocking {
        val a = scales()
        val b = repo.add(Routine(title = "Vacuum", frequency = Frequency.WEEKLY, createdOn = mon))
        assertEquals(listOf(1L, 2L), listOf(a.id, b.id))
    }

    @Test
    fun setMark_replacesMarkOfSamePeriod() = runBlocking {
        val r = scales()
        repo.setMark(RoutineMark(r.id, mon, TaskStatus.DONE, mon))
        repo.setMark(RoutineMark(r.id, mon, TaskStatus.CANCELLED, mon))
        repo.setMark(RoutineMark(r.id, mon.plusDays(1), TaskStatus.DONE, mon.plusDays(1)))

        assertEquals(
            listOf(TaskStatus.CANCELLED, TaskStatus.DONE),
            repo.marks.value.sortedBy { it.periodStart }.map { it.status },
        )
    }

    @Test
    fun clearMark_onlyThatPeriod() = runBlocking {
        val r = scales()
        repo.setMark(RoutineMark(r.id, mon, TaskStatus.DONE, mon))
        repo.setMark(RoutineMark(r.id, mon.plusDays(1), TaskStatus.DONE, mon.plusDays(1)))
        repo.clearMark(r.id, mon)
        assertEquals(listOf(mon.plusDays(1)), repo.marks.value.map { it.periodStart })
    }

    @Test
    fun delete_removesMarksToo() = runBlocking {
        val r = scales()
        repo.setMark(RoutineMark(r.id, mon, TaskStatus.DONE, mon))
        repo.delete(r.id)
        assertEquals(emptyList<Routine>(), repo.routines.value)
        assertEquals(emptyList<RoutineMark>(), repo.marks.value)
    }

    @Test
    fun update_replaces_unknownRejected() = runBlocking<Unit> {
        val r = scales()
        repo.update(r.copy(active = false))
        assertEquals(false, repo.routines.value.single().active)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.update(r.copy(id = 99)) } }
    }
}
