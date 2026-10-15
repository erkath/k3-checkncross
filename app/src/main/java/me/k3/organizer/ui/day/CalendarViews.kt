package me.k3.organizer.ui.day

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.k3.organizer.R
import me.k3.organizer.model.CalendarEvent
import me.k3.organizer.ui.theme.MyOrganizerTheme
import java.time.LocalDate

/** Callbacks of the "show calendar events?" banner. */
class CalendarPromptActions(val onAllow: () -> Unit, val onDismiss: () -> Unit)

/**
 * A calendar event in the day list. No checkbox and no swipes: events belong to the calendar app,
 * which a tap opens. The colored bar sits where a task's checkbox is, so titles line up.
 */
@Composable
fun EventRow(event: CalendarEvent, day: LocalDate, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val details = listOfNotNull(event.timeLabelOn(day).takeUnless { event.allDay }, event.location)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(onClick = onOpen)
            .padding(start = 4.dp, end = 16.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.width(48.dp).fillMaxHeight().padding(vertical = 8.dp)) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(event.color?.let { Color(it) } ?: colors.primary),
            )
        }
        Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                event.title.ifEmpty { stringResource(R.string.event_no_title) },
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
            )
            if (details.isNotEmpty()) {
                Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

/** Opens the occurrence in the user's calendar app; does nothing if there is none. */
fun openInCalendar(context: Context, event: CalendarEvent) {
    val intent = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId))
        // Which occurrence of a repeating event to show.
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.beginMillis)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endMillis)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w("EventRow", "No calendar app to open event ${event.eventId}", e)
    }
}

/** Offers to show calendar events; shown until access is granted or the user says "Not now". */
@Composable
fun CalendarPromptBanner(actions: CalendarPromptActions, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_event),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    stringResource(R.string.calendar_prompt_text),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(modifier = Modifier.align(Alignment.End)) {
                TextButton(onClick = actions.onDismiss) { Text(stringResource(R.string.calendar_prompt_not_now)) }
                TextButton(onClick = actions.onAllow) { Text(stringResource(R.string.calendar_prompt_allow)) }
            }
        }
    }
}

/** Last row of the day list while the calendar is not connected; quieter than the banner, and never dismissed. */
@Composable
fun CalendarConnectRow(onConnect: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_event),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(12.dp))
        Text(
            stringResource(R.string.calendar_not_connected),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onConnect) { Text(stringResource(R.string.calendar_connect)) }
    }
}

/** Opens this app's page in the system Settings, where its permissions can be switched on. */
fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w("CalendarAccess", "No settings screen", e)
    }
}

/** The Activity behind a Compose [Context]; it may be wrapped (e.g. by the theme). */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Demo events for previews. */
fun sampleEvents(day: LocalDate): List<CalendarEvent> = listOf(
    CalendarEvent(1, "Mom's birthday", true, day.atStartOfDay(), day.plusDays(1).atStartOfDay(), 0xFFE67C73.toInt(), null, 0, 0),
    CalendarEvent(2, "Team standup", false, day.atTime(10, 0), day.atTime(10, 15), 0xFF4285F4.toInt(), "Room 4", 0, 0),
    CalendarEvent(3, "Flight to Riga", false, day.atTime(17, 30), day.atTime(19, 0), 0xFF33B679.toInt(), null, 0, 0),
)

@Preview(showBackground = true)
@Composable
private fun CalendarPromptBannerPreview() {
    MyOrganizerTheme { Surface { CalendarPromptBanner(CalendarPromptActions({}, {})) } }
}

@Preview(showBackground = true)
@Composable
private fun CalendarConnectRowPreview() {
    MyOrganizerTheme { Surface { CalendarConnectRow(onConnect = {}) } }
}
