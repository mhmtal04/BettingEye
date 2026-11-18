package com.aibettingeye.observer

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class EyeAccessibilityService : AccessibilityService() {

    private var currentPkg = ""
    private var lastRefresh = 0L
    private var bubble: BubbleOverlay? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Store.init(applicationContext)
        bubble = BubbleOverlay(this, ::probeAt, ::pageMatch)
        refreshForeground()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val pkg = e.packageName?.toString() ?: return
        val isState = e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (!isState && !Sources.isCandidate(pkg)) return
        val now = SystemClock.uptimeMillis()
        if (!isState && now - lastRefresh < 300) return
        lastRefresh = now
        refreshForeground()
    }

    /** Ön plandaki uygulamaya göre baloncuğu göster/gizle. Sayfa içeriği burada okunmaz. */
    private fun refreshForeground() {
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString() ?: return
        if (Sources.isIgnorable(pkg)) return
        if (pkg != currentPkg) {
            currentPkg = pkg
            ObserverState.debug.value = ObserverState.debug.value.copy(lastPackage = pkg)
        }
        val want = ObserverState.bubbleEnabled.value && Sources.isCandidate(pkg)
        if (want) bubble?.show() else bubble?.hide()
    }

    /** Ekranda (x, y) noktasındaki maç satırını bulur. Yalnızca kullanıcı baloncuğu bırakınca/dokununca çalışır. */
    private fun probeAt(x: Int, y: Int): DetectedMatch? {
        val root = rootInActiveWindow ?: return null
        val pkg = root.packageName?.toString() ?: return null
        if (!Sources.isCandidate(pkg)) return null
        val source = Sources.sourceName(pkg)
        var cur: AccessibilityNodeInfo? = deepestAt(root, x, y, 0)
        if (cur == null) {
            ObserverState.debug.value = DebugInfo(pkg, source, "Bırakılan noktada öğe bulunamadı", emptyList())
            return null
        }
        var level = 0
        var lastLines: List<String> = emptyList()
        var note = ""
        while (cur != null && level < 10) {
            val lines = ArrayList<String>()
            collect(cur, lines, 0)
            lastLines = lines
            val res = MatchParser.parseRow(lines, source)
            note = res.note
            if (res.match != null) {
                ObserverState.debug.value = DebugInfo(pkg, source, "Satır: " + res.note, lines.take(80))
                return res.match
            }
            if (res.pairCount > 1) break
            cur = cur.parent
            level++
        }
        ObserverState.debug.value = DebugInfo(pkg, source, "Satır: $note", lastLines.take(80))
        return null
    }

    private fun pageMatch(): DetectedMatch? {
        val root = rootInActiveWindow ?: return null
        val pkg = root.packageName?.toString() ?: return null
        if (!Sources.isCandidate(pkg)) return null
        val lines = ArrayList<String>()
        collect(root, lines, 0)
        val source = Sources.sourceName(pkg)
        val res = MatchParser.parse(lines, source)
        ObserverState.debug.value = DebugInfo(pkg, source, "Sayfa: " + res.note, lines.take(80))
        return res.match
    }

    private fun deepestAt(node: AccessibilityNodeInfo, x: Int, y: Int, depth: Int): AccessibilityNodeInfo? {
        if (depth > 50) return null
        if (!node.isVisibleToUser) return null
        val r = Rect()
        node.getBoundsInScreen(r)
        if (!r.contains(x, y)) return null
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            val d = deepestAt(c, x, y, depth + 1)
            if (d != null) return d
        }
        return node
    }

    private fun collect(node: AccessibilityNodeInfo, out: MutableList<String>, depth: Int) {
        if (out.size >= 700 || depth > 40) return
        val t = node.text?.toString()?.trim()
        if (!t.isNullOrEmpty()) out.add(t)
        val d = node.contentDescription?.toString()?.trim()
        if (!d.isNullOrEmpty() && d != t) out.add(d)
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            collect(c, out, depth + 1)
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        bubble?.hide()
        bubble = null
        return super.onUnbind(intent)
    }
}
