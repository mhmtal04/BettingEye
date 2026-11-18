package com.aibettingeye.observer

import java.util.Locale

object MatchParser {

    data class Result(val match: DetectedMatch?, val pairCount: Int, val note: String)

    private val tr = Locale("tr", "TR")
    private val inline = Regex("""^(.{2,32}?)\s+(?:-|–|—|vs\.?|v)\s+(.{2,32})$""", RegexOption.IGNORE_CASE)
    private val sepTokens = setOf("-", "–", "—", "vs", "vs.", "v")
    private val oddRe = Regex("""^\d{1,2}[.,]\d{2}$""")
    private val dateRe = Regex("""\b\d{1,2}[./]\d{1,2}[./]\d{2,4}\b""")
    private val timeRe = Regex("""\b(?:[01]?\d|2[0-3]):[0-5]\d\b""")
    private val ws = Regex("""\s+""")
    private val url = Regex("""https?://\S+""")

    private val stopSides = setOf(
        "maç sonucu", "çifte şans", "ilk yarı", "iy/ms", "alt", "üst", "var", "yok",
        "tümü", "canlı", "bülten", "futbol", "ev sahibi", "deplasman", "beraberlik",
        "iy", "ms", "kg", "canli", "oran", "oranlar", "detay", "istatistik"
    )

    private fun clean(l: String) = ws.replace(url.replace(l, " "), " ").trim()
    private fun norm(s: String) = s.trim().lowercase(tr)

    private fun isTeam(raw: String): Boolean {
        val s = raw.trim()
        if (s.length !in 2..32) return false
        if (s.any { it == ':' || it == '%' || it == '@' || it == '/' }) return false
        if (s.count { it.isLetter() } < 2) return false
        if (s.all { it.isDigit() || it == '.' || it == ',' || it == ' ' }) return false
        if (norm(s) in stopSides) return false
        return true
    }

    private fun extras(lines: List<String>): Triple<List<String>, String, String> {
        val odds = lines
            .filter { oddRe.matches(it) }
            .mapNotNull { it.replace(',', '.').toDoubleOrNull() }
            .filter { it in 1.01..60.0 }
            .take(6)
            .map { String.format(Locale.US, "%.2f", it) }
        var date = ""
        var time = ""
        for (l in lines) {
            if (date.isEmpty()) date = dateRe.find(l)?.value ?: ""
            if (time.isEmpty()) time = timeRe.find(l)?.value ?: ""
            if (date.isNotEmpty() && time.isNotEmpty()) break
        }
        return Triple(odds, date, time)
    }

    /** Sayfa/ekran metninden maç bulur. Ekranda tek maç varsa (veya biri baskınsa) döner. */
    fun parse(rawLines: List<String>, source: String): Result {
        val lines = rawLines.map(::clean).filter { it.isNotEmpty() }
        val teams = LinkedHashMap<String, Pair<String, String>>()
        val freq = HashMap<String, Int>()

        fun add(h: String, a: String) {
            val k = norm(h) + "|" + norm(a)
            if (!teams.containsKey(k)) teams[k] = h.trim() to a.trim()
            freq[k] = (freq[k] ?: 0) + 1
        }

        for (i in lines.indices) {
            val m = inline.matchEntire(lines[i])
            if (m != null) {
                val h = m.groupValues[1]
                val a = m.groupValues[2]
                if (isTeam(h) && isTeam(a)) add(h, a)
            }
            if (i + 2 < lines.size && lines[i + 1].lowercase() in sepTokens &&
                isTeam(lines[i]) && isTeam(lines[i + 2])
            ) {
                add(lines[i], lines[i + 2])
            }
        }

        if (teams.isEmpty()) return Result(null, 0, "Maç çifti bulunamadı")

        val key: String
        if (teams.size == 1) {
            key = teams.keys.first()
        } else {
            val sorted = freq.entries.sortedByDescending { it.value }
            if (sorted[0].value >= 2 && sorted[0].value > sorted[1].value) {
                key = sorted[0].key
            } else {
                return Result(null, teams.size, "${teams.size} maç görünüyor (liste ekranı)")
            }
        }

        val (home, away) = teams[key]!!
        val (odds, date, time) = extras(lines)
        return Result(DetectedMatch(home, away, "", date, time, odds, source), teams.size, "Maç algılandı")
    }

    /** Bir maç satırının (küçük metin grubunun) içinden maç bulur. Ayraç yoksa iki takım satırını dener. */
    fun parseRow(rawLines: List<String>, source: String): Result {
        val r = parse(rawLines, source)
        if (r.match != null || r.pairCount > 1) return r
        val lines = rawLines.map(::clean).filter { it.isNotEmpty() }
        val cands = lines
            .filter { isTeam(it) && !oddRe.matches(it) && !timeRe.containsMatchIn(it) && !dateRe.containsMatchIn(it) }
            .distinct()
        if (cands.size == 2) {
            val (odds, date, time) = extras(lines)
            return Result(DetectedMatch(cands[0], cands[1], "", date, time, odds, source), 1, "Satırdaki iki takım adı okundu")
        }
        return Result(null, 0, "Satırda maç bulunamadı (${cands.size} metin adayı)")
    }
}
