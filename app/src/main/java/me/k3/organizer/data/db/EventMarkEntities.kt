package me.k3.organizer.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.k3.organizer.model.EventMark
import me.k3.organizer.model.TaskStatus

/** A row of `event_marks`: at most one mark per event occurrence. */
@Entity(tableName = "event_marks", primaryKeys = ["eventId", "beginMillis"])
data class EventMarkEntity(val eventId: Long, val beginMillis: Long, val status: String)

fun EventMarkEntity.toMark() = EventMark(eventId, beginMillis, TaskStatus.valueOf(status))

fun EventMark.toEntity() = EventMarkEntity(eventId, beginMillis, status.name)

/** SQL for `event_marks`. */
@Dao
interface EventMarkDao {
    @Query("SELECT * FROM event_marks")
    fun observeAll(): Flow<List<EventMarkEntity>>

    /** A new mark for the same occurrence replaces the old one. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(mark: EventMarkEntity)

    @Query("DELETE FROM event_marks WHERE eventId = :eventId AND beginMillis = :beginMillis")
    suspend fun clear(eventId: Long, beginMillis: Long)
}
