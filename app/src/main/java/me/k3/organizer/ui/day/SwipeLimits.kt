package me.k3.organizer.ui.day

enum class SwipeDirection { LEFT, RIGHT }

/**
 * Geometry of the horizontal swipe on a task row, in pixels. Distances are positive;
 * the row offset is negative when the row moves left and positive when it moves right.
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

    /** Keeps the offset within reach and out of disabled directions. */
    fun clamp(offset: Float, allowLeft: Boolean, allowRight: Boolean): Float =
        offset.coerceIn(if (allowLeft) -max else 0f, if (allowRight) max else 0f)

    /** The action that fires if the row is released here, ignoring speed. */
    fun armedDirection(offset: Float): SwipeDirection? = when {
        offset <= -threshold -> SwipeDirection.LEFT
        offset >= threshold -> SwipeDirection.RIGHT
        else -> null
    }

    /** On release: armed or flung in the direction of the offset; flinging back cancels. */
    fun release(offset: Float, velocity: Float): SwipeDirection? {
        val direction = when {
            offset < 0f -> SwipeDirection.LEFT
            offset > 0f -> SwipeDirection.RIGHT
            else -> return null
        }
        val sign = if (direction == SwipeDirection.LEFT) -1f else 1f
        val along = velocity * sign // > 0: still moving outwards
        return when {
            along <= -flingVelocity -> null
            along >= flingVelocity -> direction.takeIf { offset * sign >= minFling }
            else -> armedDirection(offset)
        }
    }
}
