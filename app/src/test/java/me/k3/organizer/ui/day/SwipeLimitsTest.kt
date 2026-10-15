package me.k3.organizer.ui.day

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeLimitsTest {
    private val limits = SwipeLimits(max = 120f, threshold = 72f, minFling = 24f, flingVelocity = 1000f)

    @Test
    fun clamp_keepsOffsetBetweenMaxAndZero() {
        assertEquals(-120f, limits.clamp(-500f))
        assertEquals(0f, limits.clamp(30f))
        assertEquals(-50f, limits.clamp(-50f))
    }

    @Test
    fun isArmed_fromThreshold() {
        assertFalse(limits.isArmed(-71f))
        assertTrue(limits.isArmed(-72f))
    }

    @Test
    fun slowRelease_firesOnlyWhenArmed() {
        assertFalse(limits.shouldFire(offset = -60f, velocity = 0f))
        assertTrue(limits.shouldFire(offset = -80f, velocity = 0f))
    }

    @Test
    fun flingLeft_firesFromMinDistance() {
        assertTrue(limits.shouldFire(offset = -30f, velocity = -1500f))
        assertFalse(limits.shouldFire(offset = -10f, velocity = -1500f))
    }

    @Test
    fun flingBackRight_cancelsEvenWhenArmed() {
        assertFalse(limits.shouldFire(offset = -100f, velocity = 1500f))
    }

    @Test
    fun inconsistentLimits_rejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SwipeLimits(max = 50f, threshold = 72f, minFling = 24f, flingVelocity = 1000f)
        }
    }
}
