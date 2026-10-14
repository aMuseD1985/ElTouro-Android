package de.eltouro.app.diag

/**
 * What the loaded page itself reports: its path, whether assets/native_tracker.js sees the app bridge (otherwise
 * the page records in the browser, which Android pauses with the display off) and whether the rider is signed in.
 * Asked with [SCRIPT] via evaluateJavascript – no change to the web app needed. Plain JVM.
 */
data class BridgeProbe(val path: String, val hasNative: Boolean, val available: Boolean, val capacitor: Boolean, val signedIn: Boolean?, val csrf: String? = null) {
    /** Ride mode (/tour/<id>/go) or free ride (/free) – the pages that record */
    val ridePage: Boolean get() = RIDE.matches(path)

    fun summary(): String = "$path bridge=${if (available) "yes" else "no"} capacitor=${if (capacitor) "yes" else "no"}" +
        " tracker=${if (hasNative) "yes" else "no"} user=${when (signedIn) { true -> "yes"; false -> "no"; null -> "?" }}"

    companion object {
        const val SCRIPT = "(function(){try{var C=window.Capacitor,N=window.ElTouroNative,m=document.getElementById('app-texts');" +
            "return JSON.stringify({p:location.pathname,n:!!N,a:!!(N&&N.available&&N.available())," +
            "c:!!(C&&C.isNativePlatform&&C.isNativePlatform()),u:m?m.dataset.user==='1':null,k:m&&m.dataset.csrf||null});}catch(e){return null;}})()"

        private val RIDE = Regex("^/(tour/\\d+/go|free)/?$")

        /** [raw] is what evaluateJavascript hands back: the JSON string, itself JSON-encoded ("\"{\\\"p\\\":…}\""). */
        fun parse(raw: String?): BridgeProbe? {
            if (raw == null) return null
            var s = raw.trim()
            if (s == "null" || s.isEmpty()) return null
            if (s.length >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
                s = s.substring(1, s.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
            }
            val path = Regex("\"p\":\"((?:[^\"\\\\]|\\\\.)*)\"").find(s)?.groupValues?.get(1) ?: return null
            fun flag(k: String) = Regex("\"$k\":(true|false|null)").find(s)?.groupValues?.get(1)
            val csrf = Regex("\"k\":\"([A-Za-z0-9_\\-]{16,128})\"").find(s)?.groupValues?.get(1)
            return BridgeProbe(path, flag("n") == "true", flag("a") == "true", flag("c") == "true",
                when (flag("u")) { "true" -> true; "false" -> false; else -> null }, csrf)
        }
    }
}
