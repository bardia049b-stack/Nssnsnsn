package app.nebulabox.config

import app.nebulabox.util.AppLogger
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.Locale

object GeoCatalog {

    private const val TAG = "GeoCatalog"

    private val SHIPPED_GEOSITE = setOf(
        "CATEGORY-RU", "GEOLOCATION-CN", "CATEGORY-FINANCE", "GOOGLE-PLAY", "TELEGRAM", "NETFLIX", "CATEGORY-PUBLIC-TRACKER", "CATEGORY-ADS",
        "GOOGLE", "CATEGORY-CRYPTOCURRENCY", "TWITTER", "MICROSOFT", "YOUTUBE", "PRIVATE", "CATEGORY-IR", "ADOBE",
        "GFW", "DISCORD", "APPLE", "SPOTIFY", "CATEGORY-ADS-IR", "OPENAI", "BILIBILI", "TIKTOK",
        "CN", "AMAZON", "FACEBOOK", "GREATFIRE", "CLOUDFLARE", "CATEGORY-DEV", "INSTAGRAM", "GITHUB",
        "CATEGORY-FORUMS", "WHATSAPP", "SPEEDTEST", "CATEGORY-ADS-ALL",
    )

    private val SHIPPED_GEOIP = setOf(
        "AE", "AF", "AM", "AZ", "BG", "BH", "BY", "CN",
        "CY", "FACEBOOK", "FASTLY", "GE", "GOOGLE", "GR", "IL", "IQ",
        "IR", "KG", "KR", "KW", "KZ", "MD", "MY", "NETFLIX",
        "OM", "PK", "PRIVATE", "QA", "RO", "RU", "SA", "TELEGRAM",
        "TH", "TJ", "TM", "TR", "TWITTER", "UA", "UZ", "VN",
    )

    private val CN_PRIVATE_FILE = setOf("CN", "PRIVATE")

    fun sanitize(root: JsonObject): Int {
        var dropped = 0

        val rules = root.getAsJsonObject("routing")?.getAsJsonArray("rules")
        if (rules != null) {
            val emptied = mutableListOf<JsonObject>()
            for (element in rules) {
                val rule = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                var touched = 0
                rule.getAsJsonArray("domain")?.let { touched += filter(it, ::isKnownDomain) }
                rule.getAsJsonArray("ip")?.let { touched += filter(it, ::isKnownIp) }
                dropped += touched
                if (touched > 0 && rule.hasNoMatcher()) emptied.add(rule)
            }
            emptied.forEach { rules.remove(it) }
            dropped += emptied.size
        }

        val servers = root.getAsJsonObject("dns")?.getAsJsonArray("servers")
        if (servers != null) {
            for (element in servers) {
                val server = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                server.getAsJsonArray("domains")?.let { dropped += filter(it, ::isKnownDomain) }
                server.getAsJsonArray("expectIPs")?.let { dropped += filter(it, ::isKnownIp) }
            }
        }

        if (dropped > 0) {
            AppLogger.w(TAG, "Dropped $dropped geodata reference(s) missing from this build")
        }
        return dropped
    }

    fun stripAll(root: JsonObject): Int = collect(root) { true }

    private fun collect(root: JsonObject, drop: (String) -> Boolean): Int {
        var dropped = 0
        val rules = root.getAsJsonObject("routing")?.getAsJsonArray("rules")
        if (rules != null) {
            val emptied = mutableListOf<JsonObject>()
            for (element in rules) {
                val rule = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                var touched = 0
                rule.getAsJsonArray("domain")?.let { touched += purge(it, drop) }
                rule.getAsJsonArray("ip")?.let { touched += purge(it, drop) }
                dropped += touched
                if (touched > 0 && rule.hasNoMatcher()) emptied.add(rule)
            }
            emptied.forEach { rules.remove(it) }
            dropped += emptied.size
        }
        val servers = root.getAsJsonObject("dns")?.getAsJsonArray("servers")
        if (servers != null) {
            for (element in servers) {
                val server = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                server.getAsJsonArray("domains")?.let { dropped += purge(it, drop) }
                server.getAsJsonArray("expectIPs")?.let { dropped += purge(it, drop) }
            }
        }
        return dropped
    }

    private fun purge(array: JsonArray, drop: (String) -> Boolean): Int {
        val remove = mutableListOf<Int>()
        for (index in 0 until array.size()) {
            val value = array.get(index).takeIf { it.isJsonPrimitive }?.asString ?: continue
            val lowered = value.trim().lowercase(Locale.US)
            if (lowered.startsWith("geosite:") || lowered.startsWith("geoip:") || lowered.startsWith("ext:")) {
                if (drop(value)) remove.add(index)
            }
        }
        remove.asReversed().forEach { array.remove(it) }
        return remove.size
    }

    private fun filter(array: JsonArray, known: (String) -> Boolean): Int {
        val remove = mutableListOf<Int>()
        for (index in 0 until array.size()) {
            val value = array.get(index).takeIf { it.isJsonPrimitive }?.asString ?: continue
            if (!known(value)) remove.add(index)
        }
        remove.asReversed().forEach { array.remove(it) }
        return remove.size
    }

    private fun isKnownDomain(raw: String): Boolean {
        val value = raw.trim()
        return when {
            value.startsWith("geosite:", true) -> normalize(value.substringAfter(':')) in SHIPPED_GEOSITE
            value.startsWith("ext:", true) -> extCode(value, SHIPPED_GEOSITE)
            value.startsWith("ext-domain:", true) -> extCode(value, SHIPPED_GEOSITE)
            else -> true
        }
    }

    private fun isKnownIp(raw: String): Boolean {
        val value = raw.trim()
        return when {
            value.startsWith("geoip:", true) -> normalize(value.substringAfter(':')) in SHIPPED_GEOIP
            value.startsWith("ext:", true) -> extCode(value, SHIPPED_GEOIP)
            value.startsWith("ext-ip:", true) -> extCode(value, SHIPPED_GEOIP)
            else -> true
        }
    }

    private fun extCode(raw: String, shipped: Set<String>): Boolean {
        val body = raw.substringAfter(':')
        val file = body.substringBefore(':').lowercase(Locale.US)
        val code = body.substringAfter(':', "").trim()
        if (code.isBlank()) return true
        return when {
            file.contains("geosite") -> normalize(code) in SHIPPED_GEOSITE
            file.contains("geoip-only-cn-private") -> normalize(code) in CN_PRIVATE_FILE
            file.contains("geoip") -> normalize(code) in SHIPPED_GEOIP
            else -> true
        }
    }

    private fun normalize(raw: String): String = raw.trim().uppercase(Locale.US)

    private fun JsonObject.hasNoMatcher(): Boolean {
        val keys = listOf(
            "ip", "domain", "port", "sourcePort", "network", "source", "user",
            "inboundTag", "protocol", "attrs", "process", "domainMatcher",
        )
        return keys.none { key ->
            val value = get(key) ?: return@none false
            when {
                value.isJsonNull -> false
                value.isJsonArray -> value.asJsonArray.size() > 0
                value.isJsonPrimitive -> value.asString.isNotBlank()
                else -> true
            }
        }
    }
}
