package com.aibettingeye.data

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object Http {
    data class Fetched(val text: String, val fetchedAt: Long, val fromCache: Boolean)

    /** Son hatanın kısa açıklaması (ekranda gösterilir). */
    @Volatile
    var lastError: String = ""

    private const val UA =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    fun get(ctx: Context, url: String, maxAgeMs: Long, force: Boolean = false): Fetched? {
        val dir = File(ctx.filesDir, "cache")
        dir.mkdirs()
        val f = File(dir, "c" + url.hashCode().toString() + ".txt")
        val now = System.currentTimeMillis()
        if (!force && f.exists() && now - f.lastModified() < maxAgeMs) {
            return Fetched(f.readText(), f.lastModified(), true)
        }
        try {
            var u = url
            for (hop in 0 until 5) {
                val c = URL(u).openConnection() as HttpURLConnection
                c.instanceFollowRedirects = false
                c.connectTimeout = 20000
                c.readTimeout = 45000
                c.setRequestProperty("User-Agent", UA)
                c.setRequestProperty("Accept", "text/csv,text/plain,*/*")
                c.setRequestProperty("Accept-Language", "tr,en;q=0.8")
                val code = c.responseCode
                if (code in 301..308) {
                    val loc = c.getHeaderField("Location")
                    c.disconnect()
                    if (loc == null) { lastError = "HTTP $code, yönlendirme adresi yok"; break }
                    u = URL(URL(u), loc).toString()
                    continue
                }
                if (code == 200) {
                    val t = c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    c.disconnect()
                    if (t.contains("<html", ignoreCase = true)) {
                        lastError = "CSV yerine web sayfası döndü (" + url.substringAfterLast('/') + ")"
                        break
                    }
                    f.writeText(t)
                    return Fetched(t, System.currentTimeMillis(), false)
                }
                lastError = "HTTP $code (" + url.substringAfterLast('/') + ")"
                c.disconnect()
                break
            }
        } catch (e: Exception) {
            lastError = e.javaClass.simpleName + ": " + (e.message ?: "")
        }
        if (f.exists()) return Fetched(f.readText(), f.lastModified(), true)
        return null
    }
}
