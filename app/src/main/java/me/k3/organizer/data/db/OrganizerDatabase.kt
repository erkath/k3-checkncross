package me.k3.organizer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The app's SQLite database. Bump [version] and add a migration whenever a table changes;
 * the schema of every version is exported to `app/schemas/`.
 */
@Database(entities = [TaskEntity::class], version = 1, exportSchema = true)
abstract class OrganizerDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        fun build(context: Context): OrganizerDatabase =
            Room.databaseBuilder(context, OrganizerDatabase::class.java, "organizer.db").build()
    }
}
