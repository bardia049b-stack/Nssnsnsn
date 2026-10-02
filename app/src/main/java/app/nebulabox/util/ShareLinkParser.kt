package app.nebulabox.util

import android.util.Base64
import app.nebulabox.data.Profile
import app.nebulabox.data.Protocol
import app.nebulabox.data.TlsSettings
import app.nebulabox.data.Transport
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID

/**
 * Parses the share-link formats used across the ecosystem.
 *
 * Supported: vless:// vmess:// trojan:// ss:// socks:// http(s):// hysteria2:// hy2://
 *            tuic:// wireguard:// ssh://  plus bare sing-box JSON.
 */
object ShareLinkParser {

    class ParseException(message: String) : Exception(message)

    fun looksLikeShareLink(text: String): Boolean {
        val t = text.trim()
        return listOf(
            "vless://", "vmess://", "trojan://", "ss://", "socks://", "http://",
            "https://", "hysteria2://", "hy2://", "tuic://", "wireguard://", "ssh://",
        ).any { t.startsWith(it, ignoreCase = true) } || t.startsWith("{")
    }

    /** Splits a pasted blob that may contain several links or a newline separated list. */
    fun parseMany(text: String): List<Profile> {
        val lines = text.split("\n", "\r", " ").map { it.trim() }.filter { it.isNotEmpty() }
        val out = mutableListOf<Profile>()
        for (line in lines) {
            runCatching { out.addAll(parse(line)) }
        }
        if (out.isEmpty() && text.trim().startsWith("{")) {
            runCatching { out.add(parseSingBoxJson(text)) }
        }
        return out
    }

