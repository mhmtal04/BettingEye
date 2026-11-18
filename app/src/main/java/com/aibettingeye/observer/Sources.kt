package com.aibettingeye.observer

object Sources {
    private val browsers = setOf(
        "com.android.chrome", "org.mozilla.firefox", "com.sec.android.app.sbrowser",
        "com.opera.browser", "com.brave.browser", "com.microsoft.emmx",
        "com.duckduckgo.mobile.android", "com.kiwibrowser.browser"
    )

    /** Baloncuğun görüneceği ve okumaya izin verilen uygulamalar. */
    fun isCandidate(pkg: String): Boolean {
        val p = pkg.lowercase()
        return p.contains("nesine") || p.contains("mackolik") || p.contains("iddaa") || p in browsers
    }

    fun sourceName(pkg: String): String {
        val p = pkg.lowercase()
        return when {
            p.contains("nesine") -> "Nesine"
            p.contains("mackolik") -> "Maçkolik"
            p.contains("iddaa") -> "İddaa"
            else -> "Tarayıcı"
        }
    }

    fun isIgnorable(pkg: String): Boolean {
        val p = pkg.lowercase()
        return p == "com.android.systemui" || p.contains("inputmethod") || p.contains("keyboard") ||
            p.contains("swiftkey") || p.contains("gboard")
    }
}
