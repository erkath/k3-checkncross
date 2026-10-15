package me.k3.organizer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** SQL for the `tasks` table. Room generates the implementation at build time (via KSP). */
@Dao
interface TaskDao {
    /** Re-emits whenever the table changes. */
    @Query("SELECT * FROM tasks ORDER BY id")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY id")
    suspend fun getAll(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): TaskEntity?

    /** Returns the new row id. */
    @Insert
    suspend fun insert(task: TaskEntity): Long

    /** Returns the number of rows updated. */
    @Update
    suspend fun update(task: TaskEntity): Int

    /** Returns the number of rows deleted. */
    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("UPDATE tasks SET movedFrom = NULL WHERE movedFrom = :id")
    suspend fun unlinkMovedFrom(id: Long)
}
