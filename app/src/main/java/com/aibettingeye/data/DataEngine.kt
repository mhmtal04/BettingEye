package com.aibettingeye.data

import android.content.Context
import com.aibettingeye.observer.DetectedMatch
import com.aibettingeye.observer.ObserverState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object DataEngine {

    private const val INTL_URL = "https://raw.githubusercontent.com/martj42/international_results/master/results.csv"
    private const val HOUR = 3600_000L

    private data class League(val code: String, val name: String)

    private val leagues = listOf(
        League("T1", "Türkiye Süper Lig"), League("E0", "İngiltere Premier Lig"),
        League("E1", "İngiltere Championship"), League("SP1", "İspanya La Liga"),
        League("D1", "Almanya Bundesliga"), League("I1", "İtalya Serie A"),
        League("F1", "Fransa Ligue 1"), League("N1", "Hollanda Eredivisie"),
        League("P1", "Portekiz Liga"), League("B1", "Belçika Pro Lig"),
        League("G1", "Yunanistan Süper Lig"), League("SC0", "İskoçya Premiership")
    )

    private fun seasonCode(offset: Int): String {
        val now = LocalDate.now()
        val start = (if (now.monthValue >= 7) now.year else now.year - 1) - offset
        return "%02d%02d".format(start % 100, (start + 1) % 100)
    }

    suspend fun run(ctx: Context, m: DetectedMatch, force: Boolean) {
        ObserverState.bundle.value = BundleState.Loading("Veriler indiriliyor…")
        val result: BundleState = try {
            withContext(Dispatchers.IO) { collect(ctx, m, force) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BundleState.Failed("Veri alınamadı: " + (e.message ?: "bilinmeyen hata"))
        }
        ObserverState.bundle.value = result
    }

    private suspend fun collect(ctx: Context, m: DetectedMatch, force: Boolean): BundleState {
        return if (Names.isCountry(m.home) && Names.isCountry(m.away)) {
            national(ctx, m, force)
        } else {
            clubs(ctx, m, force)
        }
    }

    // ---------------- Milli takım ----------------

    private fun national(ctx: Context, m: DetectedMatch, force: Boolean): BundleState {
        val f = Http.get(ctx, INTL_URL, 12 * HOUR, force)
            ?: return BundleState.Failed("Milli maç verisi indirilemedi. İnternet bağlantını kontrol et.\nAyrıntı: " + Http.lastError)
        val all = Csv.international(f.text).sortedByDescending { it.date }
        val names = HashSet<String>()
        all.forEach { names.add(it.home); names.add(it.away) }
        val h = Names.resolveCountry(m.home, names)
        val a = Names.resolveCountry(m.away, names)
        val notes = ArrayList<String>()
        if (h == null) notes.add("Ev sahibi takım kaynakta bulunamadı: ${m.home}")
        if (a == null) notes.add("Deplasman takımı kaynakta bulunamadı: ${m.away}")
        val latest = all.firstOrNull()?.date
        if (latest != null && ChronoUnit.DAYS.between(latest, LocalDate.now()) > 14) {
            notes.add("Kaynak ${latest} tarihine kadar güncel. Sonrasında oynanan maçlar eksik olabilir.")
        }
        notes.add("Milli maçlarda korner, şut, kart ve ilk yarı skoru bu kaynakta yok.")
        val homeLast = if (h != null) all.filter { it.home == h || it.away == h }.take(10) else emptyList()
        val awayLast = if (a != null) all.filter { it.home == a || it.away == a }.take(10) else emptyList()
        val h2h = if (h != null && a != null) {
            all.filter { (it.home == h && it.away == a) || (it.home == a && it.away == h) }.take(10)
        } else emptyList()
        return BundleState.Ready(
            DataBundle(
                key = m.key, kind = "Milli takım",
                home = SideData(m.home, h, "Milli takım", homeLast),
                away = SideData(m.away, a, "Milli takım", awayLast),
                h2h = h2h,
                sources = listOf(
                    SourceInfo(
                        "Uluslararası maç sonuçları (github.com/martj42/international_results)",
                        f.fetchedAt, latest,
                        "Açık veri (CC0), topluluk bakımlı. Resmî maçlar, B takımı ve hazırlık turnuvaları dışarıda kalabilir. Güncelleme gecikmeli olabilir."
                    )
                ),
                notes = notes
            )
        )
    }

    // ---------------- Kulüp ----------------

    private data class Hit(val league: League, val team: String, val score: Double)

    private suspend fun season(ctx: Context, lg: League, season: String, force: Boolean, maxAge: Long):
        Pair<Http.Fetched?, List<MatchRecord>> = withContext(Dispatchers.IO) {
        val url = "https://www.football-data.co.uk/mmz4281/$season/${lg.code}.csv"
        val f = Http.get(ctx, url, maxAge, force)
        if (f == null) Pair(null, emptyList()) else Pair(f, Csv.footballData(f.text, lg.name))
    }

    private fun findTeam(input: String, per: List<Pair<League, List<MatchRecord>>>): Hit? {
        var best: Hit? = null
        for ((lg, recs) in per) {
            val names = HashSet<String>()
            recs.forEach { names.add(it.home); names.add(it.away) }
            val r = Names.bestClub(input, names) ?: continue
            if (best == null || r.second > best.score) best = Hit(lg, r.first, r.second)
        }
        return best
    }

    private suspend fun clubs(ctx: Context, m: DetectedMatch, force: Boolean): BundleState = coroutineScope {
        val cur = seasonCode(0)
        val fetchTimes = ArrayList<Long>()

        val curFiles = ArrayList<Pair<League, Pair<Http.Fetched?, List<MatchRecord>>>>()
        for (chunk in leagues.chunked(4)) {
            curFiles.addAll(chunk.map { lg -> async { lg to season(ctx, lg, cur, force, 6 * HOUR) } }.awaitAll())
        }
        curFiles.forEach { it.second.first?.let { f -> fetchTimes.add(f.fetchedAt) } }
        if (curFiles.all { it.second.first == null }) {
            return@coroutineScope BundleState.Failed("Kulüp verisi indirilemedi. İnternet bağlantını kontrol et.\nAyrıntı: " + Http.lastError)
        }

        var per = curFiles.map { it.first to it.second.second }
        var hHit = findTeam(m.home, per)
        var aHit = findTeam(m.away, per)

        if (hHit == null || aHit == null) {
            // sezon başında takım henüz oynamamış olabilir: önceki sezonu da tara
            val prevFiles = leagues.map { lg -> async { lg to season(ctx, lg, seasonCode(1), force, 7 * 24 * HOUR) } }.awaitAll()
            per = per.map { (lg, recs) ->
                val p = prevFiles.first { it.first == lg }.second.second
                lg to (recs + p)
            }
            if (hHit == null) hHit = findTeam(m.home, per)
            if (aHit == null) aHit = findTeam(m.away, per)
        }

        val hh = hHit
        val aa = aHit

        suspend fun leagueRecords(lg: League): List<MatchRecord> {
            val c = curFiles.first { it.first == lg }.second.second
            val p1 = season(ctx, lg, seasonCode(1), force, 7 * 24 * HOUR)
            val p2 = season(ctx, lg, seasonCode(2), force, 7 * 24 * HOUR)
            p1.first?.let { fetchTimes.add(it.fetchedAt) }
            p2.first?.let { fetchTimes.add(it.fetchedAt) }
            return (c + p1.second + p2.second).sortedByDescending { it.date }
        }

        val notes = ArrayList<String>()
        val cache = HashMap<String, List<MatchRecord>>()
        suspend fun recs(lg: League): List<MatchRecord> = cache.getOrPut(lg.code) { leagueRecords(lg) }

        var homeSide = SideData(m.home, null, "", emptyList())
        var awaySide = SideData(m.away, null, "", emptyList())
        if (hh != null) {
            val r = recs(hh.league)
            homeSide = SideData(m.home, hh.team, hh.league.name,
                r.filter { it.home == hh.team || it.away == hh.team }.take(10))
        } else notes.add("Ev sahibi takım desteklenen liglerde bulunamadı: ${m.home}")
        if (aa != null) {
            val r = recs(aa.league)
            awaySide = SideData(m.away, aa.team, aa.league.name,
                r.filter { it.home == aa.team || it.away == aa.team }.take(10))
        } else notes.add("Deplasman takımı desteklenen liglerde bulunamadı: ${m.away}")

        var h2h: List<MatchRecord> = emptyList()
        if (hh != null && aa != null) {
            if (hh.league == aa.league) {
                val r = recs(hh.league)
                h2h = r.filter {
                    (it.home == hh.team && it.away == aa.team) || (it.home == aa.team && it.away == hh.team)
                }.take(10)
            } else {
                notes.add("Takımlar farklı liglerde. Kaynak kupa ve ligler arası maçları içermediği için ikili rekabet verisi yok.")
            }
        }
        notes.add("Son maçlar yalnızca bu kaynağın kapsadığı lig maçlarından gelir. Kupa ve Avrupa maçları dahil değil.")

        val latest = (homeSide.last + awaySide.last).maxOfOrNull { it.date }
        if (latest != null && ChronoUnit.DAYS.between(latest, LocalDate.now()) > 14) {
            notes.add("Kaynaktaki en yeni maç $latest tarihli. Daha yeni maçlar eksik olabilir.")
        }

        BundleState.Ready(
            DataBundle(
                key = m.key, kind = "Kulüp",
                home = homeSide, away = awaySide, h2h = h2h,
                sources = listOf(
                    SourceInfo(
                        "football-data.co.uk",
                        fetchTimes.maxOrNull() ?: System.currentTimeMillis(), latest,
                        "Açık veri, uzun süredir yayında. Maç sonuçları genelde 1-2 gün içinde işlenir. Korner, şut ve kart verisi bazı liglerde eksik olabilir."
                    )
                ),
                notes = notes
            )
        )
    }
}
