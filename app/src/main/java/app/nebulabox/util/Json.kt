package app.nebulabox.util

/**
 * A deliberately tiny JSON reader/writer.
 *
 * The app needs to emit sing-box configuration and to read fragments of
 * vmess:// payloads. A full JSON tree library costs method count and APK
 * size, so this covers objects, arrays, strings, numbers, booleans and
 * null and nothing else.
 */
object Json {

    fun escape(value: String): String {
        val sb = StringBuilder(value.length + 8)
        for (ch in value) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (ch < ' ') sb.append("\\u%04x".format(ch.code)) else sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun quote(value: String): String = "\"" + escape(value) + "\""

    /** Renders pairs as a JSON object, dropping null values. */
    fun obj(vararg pairs: Pair<String, Any?>): String {
        val parts = pairs.filter { it.second != null }
            .map { (k, v) -> quote(k) + ":" + any(v!!) }
        return parts.joinToString(",", "{", "}")
    }

    fun any(value: Any?): String = when (value) {
        null -> "null"
        is Boolean -> value.toString()
        is Number -> value.toString()
        is String -> quote(value)
        is Map<*, *> -> value.entries.joinToString(",", "{", "}") { (k, v) ->
            quote(k.toString()) + ":" + any(v)
        }
        is Iterable<*> -> value.joinToString(",", "[", "]") { any(it) }
        is Array<*> -> value.joinToString(",", "[", "]") { any(it) }
        else -> quote(value.toString())
    }

    // ------------------------------------------------------------- parsing

    fun miniMap(text: String): Map<String, Any?> {
        val value = parse(text)
        @Suppress("UNCHECKED_CAST")
        return value as? Map<String, Any?> ?: emptyMap()
    }

    fun parse(text: String): Any? = Parser(text).parseValue()

    private class Parser(private val src: String) {
        private var pos = 0

        fun parseValue(): Any? {
            skipWs()
            if (pos >= src.length) return null
            return when (src[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't', 'f' -> parseBoolean()
                'n' -> parseNull()
                else -> parseNumber()
            }
        }

        private fun parseObject(): Map<String, Any?> {
            expect('{')
            val map = LinkedHashMap<String, Any?>()
            skipWs()
            if (peek() == '}') {
                pos++
                return map
            }
            while (true) {
                skipWs()
                val key = parseString()
                skipWs()
                expect(':')
                map[key] = parseValue()
                skipWs()
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return map
                    }
                    else -> return map
                }
            }
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            val list = mutableListOf<Any?>()
            skipWs()
            if (peek() == ']') {
                pos++
                return list
            }
            while (true) {
                list.add(parseValue())
                skipWs()
                when (peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return list
                    }
                    else -> return list
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (pos < src.length) {
                val ch = src[pos++]
                when {
                    ch == '"' -> return sb.toString()
                    ch == '\\' && pos < src.length -> {
                        when (val esc = src[pos++]) {
                            '"', '\\', '/' -> sb.append(esc)
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                if (pos + 4 <= src.length) {
                                    val hex = src.substring(pos, pos + 4)
                                    pos += 4
                                    sb.append(hex.toIntOrNull(16)?.toChar() ?: '?')
                                }
                            }
                            else -> sb.append(esc)
                        }
                    }
                    else -> sb.append(ch)
                }
            }
            return sb.toString()
        }

        private fun parseNumber(): Number {
            val start = pos
            while (pos < src.length && "+-0123456789.eE".contains(src[pos])) pos++
            val text = src.substring(start, pos)
            return if (text.contains('.') || text.contains('e') || text.contains('E')) {
                text.toDoubleOrNull() ?: 0.0
            } else {
                text.toLongOrNull() ?: 0L
            }
        }

        private fun parseBoolean(): Boolean {
            return if (src.startsWith("true", pos)) {
                pos += 4; true
            } else {
                pos += 5; false
            }
        }

        private fun parseNull(): Any? {
            pos += 4
            return null
        }

        private fun peek(): Char = if (pos < src.length) src[pos] else 0.toChar()

        private fun expect(ch: Char) {
            if (pos < src.length && src[pos] == ch) pos++
        }

        private fun skipWs() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }
    }
}
