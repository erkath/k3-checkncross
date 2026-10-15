package me.k3.organizer.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.k3.organizer.data.db.OrganizerDatabase
import me.k3.organizer.model.Task
import me.k3.organizer.model.TaskStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/** Runs on a device or emulator: `./gradlew connectedDebugAndroidTest`. */
@RunWith(AndroidJUnit4::class)
class RoomTaskRepositoryTest {
    private val mon = LocalDate.of(2026, 10, 12)
    private val tue = mon.plusDays(1)

    private lateinit var db: OrganizerDatabase
    private lateinit var repo: RoomTaskRepository
    private lateinit var actions: TaskActions

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, OrganizerDatabase::class.java).build()
        repo = RoomTaskRepository(db)
        actions = TaskActions(repo)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun add_assignsIds_andFlowEmits() = runBlocking {
        val a = repo.add(Task(title = "a", day = mon, startTime = LocalTime.of(9, 0), duration = Duration.ofHours(1)))
        val b = repo.add(Task(title = "b"))
        assertEquals(listOf(a, b), repo.tasks.first())
        assertEquals(listOf(a, b), repo.all())
    }

    @Test
    fun update_replacesTask() = runBlocking {
        val a = repo.add(Task(title = "a", day = mon))
        repo.update(a.id) { it.markDone() }
        assertEquals(TaskStatus.DONE, repo.all().single().status)
    }

    @Test
    fun update_failingTransform_changesNothing() = runBlocking<Unit> {
        val a = repo.add(Task(title = "a", day = mon))
        assertThrows(IllegalStateException::class.java) { runBlocking { repo.update(a.id) { it.reopen() } } }
        assertEquals(listOf(a), repo.all())
    }

    @Test
    fun delete_unlinksCopies() = runBlocking {
        val a = repo.add(Task(title = "a", day = mon))
        val copy = actions.move(a.id, tue)
        repo.delete(a.id)
        assertEquals(listOf(copy.copy(movedFrom = null)), repo.all())
    }

    @Test
    fun unknownId_rejected() {
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.update(42) { it } } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.delete(42) } }
    }

    @Test
    fun moveAndUndo_throughRoom() = runBlocking {
        val a = repo.add(Task(title = "a", day = mon))
        actions.move(a.id, tue)
        actions.undoMove(a.id)
        assertEquals(listOf(a), repo.all())
    }

    @Test
    fun failedUndo_rollsBack() = runBlocking<Unit> {
        val a = repo.add(Task(title = "a", day = mon))
        val copy = actions.move(a.id, tue)
        repo.update(copy.id) { it.markDone() }
        val before = repo.all()

        assertThrows(IllegalStateException::class.java) { runBlocking { actions.undoMove(a.id) } }
        assertEquals(before, repo.all())
    }

    @Test
    fun failedMoveAll_rollsBack() = runBlocking<Unit> {
        val a = repo.add(Task(title = "a", day = mon))
        val b = repo.add(Task(title = "b", day = mon))
        repo.update(b.id) { it.markDone() }
        val before = repo.all()

        // a moves first, then b fails: the transaction must undo a's move too.
        assertThrows(IllegalStateException::class.java) { runBlocking { actions.moveAll(listOf(a.id, b.id), tue) } }
        assertEquals(before, repo.all())
    }
}
