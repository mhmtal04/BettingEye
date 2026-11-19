package com.aibettingeye.data

import java.time.LocalDate

object Csv {
    fun splitLine(l: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var q = false
        for (ch in l) {
            when {
                ch == '"' -> q = !q
                ch == ',' && !q -> { out.add(sb.toString()); sb.setLength(0) }
                else -> sb.append(ch)
            }
        }
        out.add(sb.toString())
        return out
    }

    fun rows(text: String): Sequence<Map<String, String>> {
        val it = text.removePrefix("\uFEFF").lineSequence().iterator()
        if (!it.hasNext()) return emptySequence()
        val header = splitLine(it.next()).map { h -> h.trim() }
        return sequence {
            while (it.hasNext()) {
                val l = it.next()
                if (l.isBlank()) continue
                val cells = splitLine(l)
                val m = HashMap<String, String>()
                for (j in header.indices) if (j < cells.size) m[header[j]] = cells[j].trim()
                yield(m)
            }
        }
    }

    fun parseDate(s: String): LocalDate? {
        return try {
            if (s.contains('/')) {
                val p = s.split('/')
                var y = p[2].toInt()
                if (y < 100) y += 2000
                LocalDate.of(y, p[1].toInt(), p[0].toInt())
            } else {
                LocalDate.parse(s)
            }
        } catch (e: Exception) {
            null
        }
    }

    /** martj42/international_results results.csv (1990 sonrası, oynanmış maçlar) */
    fun international(text: String): List<MatchRecord> =
        rows(text).mapNotNull { r ->
            val d = parseDate(r["date"] ?: return@mapNotNull null) ?: return@mapNotNull null
            if (d.year < 1990) return@mapNotNull null
            val hg = r["home_score"]?.toIntOrNull() ?: return@mapNotNull null
            val ag = r["away_score"]?.toIntOrNull() ?: return@mapNotNull null
            MatchRecord(
                date = d,
                home = r["home_team"] ?: return@mapNotNull null,
                away = r["away_team"] ?: return@mapNotNull null,
                hg = hg, ag = ag,
                competition = r["tournament"] ?: "",
                neutral = r["neutral"].equals("TRUE", ignoreCase = true)
            )
        }.toList()

    /** openfootball/football.json: {"matches":[{"date","team1","team2","score":{"ft":[a,b],"ht":[a,b]}}]} */
    fun footballJson(text: String, comp: String): List<MatchRecord> {
        return try {
            val arr = org.json.JSONObject(text).optJSONArray("matches") ?: return emptyList()
            val out = ArrayList<MatchRecord>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val sc = o.optJSONObject("score") ?: continue
                val ft = sc.optJSONArray("ft") ?: continue
                if (ft.length() < 2) continue
                val d = parseDate(o.optString("date")) ?: continue
                val h = o.optString("team1")
                val a = o.optString("team2")
                if (h.isEmpty() || a.isEmpty()) continue
                val ht = sc.optJSONArray("ht")
                val hasHt = ht != null && ht.length() >= 2
                out.add(
                    MatchRecord(
                        date = d, home = h, away = a, hg = ft.getInt(0), ag = ft.getInt(1),
                        hthg = if (hasHt) ht!!.getInt(0) else null,
                        htag = if (hasHt) ht!!.getInt(1) else null,
                        competition = comp
                    )
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    private val txtMonth = Regex("""(?i)\b(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[/ .]+(\d{1,2})(?:[/ ,.]+(\d{4}))?""")
    private val txtIso = Regex("""(\d{4})-(\d{2})-(\d{2})""")
    private val txtMatch = Regex(
        """^\s*(?:\d{1,2}[.:]\d{2}\s+)?(.+?)\s+(\d{1,2})\s*[-–]\s*(\d{1,2})(?:\s+(?:a\.?e\.?t\.?|pen\.?))?(?:\s*\((\d{1,2})\s*[-–]\s*(\d{1,2})[^)]*\))?\s+(.+?)\s*(?:@.*)?$"""
    )
    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    /** openfootball Football.TXT: tarih satırları + "Takım A  2-1 (1-0)  Takım B" satırları. */
    fun footballTxt(text: String, comp: String, startYear: Int): List<MatchRecord> {
        val out = ArrayList<MatchRecord>()
        var cur: LocalDate? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("=") || line.startsWith("#")) continue
            val mm = txtMatch.matchEntire(raw)
            if (mm != null && cur != null) {
                val h = mm.groupValues[1].trim()
                val a = mm.groupValues[6].trim()
                if (h.length in 2..40 && a.length in 2..40 && h != a) {
                    val ht1 = mm.groupValues[4].toIntOrNull()
                    val ht2 = mm.groupValues[5].toIntOrNull()
                    out.add(
                        MatchRecord(
                            date = cur, home = h, away = a,
                            hg = mm.groupValues[2].toInt(), ag = mm.groupValues[3].toInt(),
                            hthg = if (ht1 != null && ht2 != null) ht1 else null,
                            htag = if (ht1 != null && ht2 != null) ht2 else null,
                            competition = comp
                        )
                    )
                }
                continue
            }
            val iso = txtIso.find(line)
            if (iso != null) {
                cur = try {
                    LocalDate.of(iso.groupValues[1].toInt(), iso.groupValues[2].toInt(), iso.groupValues[3].toInt())
                } catch (e: Exception) { cur }
                continue
            }
            val mo = txtMonth.find(line)
            if (mo != null) {
                val month = months.indexOf(mo.groupValues[1].lowercase()) + 1
                val day = mo.groupValues[2].toInt()
                val year = mo.groupValues[3].toIntOrNull() ?: (if (month >= 7) startYear else startYear + 1)
                cur = try { LocalDate.of(year, month, day) } catch (e: Exception) { cur }
            }
        }
        return out
    }
}
