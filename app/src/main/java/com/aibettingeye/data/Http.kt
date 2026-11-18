package com.aibettingeye.data

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object Http {
    data class Fetched(val text: String, val fetchedAt: Long, val fromCache: Boolean)

    fun get(ctx: Context, url: String, maxAgeMs: Long, force: Boolean = false): Fetched? {
        val dir = File(ctx.filesDir, "cache")
        dir.mkdirs()
        val f = File(dir, "c" + url.hashCode().toString() + ".txt")
        val now = System.currentTimeMillis()
        if (!force && f.exists() && now - f.lastModified() < maxAgeMs) {
            return Fetched(f.readText(), f.lastModified(), true)
        }
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 15000
            c.readTimeout = 40000
            c.setRequestProperty("User-Agent", "AIBettingEye/0.3 (kisisel kullanim)")
            if (c.responseCode == 200) {
                val t = c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                c.disconnect()
                f.writeText(t)
                return Fetched(t, System.currentTimeMillis(), false)
            }
            c.disconnect()
        } catch (e: Exception) {
            // ağ hatası: eski önbellek varsa onu kullan
        }
        if (f.exists()) return Fetched(f.readText(), f.lastModified(), true)
        return null
    }
}
