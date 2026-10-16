package me.k3.organizer.data.db

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.k3.organizer.data.RoomEventMarkRepository
import me.k3.organizer.data.RoomRoutineRepository
import me.k3.organizer.data.RoomTaskRepository
import me.k3.organizer.model.EventMark
import me.k3.organizer.model.Frequency
import me.k3.organizer.model.Routine
import me.k3.organizer.model.TaskStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Upgrades a real version-1 database file, as an installed app has it, to the current version.
 * Room checks the resulting schema against what the code expects and throws if they differ.
 *
 * The version-1 file is built from the SQL in `app/schemas/.../1.json` (no extra test library).
 * Runs on a device or emulator: `./gradlew connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "migration-test.db"

    @Before
    fun setUp() {
        context.deleteDatabase(name)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    @Test
    fun from1_keepsTasks_andAddsLaterTables() = runBlocking {
        createVersion1(withTask = "Buy milk")

        val db = Room.databaseBuilder(context, OrganizerDatabase::class.java, name).build()
        try {
            val task = RoomTaskRepository(db).all().single()
            assertEquals("Buy milk", task.title)
            assertEquals(TaskStatus.OPEN, task.status)
            assertEquals(LocalDate.of(2026, 10, 12), task.day)

            val routines = RoomRoutineRepository(db)
            val r = routines.add(Routine(title = "Play scales", frequency = Frequency.DAILY, createdOn = task.day!!))
            assertEquals(listOf(r), routines.routines.first())

            // Version 3: event marks.
            val eventMarks = RoomEventMarkRepository(db)
            val mark = EventMark(eventId = 7, beginMillis = 1_000, status = TaskStatus.DONE)
            eventMarks.set(mark)
            assertEquals(listOf(mark), eventMarks.marks.first())
        } finally {
            db.close()
        }
    }

    private fun createVersion1(withTask: String) {
        val file = context.getDatabasePath(name).also { it.parentFile?.mkdirs() }
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            // From app/schemas/me.k3.organizer.data.db.OrganizerDatabase/1.json
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `tasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                    "`note` TEXT, `status` TEXT NOT NULL, `day` INTEGER, `startMinute` INTEGER, `durationMinutes` INTEGER, " +
                    "`movedTo` INTEGER, `movedFrom` INTEGER)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_day` ON `tasks` (`day`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_movedFrom` ON `tasks` (`movedFrom`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '$VERSION_1_HASH')")
            db.execSQL(
                "INSERT INTO tasks (title, status, day) VALUES (?, 'OPEN', ?)",
                arrayOf<Any>(withTask, LocalDate.of(2026, 10, 12).toEpochDay()),
            )
            db.version = 1
        } finally {
            db.close()
        }
    }

    private companion object {
        const val VERSION_1_HASH = "eb630d9bd0881c457e11a5b829064e66"
    }
}
