package me.k3.organizer.ui.day

import me.k3.organizer.ui.day.SwipeDirection.LEFT
import me.k3.organizer.ui.day.SwipeDirection.RIGHT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SwipeLimitsTest {
    private val limits = SwipeLimits(max = 120f, threshold = 72f, minFling = 24f, flingVelocity = 1000f)

    @Test
    fun clamp_keepsOffsetWithinMax() {
        assertEquals(-120f, limits.clamp(-500f, allowLeft = true, allowRight = true))
        assertEquals(120f, limits.clamp(500f, allowLeft = true, allowRight = true))
        assertEquals(-50f, limits.clamp(-50f, allowLeft = true, allowRight = true))
    }

    @Test
    fun clamp_blocksDisabledDirections() {
        assertEquals(0f, limits.clamp(30f, allowLeft = true, allowRight = false))
        assertEquals(0f, limits.clamp(-30f, allowLeft = false, allowRight = true))
        assertEquals(0f, limits.clamp(-30f, allowLeft = false, allowRight = false))
    }

    @Test
    fun armedDirection_fromThresholdBothWays() {
        assertNull(limits.armedDirection(-71f))
        assertEquals(LEFT, limits.armedDirection(-72f))
        assertNull(limits.armedDirection(71f))
        assertEquals(RIGHT, limits.armedDirection(72f))
    }

    @Test
    fun slowRelease_firesOnlyWhenArmed() {
        assertNull(limits.release(offset = -60f, velocity = 0f))
        assertEquals(LEFT, limits.release(offset = -80f, velocity = 0f))
        assertEquals(RIGHT, limits.release(offset = 80f, velocity = 0f))
    }

    @Test
    fun fling_firesFromMinDistance() {
        assertEquals(LEFT, limits.release(offset = -30f, velocity = -1500f))
        assertEquals(RIGHT, limits.release(offset = 30f, velocity = 1500f))
        assertNull(limits.release(offset = -10f, velocity = -1500f))
        assertNull(limits.release(offset = 10f, velocity = 1500f))
    }

    @Test
    fun flingBack_cancelsEvenWhenArmed() {
        assertNull(limits.release(offset = -100f, velocity = 1500f))
        assertNull(limits.release(offset = 100f, velocity = -1500f))
    }

    @Test
    fun noOffset_nothingFires() {
        assertNull(limits.release(offset = 0f, velocity = 1500f))
    }

    @Test
    fun inconsistentLimits_rejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SwipeLimits(max = 50f, threshold = 72f, minFling = 24f, flingVelocity = 1000f)
        }
    }
}
