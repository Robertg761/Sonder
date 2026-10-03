package app.sonder.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(val theme: String="System", val rewind: Int=10, val forward: Int=30, val smartRewind: Int=5, val skipSilence: Boolean=false, val preservePitch: Boolean=true, val grid: Boolean=true, val dailyGoal: Int=30)
class Preferences(context:Context) {
    private val prefs=context.getSharedPreferences("preferences",Context.MODE_PRIVATE)
    private val state=MutableStateFlow(Settings(prefs.getString("theme","System")!!,prefs.getInt("rewind",10),prefs.getInt("forward",30),prefs.getInt("smartRewind",5),prefs.getBoolean("skipSilence",false),prefs.getBoolean("preservePitch",true),prefs.getBoolean("grid",true),prefs.getInt("dailyGoal",30)))
    val settings=state.asStateFlow()
    fun update(s:Settings) { prefs.edit().putString("theme",s.theme).putInt("rewind",s.rewind).putInt("forward",s.forward).putInt("smartRewind",s.smartRewind).putBoolean("skipSilence",s.skipSilence).putBoolean("preservePitch",s.preservePitch).putBoolean("grid",s.grid).putInt("dailyGoal",s.dailyGoal).apply();state.value=s }
}
