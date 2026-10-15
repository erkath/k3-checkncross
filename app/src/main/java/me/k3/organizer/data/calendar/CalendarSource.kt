package me.k3.organizer.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract.Instances
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.k3.organizer.model.CalendarEvent
import me.k3.organizer.model.CalendarInstance
import java.time.LocalDate
import java.time.ZoneId

/** Events from the phone's calendars. Read-only: the app never writes to the calendar. */
interface CalendarSource {
    fun hasAccess(): Boolean

    /** Events touching [day], all-day ones first, then by start. Empty without access. */
    suspend fun eventsOn(day: LocalDate): List<CalendarEvent>
}

/** Reads `CalendarContract.Instances`: there, a repeating event is already expanded into its occurrences. */
class AndroidCalendarSource(private val context: Context) : CalendarSource {

    override fun hasAccess(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    override suspend fun eventsOn(day: LocalDate): List<CalendarEvent> {
        if (!hasAccess()) return emptyList()
        val zone = ZoneId.systemDefault()
        // One extra day on each side: all-day events sit at UTC midnights, which can fall outside
        // the local day's range. CalendarInstance.toEventOn does the exact filtering.
        val begin = day.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val rows = withContext(Dispatchers.IO) { query(begin, end) }
        return rows.mapNotNull { it.toEventOn(day, zone) }
            .sortedWith(compareBy<CalendarEvent>({ !it.allDay }, { it.sortTimeOn(day) }, { it.title }))
    }

    private fun query(beginMillis: Long, endMillis: Long): List<CalendarInstance> {
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, beginMillis)
            ContentUris.appendId(it, endMillis)
        }.build()
        val projection = arrayOf(
            Instances.EVENT_ID,
            Instances.TITLE,
            Instances.BEGIN,
            Instances.END,
            Instances.ALL_DAY,
            Instances.DISPLAY_COLOR,
            Instances.EVENT_LOCATION,
        )
        // Only calendars the user has switched on in their calendar app.
        val cursor = context.contentResolver.query(uri, projection, "${Instances.VISIBLE} = 1", null, null)
            ?: return emptyList()
        return cursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        CalendarInstance(
                            eventId = c.getLong(0),
                            title = c.getString(1),
                            beginMillis = c.getLong(2),
                            endMillis = if (c.isNull(3)) c.getLong(2) else c.getLong(3),
                            allDay = c.getInt(4) == 1,
                            color = if (c.isNull(5)) null else c.getInt(5),
                            location = c.getString(6),
                        ),
                    )
                }
            }
        }
    }
}
