package com.aibettingeye.observer

import android.content.Context
import android.content.SharedPreferences
import com.aibettingeye.data.BundleState
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class DebugInfo(
    val lastPackage: String = "",
    val source: String = "",
    val note: String = "Henüz bir şey okunmadı",
    val lines: List<String> = emptyList()
)

object ObserverState {
    val detected = MutableStateFlow<DetectedMatch?>(null)
    val selected = MutableStateFlow<DetectedMatch?>(null)
    val history = MutableStateFlow<List<DetectedMatch>>(emptyList())
    val debug = MutableStateFlow(DebugInfo())
    val shareNote = MutableStateFlow("")
    val resumeTick = MutableStateFlow(0)
    val bubbleEnabled = MutableStateFlow(false)
    val bundle = MutableStateFlow<BundleState>(BundleState.Idle)

    fun onDetected(m: DetectedMatch) {
        detected.value = m
        Store.add(m)
    }
}

object Store {
    private var prefs: SharedPreferences? = null

    fun init(ctx: Context) {
        if (prefs == null) {
            prefs = ctx.applicationContext.getSharedPreferences("eye", Context.MODE_PRIVATE)
            ObserverState.history.value = load()
            ObserverState.bubbleEnabled.value = prefs?.getBoolean("bubble", false) ?: false
        }
    }

    fun setBubble(on: Boolean) {
        ObserverState.bubbleEnabled.value = on
        prefs?.edit()?.putBoolean("bubble", on)?.apply()
    }

    private fun load(): List<DetectedMatch> {
        val s = prefs?.getString("history", null) ?: return emptyList()
        return try {
            val a = JSONArray(s)
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                DetectedMatch(
                    home = o.getString("home"),
                    away = o.getString("away"),
                    league = o.optString("league"),
                    date = o.optString("date"),
                    time = o.optString("time"),
                    odds = o.optString("odds").split("|").filter { x -> x.isNotEmpty() },
                    source = o.optString("source"),
                    ts = o.optLong("ts")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(m: DetectedMatch) {
        save((listOf(m) + ObserverState.history.value.filterNot { it.key == m.key }).take(50))
    }

    fun remove(m: DetectedMatch) {
        if (ObserverState.detected.value?.key == m.key) ObserverState.detected.value = null
        save(ObserverState.history.value.filterNot { it.key == m.key })
    }

    fun clearAll() {
        ObserverState.detected.value = null
        save(emptyList())
    }

    private fun save(list: List<DetectedMatch>) {
        ObserverState.history.value = list
        val a = JSONArray()
        list.forEach {
            a.put(
                JSONObject()
                    .put("home", it.home).put("away", it.away).put("league", it.league)
                    .put("date", it.date).put("time", it.time)
                    .put("odds", it.odds.joinToString("|"))
                    .put("source", it.source).put("ts", it.ts)
            )
        }
        prefs?.edit()?.putString("history", a.toString())?.apply()
    }
}