    fun parse(link: String): List<Profile> {
        val trimmed = link.trim()
        return when {
            trimmed.startsWith("vless://", true) -> listOf(parseVless(trimmed))
            trimmed.startsWith("vmess://", true) -> listOf(parseVmess(trimmed))
            trimmed.startsWith("trojan://", true) -> listOf(parseTrojan(trimmed))
            trimmed.startsWith("ss://", true) -> parseSs(trimmed)
            trimmed.startsWith("socks://", true) -> listOf(parseSocks(trimmed))
            trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true) ->
                listOf(parseHttpProxy(trimmed))
            trimmed.startsWith("hysteria2://", true) || trimmed.startsWith("hy2://", true) ->
                listOf(parseHysteria2(trimmed))
            trimmed.startsWith("tuic://", true) -> listOf(parseTuic(trimmed))
            trimmed.startsWith("wireguard://", true) -> listOf(parseWireGuard(trimmed))
            trimmed.startsWith("ssh://", true) -> listOf(parseSsh(trimmed))
            trimmed.startsWith("{") -> listOf(parseSingBoxJson(trimmed))
            else -> throw ParseException("unsupported link: $trimmed")
        }
    }

    // ---------------------------------------------------------------- vless

    private fun parseVless(link: String): Profile {
        val rest = stripScheme(link)
        val (authority, query) = splitQuery(rest)
        val params = parseQuery(query)
        val (userInfo, hostPart) = splitUserInfo(authority)
        val uuid = decode(userInfo)
        val (host, port) = splitHostPort(hostPart)

        val type = params["type"] ?: "tcp"
        val transport = Transport(
            type = type,
            host = params["host"] ?: "",
            path = params["path"] ?: "",
            serviceName = params["serviceName"] ?: "",
            headers = params["headers"]?.let { runCatching { parseHeaders(it) }.getOrNull() }
                ?: emptyMap(),
            maxEarlyData = params["ed"]?.toIntOrNull() ?: 0,
            earlyDataHeader = params["eh"] ?: "",
        )

        val security = params["security"] ?: ""
        val tls = TlsSettings(
            enabled = security == "tls" || security == "reality",
            serverName = params["sni"] ?: params["pbk"]?.let { "" } ?: "",
            insecure = params["allowInsecure"] == "1",
            alpn = params["alpn"]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList(),
            reality = security == "reality",
            realityPublicKey = params["pbk"] ?: "",
            realityShortId = params["sid"] ?: "",
            utls = params["fp"].isNullOrEmpty().not(),
            utlsFingerprint = params["fp"] ?: "chrome",
        )

        return Profile(
            id = newId(),
            name = (params["remarks"] ?: decode(fragment(link))).ifBlank { host },
            protocol = Protocol.VLESS,
            server = host,
            serverPort = port,
            uuid = uuid,
            flow = params["flow"] ?: "",
            transport = transport,
            tls = tls,
        )
    }

    // ---------------------------------------------------------------- vmess

    private fun parseVmess(link: String): Profile {
        val payload = stripScheme(link)
        val json = String(Base64.decode(payload, Base64.DEFAULT), Charsets.UTF_8)
        val map = app.nebulabox.util.Json.miniMap(json)

        val transport = Transport(
            type = (map["net"] ?: map["type"] ?: "tcp").toString(),
            host = (map["host"] ?: "").toString(),
            path = (map["path"] ?: "").toString(),
        )
        val tlsEnabled = map["tls"] == "tls" || map["tls"] == true
        val tls = TlsSettings(
            enabled = tlsEnabled,
            serverName = (map["sni"] ?: map["host"] ?: "").toString(),
            insecure = map["allowInsecure"] == "1" || map["allowInsecure"] == true,
            alpn = (map["alpn"] ?: "").toString().split(",").filter { it.isNotEmpty() },
        )

        return Profile(
            id = newId(),
            name = (map["ps"] ?: "").toString().ifBlank { (map["add"] ?: "").toString() },
            protocol = Protocol.VMESS,
            server = (map["add"] ?: "").toString(),
            serverPort = (map["port"] ?: "443").toString().toIntOrNull() ?: 443,
            uuid = (map["id"] ?: "").toString(),
            alterId = (map["aid"] ?: "0").toString().toIntOrNull() ?: 0,
            security = (map["scy"] ?: "auto").toString(),
            transport = transport,
            tls = tls,
        )
    }

    // --------------------------------------------------------------- trojan

    private fun parseTrojan(link: String): Profile {
        val rest = stripScheme(link)
        val (authority, query) = splitQuery(rest)
        val params = parseQuery(query)
        val (password, hostPart) = splitUserInfo(authority)
        val (host, port) = splitHostPort(hostPart)
        val type = params["type"] ?: "tcp"
        return Profile(
            id = newId(),
            name = (params["remarks"] ?: decode(fragment(link))).ifBlank { host },
            protocol = Protocol.TROJAN,
            server = host,
            serverPort = port,
            password = decode(password),
            transport = Transport(
                type = type,
                host = params["host"] ?: "",
                path = params["path"] ?: "",
                serviceName = params["serviceName"] ?: "",
            ),
            tls = TlsSettings(
                enabled = true,
                serverName = params["sni"] ?: "",
                insecure = params["allowInsecure"] == "1",
                alpn = params["alpn"]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList(),
            ),
        )
    }

    // ------------------------------------------------------------------- ss

    private fun parseSs(link: String): List<Profile> {
        val rest = stripScheme(link)
        val (authority, query) = splitQuery(rest)
        val params = parseQuery(query)

        // SS SIP002: base64(method:password)@host:port
        // Legacy:     base64 of the whole method:password@host:port
        val decoded = runCatching {
            String(Base64.decode(authority, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8)
        }.getOrNull()

        return if (decoded != null && decoded.contains("@")) {
            val cred = decoded.substringBeforeLast("@")
            val hostPart = decoded.substringAfterLast("@")
            val (host, port) = splitHostPort(hostPart)
            listOf(
                Profile(
                    id = newId(),
                    name = (params["remarks"] ?: decode(fragment(link))).ifBlank { host },
                    protocol = Protocol.SHADOWSOCKS,
                    server = host,
                    serverPort = port,
                    method = cred.substringBefore(":"),
                    password = cred.substringAfter(":"),
                    plugin = params["plugin"]?.substringBefore(";") ?: "",
                    pluginOptions = params["plugin"]?.substringAfter(";", "") ?: "",
                ),
            )
        } else {
            // v2rayN style: the whole thing is base64
            val whole = runCatching {
                String(Base64.decode(rest.substringBefore("#").substringBefore("?"), Base64.DEFAULT), Charsets.UTF_8)
            }.getOrNull() ?: throw ParseException("cannot decode ss link")
            val cred = whole.substringBefore("@")
            val hostPart = whole.substringAfter("@")
            val (host, port) = splitHostPort(hostPart)
            listOf(
                Profile(
                    id = newId(),
                    name = decode(fragment(link)).ifBlank { host },
                    protocol = Protocol.SHADOWSOCKS,
                    server = host,
                    serverPort = port,
                    method = cred.substringBefore(":"),
                    password = cred.substringAfter(":"),
                ),
            )
        }
    }

    // ---------------------------------------------------------------- socks

    private fun parseSocks(link: String): Profile {
        val rest = stripScheme(link)
        val (authority, query) = splitQuery(rest)
        val params = parseQuery(query)
        val (userInfo, hostPart) = splitUserInfo(authority)
        val (host, port) = splitHostPort(hostPart)
        val user = if (userInfo.contains(":")) userInfo.substringBefore(":") else ""
        val pass = if (userInfo.contains(":")) userInfo.substringAfter(":") else userInfo
        return Profile(
            id = newId(),
            name = (params["remarks"] ?: decode(fragment(link))).ifBlank { host },
            protocol = Protocol.SOCKS,
            server = host,
            serverPort = port,
            username = decode(user),
            password = decode(pass),
        )
    }

    private fun parseHttpProxy(link: String): Profile {
        val rest = stripScheme(link)
        val (authority, query) = splitQuery(rest)
        val params = parseQuery(query)
        val (userInfo, hostPart) = splitUserInfo(authority)
        val (host, port) = splitHostPort(hostPart)
        val user = if (userInfo.contains(":")) userInfo.substringBefore(":") else ""
        val pass = if (userInfo.contains(":")) userInfo.substringAfter(":") else userInfo
        return Profile(
            id = newId(),
            name = (params["remarks"] ?: decode(fragment(link))).ifBlank { host },
            protocol = Protocol.HTTP,
            server = host,
            serverPort = port,
            username = decode(user),
            password = decode(pass),
        )
    }

    // ------------------------------------------------------------ hysteria2

    private fun parseHysteria2(link: String): Profile {
        val rest = stripScheme(link)
        val (authority, query) = splitQuery(rest)
        val params = parseQuery(query)
        val (password, hostPart) = splitUserInfo(authority)
        val (host, port) = splitHostPort(hostPart)
        return Profile(
            id = newId(),
            name = (params["obfs-password"]?.let { "" } ?: decode(fragment(link))).ifBlank { host },
            protocol = Protocol.HYSTERIA2,
            server = host,
            serverPort = port,
            password = decode(password),
            upMbps = params["upmbps"]?.toIntOrNull() ?: 0,
            downMbps = params["downmbps"]?.toIntOrNull() ?: 0,
            tls = TlsSettings(
                enabled = true,
                serverName = params["sni"] ?: "",
                insecure = params["insecure"] == "1" || params["allowInsecure"] == "1",
            ),
        )
    }

    // ----------------------------------------------------------------- tuic

    private fun parseTuic(link: String): Profile {
        val rest = stripScheme(link)
        val (authority, query) = splitQuery(rest)
        val params = parseQuery(query)
        val (userInfo, hostPart) = splitUserInfo(authority)
        val (host, port) = splitHostPort(hostPart)
        val uuid = if (userInfo.contains(":")) userInfo.substringBefore(":") else userInfo
        val token = if (userInfo.contains(":")) userInfo.substringAfter(":") else ""
        return Profile(
            id = newId(),
            name = (params["remarks"] ?: decode(fragment(link))).ifBlank { host },
            protocol = Protocol.TUIC,
            server = host,
            serverPort = port,
            uuid = decode(uuid),
            password = decode(token),
            tls = TlsSettings(
                enabled = true,
                serverName = params["sni"] ?: "",
                insecure = params["allow_insecure"] == "1",
                alpn = params["alpn"]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList(),
            ),
        )
    }

    // ------------------------------------------------------------ wireguard

    private fun parseWireGuard(link: String): Profile {
        val rest = stripScheme(link)
        val (authority, query) = splitQuery(rest)
        val params = parseQuery(query)
        val (privateKey, hostPart) = splitUserInfo(authority)
        val (host, port) = splitHostPort(hostPart)
        return Profile(
            id = newId(),
            name = (params["remarks"] ?: decode(fragment(link))).ifBlank { host },
            protocol = Protocol.WIREGUARD,
            server = host,
            serverPort = port,
            privateKey = decode(privateKey),
            peerPublicKey = params["publicKey"] ?: params["pubkey"] ?: "",
            preSharedKey = params["presharedKey"] ?: params["psk"] ?: "",
            localAddresses = params["address"]?.split(",")?.filter { it.isNotEmpty() }
                ?: emptyList(),
            mtu = params["mtu"]?.toIntOrNull() ?: 1420,
            reserved = params["reserved"]?.split("-")?.mapNotNull { it.toIntOrNull() }
                ?: emptyList(),
        )
    }

    // ------------------------------------------------------------------ ssh

    private fun parseSsh(link: String): Profile {
        val rest = stripScheme(link)
        val (authority, query) = splitQuery(rest)
        val params = parseQuery(query)
        val (userInfo, hostPart) = splitUserInfo(authority)
        val (host, port) = splitHostPort(hostPart, 22)
        val user = if (userInfo.contains(":")) userInfo.substringBefore(":") else userInfo
        val pass = if (userInfo.contains(":")) userInfo.substringAfter(":") else ""
        return Profile(
            id = newId(),
            name = (params["remarks"] ?: decode(fragment(link))).ifBlank { host },
            protocol = Protocol.SSH,
            server = host,
            serverPort = port,
            username = decode(user),
            password = decode(pass),
            clientVersion = params["clientVersion"] ?: "SSH-2.0-OpenSSH_9.8",
            hostKeyAlgorithms = params["hostKeyAlgorithms"]?.split(",")?.filter { it.isNotEmpty() }
                ?: emptyList(),
        )
    }

    // ------------------------------------------------------- sing-box json

    private fun parseSingBoxJson(json: String): Profile {
        val root = Json.miniMap(json)
        @Suppress("UNCHECKED_CAST")
        val outbounds = root["outbounds"] as? List<Map<String, Any?>>
            ?: throw ParseException("no outbounds in config")
        val first = outbounds.firstOrNull { it["type"] != "selector" && it["type"] != "urltest" }
            ?: outbounds.first()
        val type = first["type"]?.toString() ?: throw ParseException("outbound without type")
        val protocol = Protocol.fromWire(type) ?: Protocol.DIRECT
        val transport = first["transport"] as? Map<String, Any?>
        val tlsBlock = first["tls"] as? Map<String, Any?>
        return Profile(
            id = newId(),
            name = first["tag"]?.toString() ?: first["server"]?.toString() ?: type,
            protocol = protocol,
            server = first["server"]?.toString() ?: "",
            serverPort = (first["server_port"] as? Number)?.toInt()
                ?: first["server_port"]?.toString()?.toIntOrNull() ?: 0,
            username = first["username"]?.toString() ?: first["uuid"]?.toString() ?: "",
            password = first["password"]?.toString() ?: "",
            uuid = first["uuid"]?.toString() ?: "",
            method = first["method"]?.toString() ?: "",
            flow = first["flow"]?.toString() ?: "",
            transport = Transport(
                type = transport?.get("type")?.toString() ?: "tcp",
                host = transport?.get("host")?.toString() ?: "",
                path = transport?.get("path")?.toString() ?: "",
                serviceName = transport?.get("service_name")?.toString() ?: "",
            ),
            tls = TlsSettings(
                enabled = tlsBlock?.get("enabled") == true,
                serverName = tlsBlock?.get("server_name")?.toString() ?: "",
                insecure = tlsBlock?.get("insecure") == true,
            ),
        )
    }

    // ------------------------------------------------------------- helpers

    fun newId(): String = UUID.randomUUID().toString()

    private fun stripScheme(link: String): String = link.substringAfter("://")

    private fun fragment(link: String): String =
        if (link.contains("#")) link.substringAfterLast("#") else ""

    private fun splitQuery(s: String): Pair<String, String> {
        val withoutFragment = if (s.contains("#")) s.substringBefore("#") else s
        return if (withoutFragment.contains("?")) {
            withoutFragment.substringBefore("?") to withoutFragment.substringAfter("?")
        } else {
            withoutFragment to ""
        }
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split("&").mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) null else part.substring(0, idx) to decode(part.substring(idx + 1))
        }.toMap()
    }

    private fun parseHeaders(raw: String): Map<String, String> {
        val json = Json.miniMap(raw)
        return json.mapValues { (_, v) -> v.toString() }
    }

    private fun splitUserInfo(authority: String): Pair<String, String> =
        if (authority.contains("@")) {
            authority.substringBeforeLast("@") to authority.substringAfterLast("@")
        } else {
            "" to authority
        }

    private fun splitHostPort(hostPart: String, defaultPort: Int = 443): Pair<String, Int> {
        if (hostPart.startsWith("[")) {
            val host = hostPart.substringAfter("[").substringBefore("]")
            val port = hostPart.substringAfter("]:", "").toIntOrNull() ?: defaultPort
            return host to port
        }
        val idx = hostPart.lastIndexOf(':')
        return if (idx > 0) {
            hostPart.substring(0, idx) to (hostPart.substring(idx + 1).toIntOrNull() ?: defaultPort)
        } else {
            hostPart to defaultPort
        }
    }

    private fun decode(value: String): String = runCatching {
        URLDecoder.decode(value, "UTF-8")
    }.getOrDefault(value)

    internal fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
