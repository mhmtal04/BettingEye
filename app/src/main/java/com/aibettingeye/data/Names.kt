package com.aibettingeye.data

import java.text.Normalizer
import java.util.Locale

object Names {
    private val marks = Regex("\\p{M}+")
    private val nonAlnum = Regex("[^a-z0-9 ]")
    private val spaces = Regex(" +")
    private val noise = setOf("fk", "sk", "fc", "cf", "sc", "kulubu", "ac", "as")

    fun fold(s: String): String {
        var t = s.lowercase(Locale.ROOT)
        t = Normalizer.normalize(t, Normalizer.Form.NFD)
        t = marks.replace(t, "")
        t = t.replace('ı', 'i').replace('ø', 'o').replace('đ', 'd').replace('ł', 'l').replace("ß", "ss")
        t = nonAlnum.replace(t, " ")
        return spaces.replace(t, " ").trim()
    }

    private fun key(s: String): String {
        val f = fold(s)
        val k = f.split(' ').filter { it.isNotEmpty() && it !in noise }.joinToString(" ")
        return if (k.isEmpty()) f else k
    }

    private fun lev(a: String, b: String): Int {
        if (a == b) return 0
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            prev = cur
        }
        return prev[b.length]
    }

    private fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        val ta = a.split(' ').toSet()
        val tb = b.split(' ').toSet()
        val sub = ta.all { it in tb } || tb.all { it in ta }
        val longest = (ta + tb).maxOf { it.length }
        if (sub && (ta.size == 1 || tb.size == 1) && ta.intersect(tb).any { it.length >= 4 }) return 0.92
        if (sub && longest >= 4 && ta.intersect(tb).size >= 2) return 0.92
        val m = maxOf(a.length, b.length)
        return 1.0 - lev(a, b).toDouble() / m
    }

    /** Türkçe yazımdan kaynaktaki yazıma bilinen karşılıklar (anahtarlar key() biçiminde). */
    private val clubAlias: Map<String, List<String>> = mapOf(
        "bayern munih" to listOf("bayern munchen", "bayern munich"),
        "kolonya" to listOf("koln"),
        "borussia monchengladbach" to listOf("monchengladbach", "gladbach"),
        "monchengladbach" to listOf("borussia monchengladbach"),
        "atletico madrid" to listOf("atletico de madrid"),
        "athletic bilbao" to listOf("athletic club"),
        "inter" to listOf("internazionale"),
        "inter milan" to listOf("internazionale"),
        "psg" to listOf("paris saint germain"),
        "marsilya" to listOf("marseille"),
        "lyon" to listOf("lyonnais"),
        "olympique lyon" to listOf("lyonnais"),
        "sporting lizbon" to listOf("sporting cp"),
        "sporting" to listOf("sporting cp"),
        "psv" to listOf("psv eindhoven"),
        "basaksehir" to listOf("istanbul basaksehir"),
        "medipol basaksehir" to listOf("istanbul basaksehir"),
        "aek atina" to listOf("aek athens"),
        "wolverhampton" to listOf("wolverhampton wanderers"),
        "newcastle" to listOf("newcastle united"),
        "tottenham" to listOf("tottenham hotspur"),
        "brighton" to listOf("brighton hove albion")
    )

    fun bestClub(input: String, candidates: Collection<String>): Pair<String, Double>? {
        val k0 = key(input)
        if (k0.isEmpty()) return null
        val ks = listOf(k0) + (clubAlias[k0] ?: emptyList())
        var best: String? = null
        var bs = 0.0
        var second = 0.0
        for (c in candidates) {
            val kc = key(c)
            val s = ks.maxOf { similarity(it, kc) }
            if (s > bs) { second = bs; bs = s; best = c } else if (s > second) second = s
        }
        if (best == null || bs < 0.82) return null
        if (bs < 1.0 && second >= bs - 0.01) return null
        return best to bs
    }

    private val rawCountries = listOf(
        "Almanya" to "Germany", "Fransa" to "France", "İspanya" to "Spain", "İtalya" to "Italy",
        "İngiltere" to "England", "İskoçya" to "Scotland", "Galler" to "Wales",
        "Kuzey İrlanda" to "Northern Ireland", "İrlanda" to "Republic of Ireland",
        "İrlanda Cumhuriyeti" to "Republic of Ireland", "Portekiz" to "Portugal", "Hollanda" to "Netherlands",
        "Belçika" to "Belgium", "İsviçre" to "Switzerland", "Avusturya" to "Austria", "Polonya" to "Poland",
        "Çekya" to "Czech Republic", "Çek Cumhuriyeti" to "Czech Republic", "Slovakya" to "Slovakia",
        "Macaristan" to "Hungary", "Romanya" to "Romania", "Bulgaristan" to "Bulgaria", "Sırbistan" to "Serbia",
        "Hırvatistan" to "Croatia", "Slovenya" to "Slovenia", "Bosna Hersek" to "Bosnia and Herzegovina",
        "Karadağ" to "Montenegro", "Kuzey Makedonya" to "North Macedonia", "Arnavutluk" to "Albania",
        "Yunanistan" to "Greece", "Kosova" to "Kosovo", "Türkiye" to "Turkey|Türkiye", "Türkiye Milli" to "Turkey|Türkiye",
        "Gürcistan" to "Georgia", "Ermenistan" to "Armenia", "Azerbaycan" to "Azerbaijan", "Kazakistan" to "Kazakhstan",
        "İsrail" to "Israel", "Kıbrıs" to "Cyprus", "Malta" to "Malta", "Lüksemburg" to "Luxembourg",
        "Lihtenştayn" to "Liechtenstein", "San Marino" to "San Marino", "Andorra" to "Andorra",
        "Faroe Adaları" to "Faroe Islands", "Faroe" to "Faroe Islands", "İzlanda" to "Iceland", "Norveç" to "Norway",
        "İsveç" to "Sweden", "Danimarka" to "Denmark", "Finlandiya" to "Finland", "Estonya" to "Estonia",
        "Letonya" to "Latvia", "Litvanya" to "Lithuania", "Belarus" to "Belarus", "Beyaz Rusya" to "Belarus",
        "Ukrayna" to "Ukraine", "Moldova" to "Moldova", "Rusya" to "Russia",
        "Brezilya" to "Brazil", "Arjantin" to "Argentina", "Uruguay" to "Uruguay", "Paraguay" to "Paraguay",
        "Şili" to "Chile", "Kolombiya" to "Colombia", "Peru" to "Peru", "Ekvador" to "Ecuador", "Bolivya" to "Bolivia",
        "Venezuela" to "Venezuela", "ABD" to "United States", "Amerika Birleşik Devletleri" to "United States",
        "Meksika" to "Mexico", "Kanada" to "Canada", "Kosta Rika" to "Costa Rica", "Panama" to "Panama",
        "Jamaika" to "Jamaica", "Honduras" to "Honduras", "Haiti" to "Haiti",
        "Japonya" to "Japan", "Güney Kore" to "South Korea", "Çin" to "China", "Avustralya" to "Australia",
        "Yeni Zelanda" to "New Zealand", "İran" to "Iran", "Irak" to "Iraq", "Suudi Arabistan" to "Saudi Arabia",
        "Katar" to "Qatar", "BAE" to "United Arab Emirates", "Birleşik Arap Emirlikleri" to "United Arab Emirates",
        "Ürdün" to "Jordan", "Özbekistan" to "Uzbekistan",
        "Fas" to "Morocco", "Cezayir" to "Algeria", "Tunus" to "Tunisia", "Mısır" to "Egypt", "Senegal" to "Senegal",
        "Nijerya" to "Nigeria", "Gana" to "Ghana", "Kamerun" to "Cameroon", "Fildişi Sahili" to "Ivory Coast",
        "Mali" to "Mali", "Güney Afrika" to "South Africa", "DR Kongo" to "DR Congo"
    )

    private val countries: Map<String, String> by lazy { rawCountries.associate { fold(it.first) to it.second } }
    private val countryKeys: Set<String> by lazy {
        countries.keys + countries.values.flatMap { it.split('|') }.map { fold(it) }
    }

    fun isCountry(input: String) = fold(input) in countryKeys

    fun resolveCountry(input: String, dataset: Set<String>): String? {
        val f = fold(input)
        countries[f]?.split('|')?.forEach { if (it in dataset) return it }
        return dataset.firstOrNull { fold(it) == f }
    }
}
