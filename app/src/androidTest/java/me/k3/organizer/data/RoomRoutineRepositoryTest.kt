package me.k3.organizer.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.k3.organizer.data.db.OrganizerDatabase
import me.k3.organizer.model.Frequency
import me.k3.organizer.model.Routine
import me.k3.organizer.model.RoutineMark
import me.k3.organizer.model.TaskStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Runs on a device or emulator: `./gradlew connectedDebugAndroidTest`. */
@RunWith(AndroidJUnit4::class)
class RoomRoutineRepositoryTest {
    private val mon = LocalDate.of(2026, 10, 12)
    private lateinit var db: OrganizerDatabase
    private lateinit var repo: RoomRoutineRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, OrganizerDatabase::class.java).build()
        repo = RoomRoutineRepository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun addAndObserve() = runBlocking {
        val r = repo.add(Routine(title = "Play scales", frequency = Frequency.DAILY, createdOn = mon))
        assertEquals(listOf(r), repo.routines.first())
    }

    @Test
    fun setMark_replaces_clearMark_removes() = runBlocking {
        val r = repo.add(Routine(title = "Vacuum", frequency = Frequency.WEEKLY, createdOn = mon))
        repo.setMark(RoutineMark(r.id, mon, TaskStatus.DONE, mon.plusDays(2)))
        repo.setMark(RoutineMark(r.id, mon, TaskStatus.CANCELLED, mon.plusDays(3)))
        assertEquals(listOf(RoutineMark(r.id, mon, TaskStatus.CANCELLED, mon.plusDays(3))), repo.marks.first())

        repo.clearMark(r.id, mon)
        assertEquals(emptyList<RoutineMark>(), repo.marks.first())
    }

    @Test
    fun delete_removesMarks_unknownRejected() = runBlocking<Unit> {
        val r = repo.add(Routine(title = "Play scales", frequency = Frequency.DAILY, createdOn = mon))
        repo.setMark(RoutineMark(r.id, mon, TaskStatus.DONE, mon))
        repo.delete(r.id)
        assertEquals(emptyList<RoutineMark>(), repo.marks.first())
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.delete(r.id) } }
    }
}
