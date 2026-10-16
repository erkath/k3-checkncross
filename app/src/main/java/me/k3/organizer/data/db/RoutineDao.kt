package me.k3.organizer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** SQL for `routines` and `routine_marks`. */
@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY id")
    fun observeRoutines(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routine_marks")
    fun observeMarks(): Flow<List<RoutineMarkEntity>>

    @Insert
    suspend fun insert(routine: RoutineEntity): Long

    /** Returns the number of rows updated. */
    @Update
    suspend fun update(routine: RoutineEntity): Int

    /** Returns the number of rows deleted. */
    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("DELETE FROM routine_marks WHERE routineId = :routineId")
    suspend fun deleteMarks(routineId: Long)

    /** A new mark for the same routine and period replaces the old one. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMark(mark: RoutineMarkEntity)

    @Query("DELETE FROM routine_marks WHERE routineId = :routineId AND periodStart = :periodStart")
    suspend fun clearMark(routineId: Long, periodStart: Long)
}
