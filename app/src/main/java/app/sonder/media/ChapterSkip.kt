package app.sonder.media

/**
 * Chapter-aware previous and next, shared by the notification, lock screen, headset buttons and the player screen.
 * Positions are milliseconds from the start of the book. [starts] are chapter starts in order.
 */
object ChapterSkip {
    /** Pressing previous within this long of a chapter starting goes to the chapter before, like a music player. */
    const val RESTART_GRACE_MS=3000L

    /** The start of the chapter playing, or of the one before it when the chapter has only just begun. Never further back than that. */
    fun previous(starts:List<Long>,position:Long):Long {
        val points=boundaries(starts)
        val index=points.indexOfLast { it<=position }.coerceAtLeast(0)
        return if(position-points[index]>RESTART_GRACE_MS) points[index] else points[(index-1).coerceAtLeast(0)]
    }

    /** The start of the chapter playing. Outside buttons use this, so an accidental previous costs at most the current chapter. */
    fun restart(starts:List<Long>,position:Long):Long = boundaries(starts).last { it<=position.coerceAtLeast(0) }

    /** The start of the next chapter, or null in the last one. */
    fun next(starts:List<Long>,position:Long):Long? = boundaries(starts).firstOrNull { it>position }

    // Audio before the first marked chapter counts as a chapter of its own.
    private fun boundaries(starts:List<Long>)=(starts+0L).filter { it>=0 }.distinct().sorted()
}
