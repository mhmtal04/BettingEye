package com.aibettingeye.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aibettingeye.data.BundleState
import com.aibettingeye.data.DataBundle
import com.aibettingeye.data.DataEngine
import com.aibettingeye.data.MatchRecord
import com.aibettingeye.data.SideData
import com.aibettingeye.data.ga
import com.aibettingeye.data.gf
import com.aibettingeye.data.res
import com.aibettingeye.observer.DetectedMatch
import com.aibettingeye.observer.ObserverState
import com.aibettingeye.observer.Store
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

private val tabs = listOf(
    "Analiz" to "◎",
    "Geçmiş" to "↺",
    "Kupon" to "▤",
    "Ayarlar" to "⚙"
)

private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

private fun isServiceEnabled(ctx: Context): Boolean {
    val enabled = Settings.Secure.getString(
        ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabled.split(':').any {
        it.contains("com.aibettingeye") && it.contains("EyeAccessibilityService")
    }
}

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val selected by ObserverState.selected.collectAsState()
    LaunchedEffect(selected) {
        val m = selected
        if (m != null) {
            tab = 0
            DataEngine.run(ctx.applicationContext, m, false)
        }
    }

    Scaffold(
        containerColor = Palette.Ink,
        bottomBar = {
            NavigationBar(containerColor = Palette.Panel) {
                tabs.forEachIndexed { i, (label, glyph) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Text(glyph, fontSize = 20.sp) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Palette.Amber,
                            selectedTextColor = Palette.Amber,
                            indicatorColor = Palette.PanelHigh,
                            unselectedIconColor = Palette.TextDim,
                            unselectedTextColor = Palette.TextDim
                        )
                    )
                }
            }
        }
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (tab) {
                0 -> AnalysisTab()
                1 -> HistoryTab()
                2 -> EmptyTab("Kupon", "Analiz ettiğin tahminleri kupona ekleyebileceksin. Kupon henüz boş.")
                else -> SettingsTab()
            }
        }
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Palette.Panel),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

@Composable
private fun Heading(text: String) =
    Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.Text)

@Composable
private fun Dim(text: String) =
    Text(text, fontSize = 14.sp, color = Palette.TextDim, lineHeight = 20.sp)

@Composable
private fun Label(text: String, color: Color = Palette.Teal) =
    Text(text, fontWeight = FontWeight.SemiBold, color = color)

private fun summary(m: DetectedMatch): String {
    val parts = mutableListOf<String>()
    if (m.date.isNotEmpty()) parts.add(m.date)
    if (m.time.isNotEmpty()) parts.add(m.time)
    if (m.source.isNotEmpty()) parts.add(m.source)
    return parts.joinToString("   ")
}

