package com.aibettingeye.observer

data class DetectedMatch(
    val home: String,
    val away: String,
    val league: String = "",
    val date: String = "",
    val time: String = "",
    val odds: List<String> = emptyList(),
    val source: String = "",
    val ts: Long = System.currentTimeMillis()
) {
    val key: String get() = home.lowercase() + "|" + away.lowercase()
}
