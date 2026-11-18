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

    /** football-data.co.uk lig dosyaları */
    fun footballData(text: String, comp: String): List<MatchRecord> =
        rows(text).mapNotNull { r ->
            val d = parseDate(r["Date"] ?: return@mapNotNull null) ?: return@mapNotNull null
            val h = r["HomeTeam"]?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val a = r["AwayTeam"]?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val hg = r["FTHG"]?.toIntOrNull() ?: return@mapNotNull null
            val ag = r["FTAG"]?.toIntOrNull() ?: return@mapNotNull null
            MatchRecord(
                date = d, home = h, away = a, hg = hg, ag = ag,
                hthg = r["HTHG"]?.toIntOrNull(), htag = r["HTAG"]?.toIntOrNull(),
                hs = r["HS"]?.toIntOrNull(), aShots = r["AS"]?.toIntOrNull(),
                hst = r["HST"]?.toIntOrNull(), ast = r["AST"]?.toIntOrNull(),
                hc = r["HC"]?.toIntOrNull(), ac = r["AC"]?.toIntOrNull(),
                hy = r["HY"]?.toIntOrNull(), ay = r["AY"]?.toIntOrNull(),
                hr = r["HR"]?.toIntOrNull(), ar = r["AR"]?.toIntOrNull(),
                referee = r["Referee"]?.takeIf { it.isNotEmpty() },
                competition = comp
            )
        }.toList()

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
}