@Composable
private fun AnalysisTab() {
    val ctx = LocalContext.current
    val selected by ObserverState.selected.collectAsState()
    val detected by ObserverState.detected.collectAsState()
    val note by ObserverState.shareNote.collectAsState()
    val tick by ObserverState.resumeTick.collectAsState()
    val bubbleOn by ObserverState.bubbleEnabled.collectAsState()
    val enabled = remember(tick) { isServiceEnabled(ctx) }

    var home by rememberSaveable { mutableStateOf("") }
    var away by rememberSaveable { mutableStateOf("") }
    var league by rememberSaveable { mutableStateOf("") }

    Heading("AI Betting Eye")
    Dim("Seçtiğin maçı analiz eden futbol asistanı.")

    Panel {
        Label("Gözlemci")
        Text(if (enabled) "Hazır" else "Erişilebilirlik izni gerekli", fontSize = 18.sp,
            color = if (enabled) Palette.Teal else Palette.Text)
        if (!enabled) {
            Dim("Erişilebilirlik ayarlarında \"AI Betting Eye Gözlemci\"yi aç. Yalnızca Nesine, Maçkolik ve tarayıcıdaki ilgili sayfalar okunur.")
            Button(
                onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Amber, contentColor = Palette.Ink),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Erişilebilirlik ayarlarını aç") }
            OutlinedButton(
                onClick = {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Uygulama bilgisi (kısıtlı ayar için)") }
            Dim("Açamazsan: Android 13 ve üstünde dışarıdan kurulan uygulamalarda \"kısıtlı ayar\" engeli çıkar. Uygulama bilgisinde sağ üstteki ⋮ menüsünden \"Kısıtlı ayarlara izin ver\"i seç, sonra tekrar dene.")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("Baloncuğu göster", color = Palette.Text, fontSize = 16.sp)
            Switch(checked = bubbleOn, onCheckedChange = { Store.setBubble(it) })
        }
        Dim("Açıkken ◎ baloncuğu Chrome, Nesine ve Maçkolik'te hep ekranda kalır. Baloncuğu bir maç satırının üstüne sürükleyip bırak, sonra baloncuğa dokun. Analiz burada açılır.")
    }

    detected?.let { m ->
        Panel {
            Label("Son algılanan maç", Palette.Amber)
            Text("${m.home} - ${m.away}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
            val s = summary(m)
            if (s.isNotEmpty()) Dim(s)
            if (m.odds.isNotEmpty()) Dim("Ekranda görülen sayılar: " + m.odds.joinToString("   "))
            Button(
                onClick = { ObserverState.selected.value = m },
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Amber, contentColor = Palette.Ink),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Bu maçı analiz et") }
        }
    }

    if (note.isNotEmpty()) {
        Panel { Dim(note) }
    }

    selected?.let { ReportCard(it) }

    Panel {
        Label("Maçı elle gir", Palette.Amber)
        OutlinedTextField(home, { home = it }, label = { Text("Ev sahibi") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(away, { away = it }, label = { Text("Deplasman") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(league, { league = it }, label = { Text("Lig (isteğe bağlı)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = {
                ObserverState.selected.value =
                    DetectedMatch(home.trim(), away.trim(), league.trim(), source = "Elle girildi")
            },
            enabled = home.isNotBlank() && away.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Amber, contentColor = Palette.Ink),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Maçı analiz et") }
        Dim("Başka uygulamadan maçı \"Paylaş → AI Betting Eye\" ile de gönderebilirsin.")
    }
}

@Composable
private fun ReportCard(m: DetectedMatch) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by ObserverState.bundle.collectAsState()

    Panel {
        Text("${m.home} - ${m.away}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
        if (m.league.isNotEmpty()) Dim(m.league)
        val s = summary(m)
        if (s.isNotEmpty()) Dim(s)
        OutlinedButton(
            onClick = { scope.launch { DataEngine.run(ctx.applicationContext, m, true) } },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Verileri yenile") }
    }

    val st = state
    when {
        st is BundleState.Ready && st.bundle.key == m.key -> ReadyData(m, st.bundle)
        st is BundleState.Failed -> Panel {
            Label("Veri alınamadı", Palette.Warn)
            Dim(st.msg)
        }
        else -> Panel {
            Label("Veri toplama")
            Dim(if (st is BundleState.Loading) st.msg else "Veriler hazırlanıyor…")
        }
    }

    Panel {
        Label("Tahmin", Palette.Amber)
        Text("Bu karşılaşma için güvenilir tahmin oluşturulamıyor.", color = Palette.Text, fontSize = 16.sp)
        Dim("Tahmin motoru aşama 6'da eklenecek. Hiçbir tahmin kazanç garantisi vermez.")
    }
}

private fun statusColor(level: Int) = when (level) {
    2 -> Palette.Teal
    1 -> Palette.Amber
    else -> Palette.Warn
}

private fun coverage(label: String, all: List<MatchRecord>, has: (MatchRecord) -> Boolean): Triple<String, String, Int> {
    if (all.isEmpty()) return Triple(label, "Bulunamadı", 0)
    val c = all.count(has)
    return when {
        c == all.size -> Triple(label, "Mevcut ($c/${all.size})", 2)
        c > 0 -> Triple(label, "Kısmen ($c/${all.size})", 1)
        else -> Triple(label, "Bulunamadı", 0)
    }
}

private fun checkRows(m: DetectedMatch, b: DataBundle): List<Triple<String, String, Int>> {
    val rows = mutableListOf<Triple<String, String, Int>>()
    rows.add(Triple("Maç bilgisi", m.source.ifEmpty { "Alındı" }, 2))
    val hn = b.home.last.size
    val an = b.away.last.size
    rows.add(
        when {
            hn >= 10 && an >= 10 -> Triple("Son 10 maç", "Mevcut ($hn + $an)", 2)
            hn > 0 && an > 0 -> Triple("Son 10 maç", "Kısmen ($hn + $an)", 1)
            hn > 0 || an > 0 -> Triple("Son 10 maç", "Kısmen ($hn + $an)", 1)
            else -> Triple("Son 10 maç", "Bulunamadı", 0)
        }
    )
    val h = b.h2h.size
    rows.add(
        when {
            h >= 5 -> Triple("İkili rekabet (H2H)", "Mevcut ($h maç)", 2)
            h > 0 -> Triple("İkili rekabet (H2H)", "Kısmen ($h maç)", 1)
            else -> Triple("İkili rekabet (H2H)", "Bulunamadı", 0)
        }
    )
    val all = b.home.last + b.away.last
    rows.add(coverage("Korner ve şut", all) { it.hc != null && it.hs != null })
    rows.add(coverage("Kartlar", all) { it.hy != null })
    rows.add(coverage("İlk yarı skorları", all) { it.hthg != null })
    rows.add(Triple("Sakatlık ve cezalılar", "Bulunamadı", 0))
    rows.add(Triple("Muhtemel / resmî ilk 11", "Bulunamadı", 0))
    rows.add(Triple("xG / xGA", "Bulunamadı", 0))
    rows.add(Triple("Maçın hakemi", "Bulunamadı", 0))
    rows.add(
        if (m.odds.isNotEmpty()) Triple("Bahis oranları", "Okundu (doğrulanmadı)", 1)
        else Triple("Bahis oranları", "Ekranda okunmadı", 0)
    )
    return rows
}

@Composable
private fun ReadyData(m: DetectedMatch, b: DataBundle) {
    Panel {
        Label("Veri kontrolü")
        checkRows(m, b).forEach { (name, status, level) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(name, color = Palette.Text, fontSize = 14.sp)
                Text(status, color = statusColor(level), fontSize = 14.sp)
            }
        }
        b.notes.forEach { Dim(it) }
    }
    TeamPanel(b.home)
    TeamPanel(b.away)
    H2hPanel(b)
    Panel {
        Label("Kaynaklar")
        val tf = SimpleDateFormat("dd.MM HH:mm", Locale("tr", "TR"))
        b.sources.forEach { s ->
            Text(s.name, color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Dim("Veri son tarihi: " + (s.latestDataDate?.format(dateFmt) ?: "-") + "   İndirme: " + tf.format(Date(s.fetchedAt)))
            Dim("Güvenilirlik: " + s.reliability)
        }
    }
}

@Composable
private fun MatchLine(r: MatchRecord, team: String?) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (team != null) {
            val letter = r.res(team)
            Text(letter, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                color = when (letter) { "G" -> Palette.Teal; "M" -> Palette.Warn; else -> Palette.Amber })
        }
        Text("${r.date.format(dateFmt)}  ${r.home} ${r.hg}-${r.ag} ${r.away}", color = Palette.Text, fontSize = 13.sp)
    }
}

@Composable
private fun TeamPanel(side: SideData) {
    var open by remember { mutableStateOf(false) }
    Panel {
        val resolvedNote = if (side.resolved != null && side.resolved != side.inputName) " (${side.resolved})" else ""
        Label(side.inputName + resolvedNote)
        if (side.league.isNotEmpty()) Dim(side.league)
        val t = side.resolved
        if (t == null) {
            Dim("Bu takım kaynakta bulunamadı.")
        } else if (side.last.isEmpty()) {
            Dim("Bu takım için maç bulunamadı.")
        } else {
            val w = side.last.count { it.res(t) == "G" }
            val d = side.last.count { it.res(t) == "B" }
            val l = side.last.count { it.res(t) == "M" }
            val goalsFor = side.last.sumOf { it.gf(t) }
            val goalsAgainst = side.last.sumOf { it.ga(t) }
            Text("Son ${side.last.size} maç: ${w}G ${d}B ${l}M   Atılan $goalsFor   Yenilen $goalsAgainst",
                color = Palette.Text, fontSize = 14.sp)
            Dim("Form (yeniden eskiye): " + side.last.joinToString(" ") { it.res(t) })
            (if (open) side.last else side.last.take(5)).forEach { MatchLine(it, t) }
            if (side.last.size > 5) {
                Text(if (open) "Daha az göster" else "Tümünü göster", color = Palette.Amber, fontSize = 14.sp,
                    modifier = Modifier.clickable { open = !open })
            }
        }
    }
}

@Composable
private fun H2hPanel(b: DataBundle) {
    Panel {
        Label("İkili rekabet")
        if (b.h2h.isEmpty()) {
            Dim("Bu iki takımın karşılaşması kaynakta bulunamadı.")
        } else {
            val h = b.home.resolved
            if (h != null) {
                val w = b.h2h.count { it.res(h) == "G" }
                val d = b.h2h.count { it.res(h) == "B" }
                val l = b.h2h.count { it.res(h) == "M" }
                Text("Son ${b.h2h.size} maç, ${b.home.inputName} açısından: ${w}G ${d}B ${l}M",
                    color = Palette.Text, fontSize = 14.sp)
            }
            b.h2h.forEach { MatchLine(it, null) }
        }
    }
}

@Composable
private fun HistoryTab() {
    val list by ObserverState.history.collectAsState()
    var confirmAll by remember { mutableStateOf(false) }

    Heading("Geçmiş")
    if (list.isEmpty()) {
        Panel { Dim("Algıladığın veya girdiğin maçlar burada saklanır. Henüz kayıt yok.") }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Dim("${list.size} maç. Analiz için dokun.")
            TextButton(onClick = { confirmAll = true }) { Text("Tümünü sil", color = Palette.Warn) }
        }
        list.forEach { m ->
            Panel(Modifier.clickable { ObserverState.selected.value = m }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${m.home} - ${m.away}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
                        val s = summary(m)
                        if (s.isNotEmpty()) Dim(s)
                    }
                    TextButton(onClick = { Store.remove(m) }) { Text("Sil", color = Palette.Warn) }
                }
            }
        }
    }

    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            confirmButton = {
                TextButton(onClick = { Store.clearAll(); confirmAll = false }) {
                    Text("Sil", color = Palette.Warn)
                }
            },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Vazgeç") } },
            title = { Text("Geçmiş silinsin mi?") },
            text = { Text("Kayıtlı ${list.size} maç silinecek. Bu işlem geri alınamaz.") },
            containerColor = Palette.Panel,
            titleContentColor = Palette.Text,
            textContentColor = Palette.TextDim
        )
    }
}

@Composable
private fun EmptyTab(title: String, body: String) {
    Heading(title)
    Panel { Dim(body) }
}

@Composable
private fun SettingsTab() {
    val ctx = LocalContext.current
    val dbg by ObserverState.debug.collectAsState()

    Heading("Ayarlar")
    Panel {
        Label("Sürüm")
        Text("0.4.1, aşama 3: veri toplama (GitHub kaynakları)", color = Palette.Text, fontSize = 14.sp)
    }
    Panel {
        Label("Gözlemci tanılama")
        Dim("Maç algılanmazsa bu bilgiyi kopyalayıp bana gönder. Yalnızca baloncuğu bıraktığın veya dokunduğun yerdeki metin burada bellekte tutulur, kaydedilmez.")
        Text("Son görülen paket: " + dbg.lastPackage.ifEmpty { "-" }, color = Palette.Text, fontSize = 13.sp)
        Text("Kaynak: " + dbg.source.ifEmpty { "-" }, color = Palette.Text, fontSize = 13.sp)
        Text("Durum: " + dbg.note, color = Palette.Text, fontSize = 13.sp)
        OutlinedButton(
            onClick = {
                val text = "paket=${dbg.lastPackage}\nkaynak=${dbg.source}\ndurum=${dbg.note}\n---\n" +
                    dbg.lines.joinToString("\n")
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("tanilama", text))
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Tanılamayı kopyala") }
        if (dbg.lines.isNotEmpty()) {
            Text(
                dbg.lines.take(30).joinToString("\n"),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = Palette.TextDim
            )
        }
    }
    Panel {
        Label("Hakkında")
        Dim("Uygulama otomatik bahis oynamaz. Tahminler istatistiksel tahmindir ve kazanç garantisi vermez. Bahis oynamak risk içerir.")
    }
}
