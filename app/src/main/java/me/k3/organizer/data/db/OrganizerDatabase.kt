package me.k3.organizer.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The app's SQLite database. Bump [version] and add a migration whenever a table changes;
 * the schema of every version is exported to `app/schemas/` (see SchemaHistoryTest).
 *
 * Versions: 1 tasks; 2 + routines and routine_marks (new tables only, so an AutoMigration).
 */
@Database(
    entities = [TaskEntity::class, RoutineEntity::class, RoutineMarkEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class OrganizerDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun routineDao(): RoutineDao

    companion object {
        fun build(context: Context): OrganizerDatabase =
            Room.databaseBuilder(context, OrganizerDatabase::class.java, "organizer.db").build()
    }
}
