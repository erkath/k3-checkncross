package me.k3.organizer.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import me.k3.organizer.data.db.OrganizerDatabase
import me.k3.organizer.data.db.toEntity
import me.k3.organizer.data.db.toMark
import me.k3.organizer.model.EventMark

/** The app's checks and crosses on calendar events: [RoomEventMarkRepository] in the app, in memory in tests. */
interface EventMarkRepository {
    val marks: Flow<List<EventMark>>

    /** Sets the mark of its occurrence, replacing any earlier one. */
    suspend fun set(mark: EventMark)

    /** Removes the mark of one occurrence: open again. */
    suspend fun clear(eventId: Long, beginMillis: Long)
}

class RoomEventMarkRepository(db: OrganizerDatabase) : EventMarkRepository {
    private val dao = db.eventMarkDao()
    override val marks: Flow<List<EventMark>> = dao.observeAll().map { rows -> rows.map { it.toMark() } }
    override suspend fun set(mark: EventMark) = dao.put(mark.toEntity())
    override suspend fun clear(eventId: Long, beginMillis: Long) = dao.clear(eventId, beginMillis)
}

class InMemoryEventMarkRepository(initial: List<EventMark> = emptyList()) : EventMarkRepository {
    private val state = MutableStateFlow(initial)
    override val marks: StateFlow<List<EventMark>> = state.asStateFlow()

    override suspend fun set(mark: EventMark) {
        state.update { list -> list.filterNot { it.eventId == mark.eventId && it.beginMillis == mark.beginMillis } + mark }
    }

    override suspend fun clear(eventId: Long, beginMillis: Long) {
        state.update { list -> list.filterNot { it.eventId == eventId && it.beginMillis == beginMillis } }
    }
}
