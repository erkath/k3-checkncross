package me.k3.organizer.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The current date for a screen's ViewModel. Not fixed at start: while [scope] lives it follows
 * midnight, and [refresh] re-reads it (call that when the screen is resumed: the device may have
 * slept through midnight). [onChange] runs after the date has changed.
 */
class TodayTracker(
    private val now: () -> LocalDateTime,
    scope: CoroutineScope,
    private val onChange: (old: LocalDate, new: LocalDate) -> Unit = { _, _ -> },
) {
    private val state = MutableStateFlow(now().toLocalDate())
    val flow: StateFlow<LocalDate> = state.asStateFlow()
    val value: LocalDate get() = state.value

    init {
        // The loop re-checks after each wait, so a late or early wake-up (device sleep,
        // a DST shift) only delays the switch, never skips it.
        scope.launch {
            while (true) {
                delay(untilNextMidnight(now()).toMillis() + 1_000)
                refresh()
            }
        }
    }

    fun refresh() {
        val old = state.value
        val new = now().toLocalDate()
        if (new == old) return
        state.value = new
        onChange(old, new)
    }
}

/** Time left until the next local midnight after [now]. */
fun untilNextMidnight(now: LocalDateTime): Duration =
    Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay())
