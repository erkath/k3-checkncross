package me.k3.organizer.ui.day

/**
 * Geometry of the swipe-left gesture, in pixels. Distances are positive;
 * the row offset is negative because the row moves to the left.
 */
data class SwipeLimits(
    /** How far the row can be dragged. */
    val max: Float,
    /** Releasing beyond this distance fires the action. */
    val threshold: Float,
    /** A fast fling fires the action if the row has moved at least this far. */
    val minFling: Float,
    /** Speed (px/s) that counts as a fling. */
    val flingVelocity: Float,
) {
    init {
        require(0 < minFling && minFling <= threshold && threshold <= max) { "need 0 < minFling <= threshold <= max" }
    }

    fun clamp(offset: Float): Float = offset.coerceIn(-max, 0f)

    fun isArmed(offset: Float): Boolean = offset <= -threshold

    /** On release: fire when armed or flung left; flinging back to the right cancels. */
    fun shouldFire(offset: Float, velocity: Float): Boolean = when {
        velocity >= flingVelocity -> false
        velocity <= -flingVelocity -> offset <= -minFling
        else -> isArmed(offset)
    }
}
