package com.aibettingeye.data

import java.time.LocalDate

data class MatchRecord(
    val date: LocalDate,
    val home: String,
    val away: String,
    val hg: Int,
    val ag: Int,
    val hthg: Int? = null,
    val htag: Int? = null,
    val hs: Int? = null,
    val aShots: Int? = null,
    val hst: Int? = null,
    val ast: Int? = null,
    val hc: Int? = null,
    val ac: Int? = null,
    val hy: Int? = null,
    val ay: Int? = null,
    val hr: Int? = null,
    val ar: Int? = null,
    val referee: String? = null,
    val competition: String = "",
    val neutral: Boolean = false
)

fun MatchRecord.gf(team: String) = if (home == team) hg else ag
fun MatchRecord.ga(team: String) = if (home == team) ag else hg
fun MatchRecord.res(team: String): String {
    val d = gf(team) - ga(team)
    return if (d > 0) "G" else if (d < 0) "M" else "B"
}

data class SideData(
    val inputName: String,
    val resolved: String?,
    val league: String,
    val last: List<MatchRecord>
)

data class SourceInfo(
    val name: String,
    val fetchedAt: Long,
    val latestDataDate: LocalDate?,
    val reliability: String
)

data class DataBundle(
    val key: String,
    val kind: String,
    val home: SideData,
    val away: SideData,
    val h2h: List<MatchRecord>,
    val sources: List<SourceInfo>,
    val notes: List<String>
)

sealed class BundleState {
    object Idle : BundleState()
    data class Loading(val msg: String) : BundleState()
    data class Ready(val bundle: DataBundle) : BundleState()
    data class Failed(val msg: String) : BundleState()
}
