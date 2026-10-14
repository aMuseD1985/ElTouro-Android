package de.eltouro.app.web

/**
 * Small JSON reader (objects → Map, arrays → List, strings, numbers → Double, true/false, null). Plain JVM, so the widget
 * data can be tested without Android; org.json is only stubbed in unit tests. Throws IllegalArgumentException on bad input.
 */
object Json {
    fun parse(text: String): Any? {
        val r = Reader(text)
        val v = r.value()
        r.ws()
        require(r.i == text.length) { "trailing data at ${r.i}" }
        return v
    }

    private class Reader(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun value(): Any? {
            ws()
            require(i < s.length) { "unexpected end" }
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else throw IllegalArgumentException("unexpected '$c' at $i")
            }
        }
        fun lit(word: String, v: Any?): Any? {
            require(s.startsWith(word, i)) { "bad literal at $i" }
            i += word.length
            return v
        }
        fun num(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(start, i).toDoubleOrNull() ?: throw IllegalArgumentException("bad number at $start")
        }
        fun str(): String {
            i++   // "
            val sb = StringBuilder()
            while (true) {
                require(i < s.length) { "unterminated string" }
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        require(i < s.length) { "bad escape" }
                        when (val e = s[i++]) {
                            '"', '\\', '/' -> sb.append(e)
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C'); 'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'u' -> { require(i + 4 <= s.length) { "bad \\u" }; sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                            else -> throw IllegalArgumentException("bad escape \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }
        fun arr(): List<Any?> {
            i++
            val out = ArrayList<Any?>()
            ws()
            if (i < s.length && s[i] == ']') { i++; return out }
            while (true) {
                out += value(); ws()
                require(i < s.length) { "unterminated array" }
                if (s[i] == ',') { i++; continue }
                require(s[i] == ']') { "expected , or ] at $i" }
                i++; return out
            }
        }
        fun obj(): Map<String, Any?> {
            i++
            val out = LinkedHashMap<String, Any?>()
            ws()
            if (i < s.length && s[i] == '}') { i++; return out }
            while (true) {
                ws(); require(i < s.length && s[i] == '"') { "expected key at $i" }
                val k = str(); ws()
                require(i < s.length && s[i] == ':') { "expected : at $i" }
                i++
                out[k] = value(); ws()
                require(i < s.length) { "unterminated object" }
                if (s[i] == ',') { i++; continue }
                require(s[i] == '}') { "expected , or } at $i" }
                i++; return out
            }
        }
    }
}
