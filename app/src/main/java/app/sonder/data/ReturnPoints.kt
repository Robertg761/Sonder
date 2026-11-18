package app.sonder.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where listening was before a chapter skip ([position]), and where the skip landed ([landed]). Positions are from the start of the book. */
data class ReturnPoint(val bookId:Long,val position:Long,val landed:Long)

/**
 * Remembers the place left behind by the last previous or next chapter skip, from any button, so an accidental
 * tap can be undone from the player. Kept on disk so it survives Sonder being closed.
 */
class ReturnPoints(context:Context) {
    private val prefs=context.getSharedPreferences("return_point",Context.MODE_PRIVATE)
    private val state=MutableStateFlow(load())
    val point=state.asStateFlow()

    @Synchronized fun skipped(bookId:Long,from:Long,to:Long) { set(chain(state.value,bookId,from,to)) }
    @Synchronized fun clear(expected:ReturnPoint?=state.value) { if(expected!=null && state.value==expected) set(null) }

    private fun set(point:ReturnPoint?) {
        state.value=point
        val editor=prefs.edit()
        if(point==null) editor.clear() else editor.putLong("bookId",point.bookId).putLong("position",point.position).putLong("landed",point.landed)
        editor.apply()
    }
    private fun load():ReturnPoint? = prefs.getLong("bookId",0).takeIf { it>0 }?.let { ReturnPoint(it,prefs.getLong("position",0),prefs.getLong("landed",0)) }

    companion object {
        /** Listening this far past where the last skip landed means a new skip starts a new return point. */
        const val CHAIN_MS=120_000L

        /**
         * Skips pressed in a row, such as previous twice to escape an accidental previous, keep the place from before
         * the first one. Any other skip replaces the point with the place it left.
         */
        fun chain(current:ReturnPoint?,bookId:Long,from:Long,to:Long):ReturnPoint {
            if(current!=null && current.bookId==bookId && kotlin.math.abs(from-current.landed)<=CHAIN_MS) return current.copy(landed=to)
            return ReturnPoint(bookId,from,to)
        }
    }
}
