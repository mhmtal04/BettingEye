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

    private data class League(
        val code: String,
        val name: String,
        val dir: String? = null,
        val files: List<String> = emptyList(),
        val json: Boolean = true
    )

    /** openfootball/football.json dosya kodları */
    private val leagues = listOf(
        League("en.1", "İngiltere Premier Lig"), League("en.2", "İngiltere Championship"),
        League("en.3", "İngiltere League One"), League("en.4", "İngiltere League Two"),
        League("de.1", "Almanya Bundesliga"), League("de.2", "Almanya 2. Bundesliga"),
        League("es.1", "İspanya La Liga"), League("es.2", "İspanya Segunda"),
        League("it.1", "İtalya Serie A"), League("it.2", "İtalya Serie B"),
        League("fr.1", "Fransa Ligue 1"), League("fr.2", "Fransa Ligue 2"),
        League("nl.1", "Hollanda Eredivisie"), League("pt.1", "Portekiz Liga"),
        League("tr.1", "Türkiye Süper Lig", dir = "turkey", files = listOf("1-superlig.txt")),
        League("tr.2", "Türkiye 1. Lig", dir = "turkey", files = listOf("2-lig1.txt"), json = false), League("be.1", "Belçika Pro Lig"),
        League("sco.1", "İskoçya Premiership"), League("gr.1", "Yunanistan Süper Lig"),
        League("at.1", "Avusturya Bundesliga")
    )

    private fun seasonLabel(offset: Int): String {
        val now = LocalDate.now()
        val start = (if (now.monthValue >= 7) now.year else now.year - 1) - offset
        return "%d-%02d".format(start, (start + 1) % 100)
    }

    private fun blockedHint(title: String, err: String): String {
        val blocked = err.contains("CertPath") || err.contains("Trust anchor") || err.contains("web sayfası")
        return if (blocked) {
            title + "\nBu veri kaynağına şu ağdan erişilemiyor. Sertifika hatası veya sayfa yönlendirmesi, operatör ya da ağ tarafında erişim engeli olabileceğini gösterir. İnternetin çalışıyor, sorun kaynağa erişimde.\nAyrıntı: " + err
        } else {
            title + " İnternet bağlantını kontrol et.\nAyrıntı: " + err
        }
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
        var fetched: Http.Fetched? = null
        var recs: List<MatchRecord> = emptyList()
        if (lg.dir != null) {
            // openfootball/europe (Football.TXT)
            val startYear = season.take(4).toIntOrNull() ?: LocalDate.now().year
            for (file in lg.files) {
                val f = Http.get(ctx, "https://raw.githubusercontent.com/openfootball/europe/master/${lg.dir}/$season/$file", maxAge, force)
                if (f != null) {
                    val r = Csv.footballTxt(f.text, lg.name, startYear)
                    if (r.isNotEmpty()) { fetched = f; recs = r; break }
                }
            }
        }
        if (recs.isEmpty() && lg.json) {
            // openfootball/football.json
            val f = Http.get(ctx, "https://raw.githubusercontent.com/openfootball/football.json/master/$season/${lg.code}.json", maxAge, force)
            if (f != null) { fetched = f; recs = Csv.footballJson(f.text, lg.name) }
        }
        Pair(fetched, recs)
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

    private suspend fun loadAll(ctx: Context, season: String, force: Boolean, maxAge: Long):
        List<Pair<League, Pair<Http.Fetched?, List<MatchRecord>>>> = coroutineScope {
        val out = ArrayList<Pair<League, Pair<Http.Fetched?, List<MatchRecord>>>>()
        for (chunk in leagues.chunked(5)) {
            out.addAll(chunk.map { lg -> async { lg to season(ctx, lg, season, force, maxAge) } }.awaitAll())
        }
        out
    }

    private suspend fun clubs(ctx: Context, m: DetectedMatch, force: Boolean): BundleState = coroutineScope {
        val fetchTimes = ArrayList<Long>()
        val curFiles = loadAll(ctx, seasonLabel(0), force, 6 * HOUR)
        curFiles.forEach { it.second.first?.let { f -> fetchTimes.add(f.fetchedAt) } }
        val anyCur = curFiles.any { it.second.first != null }

        var per = curFiles.map { it.first to it.second.second }
        var hHit = findTeam(m.home, per)
        var aHit = findTeam(m.away, per)

        var prevLoaded = false
        if (hHit == null || aHit == null) {
            // takım güncel sezon dosyasında yoksa önceki sezonu da tara
            val prevFiles = loadAll(ctx, seasonLabel(1), force, 7 * 24 * HOUR)
            prevFiles.forEach { it.second.first?.let { f -> fetchTimes.add(f.fetchedAt) } }
            prevLoaded = prevFiles.any { it.second.first != null }
            per = per.map { (lg, recs) -> lg to (recs + prevFiles.first { it.first == lg }.second.second) }
            if (hHit == null) hHit = findTeam(m.home, per)
            if (aHit == null) aHit = findTeam(m.away, per)
        }
        if (!anyCur && !prevLoaded) {
            return@coroutineScope BundleState.Failed(blockedHint("Kulüp verisi indirilemedi.", Http.lastError))
        }

        val hh = hHit
        val aa = aHit
        val notes = ArrayList<String>()
        val cache = HashMap<String, List<MatchRecord>>()

        suspend fun recs(lg: League): List<MatchRecord> {
            val hit = cache[lg.code]
            if (hit != null) return hit
            val c = curFiles.first { it.first == lg }.second.second
            val p1 = season(ctx, lg, seasonLabel(1), force, 7 * 24 * HOUR)
            val p2 = season(ctx, lg, seasonLabel(2), force, 7 * 24 * HOUR)
            p1.first?.let { fetchTimes.add(it.fetchedAt) }
            p2.first?.let { fetchTimes.add(it.fetchedAt) }
            val all = (c + p1.second + p2.second).sortedByDescending { it.date }
            cache[lg.code] = all
            fun mark(ok: Boolean) = if (ok) "var" else "yok"
            notes.add(
                "${lg.name} dosyaları: ${seasonLabel(0)} ${mark(c.isNotEmpty())}, " +
                    "${seasonLabel(1)} ${mark(p1.second.isNotEmpty())}, ${seasonLabel(2)} ${mark(p2.second.isNotEmpty())}. " +
                    "En yeni maç: " + (all.firstOrNull()?.date?.toString() ?: "-")
            )
            return all
        }

        var homeSide = SideData(m.home, null, "", emptyList())
        var awaySide = SideData(m.away, null, "", emptyList())
        if (hh != null) {
            val r = recs(hh.league)
            homeSide = SideData(m.home, hh.team, hh.league.name,
                r.filter { it.home == hh.team || it.away == hh.team }.take(10))
        } else notes.add("Ev sahibi takım kaynaktaki liglerde bulunamadı: ${m.home}")
        if (aa != null) {
            val r = recs(aa.league)
            awaySide = SideData(m.away, aa.team, aa.league.name,
                r.filter { it.home == aa.team || it.away == aa.team }.take(10))
        } else notes.add("Deplasman takımı kaynaktaki liglerde bulunamadı: ${m.away}")

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
        notes.add("Son maçlar yalnızca bu kaynağın kapsadığı lig maçlarıdır. Kupa ve Avrupa maçları dahil değil.")
        notes.add("Bu kaynakta korner, şut, kart ve hakem verisi yok.")

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
                        "openfootball/football.json ve openfootball/europe (GitHub)",
                        fetchTimes.maxOrNull() ?: System.currentTimeMillis(), latest,
                        "Açık veri (CC0), gönüllüler bakımlı, güncel sezon günde bir güncellenir. Yalnızca lig maçları ve skorlar. Bazı liglerde güncel sezon dosyası geç gelebilir."
                    )
                ),
                notes = notes
            )
        )
    }
}
