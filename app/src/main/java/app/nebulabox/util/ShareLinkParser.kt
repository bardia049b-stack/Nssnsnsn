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
 * Parses share links and custom JSON configurations used across the ecosystem.
 *
 * Supported:
 *  - vless://, vmess://, trojan://, ss://, socks://, socks5://, http(s)://,
 *    hysteria2://, hy2://, tuic://, wireguard://, wg://, ssh://
 *  - Base64-encoded subscription payloads containing multiple share links
 *  - Full sing-box JSON configs, single sing-box outbound JSON objects,
 *    JSON arrays of configs/outbounds, and Xray/V2Ray JSON configs.
 */
object ShareLinkParser {

    class ParseException(message: String) : Exception(message)

    private val SCHEME_PREFIXES = listOf(
        "vless://", "vmess://", "trojan://", "ss://", "socks://", "socks5://",
        "http://", "https://", "hysteria2://", "hy2://", "tuic://",
        "wireguard://", "wg://", "ssh://", "naive+https://",
    )

    private val SCHEME_SPLIT_REGEX = Regex(
        "(?=(?:vless|vmess|trojan|ss|socks5?|https?|hysteria2|hy2|tuic|wireguard|wg|ssh|naive\\+https)://)",
        RegexOption.IGNORE_CASE,
    )

    fun looksLikeShareLink(text: String): Boolean {
        val t = text.trim()
        return SCHEME_PREFIXES.any { t.startsWith(it, ignoreCase = true) } ||
            t.startsWith("{") || t.startsWith("[")
    }

    /**
     * Parses pasted text that may be:
     *  1. A full sing-box or Xray JSON object `{ ... }` or array `[ ... ]`
     *  2. One or more share links (newline-separated or space-separated)
     *  3. A Base64-encoded subscription body containing share links or JSON
     */
    fun parseMany(text: String): List<Profile> {
        val trimmed = text.trim().removePrefix("\uFEFF")
        if (trimmed.isEmpty()) return emptyList()

        // 1. Check if the whole input is a JSON object or JSON array
        if (trimmed.startsWith("{")) {
            runCatching { return parseJsonDocument(trimmed) }
        }
        if (trimmed.startsWith("[")) {
            runCatching {
                val list = parseJsonArray(trimmed)
                if (list.isNotEmpty()) return list
            }
        }

        // 2. Parse line by line (preserving spaces inside #remarks)
        val out = mutableListOf<Profile>()
        val rawLines = trimmed.lines().map { it.trim() }.filter { it.isNotEmpty() }
        for (line in rawLines) {
            if (line.startsWith("#") || line.startsWith("//")) continue
            val chunks = SCHEME_SPLIT_REGEX.split(line).map { it.trim() }.filter { it.isNotEmpty() }
            val candidates = if (chunks.isNotEmpty()) chunks else listOf(line)
            for (candidate in candidates) {
                runCatching { out.addAll(parse(candidate)) }
            }
        }
        if (out.isNotEmpty()) return out

        // 3. Fallback: try decoding the whole payload as Base64 (subscription content)
        val decoded = decodeBase64ToString(trimmed)?.trim()
        if (!decoded.isNullOrEmpty() && decoded != trimmed) {
            if (decoded.startsWith("{") || decoded.startsWith("[") ||
                SCHEME_PREFIXES.any { s -> decoded.contains(s, ignoreCase = true) }
            ) {
                return parseMany(decoded)
            }
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
            trimmed.startsWith("socks://", true) || trimmed.startsWith("socks5://", true) ->
                listOf(parseSocks(trimmed))
            trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true) ->
                listOf(parseHttpProxy(trimmed))
            trimmed.startsWith("hysteria2://", true) || trimmed.startsWith("hy2://", true) ->
                listOf(parseHysteria2(trimmed))
            trimmed.startsWith("tuic://", true) -> listOf(parseTuic(trimmed))
            trimmed.startsWith("wireguard://", true) || trimmed.startsWith("wg://", true) ->
                listOf(parseWireGuard(trimmed))
            trimmed.startsWith("ssh://", true) -> listOf(parseSsh(trimmed))
            trimmed.startsWith("naive+https://", true) -> listOf(parseNaive(trimmed))
            trimmed.startsWith("{") -> parseJsonDocument(trimmed)
            trimmed.startsWith("[") -> parseJsonArray(trimmed)
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

        val rawType = (params["type"] ?: params["network"] ?: "tcp").lowercase()
        val type = when (rawType) {
            "h2" -> "http"
            "xhttp", "splithttp" -> "httpupgrade"
            else -> rawType
        }
        val wsHost = params["host"] ?: params["authority"] ?: ""
        val transport = Transport(
            type = type,
            host = wsHost,
            path = params["path"] ?: "",
            serviceName = params["serviceName"] ?: params["service_name"] ?: params["path"] ?: "",
            headers = params["headers"]?.let { runCatching { parseHeaders(it) }.getOrNull() }
                ?: emptyMap(),
            maxEarlyData = params["ed"]?.toIntOrNull() ?: 0,
            earlyDataHeader = params["eh"] ?: "",
        )

        val security = (params["security"] ?: "").lowercase()
        val isReality = security == "reality" || !params["pbk"].isNullOrBlank()
        val isTls = security == "tls" || isReality
        val sni = params["sni"] ?: params["peer"] ?: wsHost.ifBlank { if (isTls && !isReality) host else "" }
        val fp = (params["fp"] ?: "").ifBlank { "chrome" }
        val tls = TlsSettings(
            enabled = isTls,
            serverName = sni,
            insecure = params["allowInsecure"] == "1" || params["allowInsecure"] == "true" || params["insecure"] == "1",
            alpn = params["alpn"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
            reality = isReality,
            realityPublicKey = params["pbk"] ?: "",
            realityShortId = params["sid"] ?: "",
            utls = isReality || !params["fp"].isNullOrBlank(),
            utlsFingerprint = fp,
        )

        return Profile(
            id = newId(),
            name = (params["remarks"] ?: decode(fragment(link))).ifBlank { "$host:$port" },
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
        val payload = stripScheme(link).trim()
        // Support both standard base64 JSON vmess:// and vmess://uuid@host:port?... query style
        val json = decodeBase64ToString(payload.substringBefore("#"))
        if (json == null || !json.trim().startsWith("{")) {
            return parseVmessUrlStyle(link)
        }
        val map = Json.miniMap(json)

        val rawNet = (map["net"] ?: map["type"] ?: "tcp").toString().lowercase()
        val netType = when (rawNet) {
            "h2" -> "http"
            "xhttp", "splithttp" -> "httpupgrade"
            else -> rawNet
        }
        val wsHost = (map["host"] ?: "").toString()
        val transport = Transport(
            type = netType,
            host = wsHost,
            path = (map["path"] ?: "").toString(),
            serviceName = (map["path"] ?: map["serviceName"] ?: "").toString(),
        )
        val tlsVal = (map["tls"] ?: "").toString().lowercase()
        val tlsEnabled = tlsVal == "tls" || tlsVal == "true" || tlsVal == "1"
        val server = (map["add"] ?: "").toString().trim()
        val port = (map["port"] ?: "443").toString().trim().toIntOrNull() ?: 443
        val sni = (map["sni"] ?: "").toString().ifBlank { wsHost.ifBlank { server } }
        val fp = (map["fp"] ?: "").toString()
        val tls = TlsSettings(
            enabled = tlsEnabled,
            serverName = sni,
            insecure = map["allowInsecure"] == "1" || map["allowInsecure"] == true || map["verify_cert"] == false,
            alpn = (map["alpn"] ?: "").toString().split(",").map { it.trim() }.filter { it.isNotEmpty() },
            utls = fp.isNotBlank(),
            utlsFingerprint = fp.ifBlank { "chrome" },
        )

        return Profile(
            id = newId(),
            name = (map["ps"] ?: "").toString().ifBlank { decode(fragment(link)).ifBlank { "$server:$port" } },
            protocol = Protocol.VMESS,
            server = server,
            serverPort = port,
            uuid = (map["id"] ?: "").toString().trim(),
            alterId = (map["aid"] ?: "0").toString().toIntOrNull() ?: 0,
            security = (map["scy"] ?: "auto").toString().ifBlank { "auto" },
            transport = transport,
            tls = tls,
        )
    }

    private fun parseVmessUrlStyle(link: String): Profile {
        val rest = stripScheme(link)
        val (authority, query) = splitQuery(rest)
        val params = parseQuery(query)
        val (userInfo, hostPart) = splitUserInfo(authority)
        val (host, port) = splitHostPort(hostPart)
        val type = (params["type"] ?: "tcp").lowercase()
        val security = (params["security"] ?: "").lowercase()
        return Profile(
            id = newId(),
            name = (params["remarks"] ?: decode(fragment(link))).ifBlank { "$host:$port" },
            protocol = Protocol.VMESS,
            server = host,
            serverPort = port,
            uuid = decode(userInfo),
            security = params["encryption"] ?: "auto",
            transport = Transport(
                type = type,
                host = params["host"] ?: "",
                path = params["path"] ?: "",
                serviceName = params["serviceName"] ?: "",
            ),
            tls = TlsSettings(
                enabled = security == "tls",
                serverName = params["sni"] ?: params["host"] ?: host,
                insecure = params["allowInsecure"] == "1",
            ),
        )
    }

    // --------------------------------------------------------------- trojan

    private fun parseTrojan(link: String): Profile {
        val rest = stripScheme(link)
        val (authority, query) = splitQuery(rest)
        val params = parseQuery(query)
        val (password, hostPart) = splitUserInfo(authority)
        val (host, port) = splitHostPort(hostPart)
        val rawType = (params["type"] ?: "tcp").lowercase()
        val type = if (rawType == "h2") "http" else rawType
        val security = (params["security"] ?: "tls").lowercase()
        val isReality = security == "reality" || !params["pbk"].isNullOrBlank()
        val tlsEnabled = security != "none"
        val wsHost = params["host"] ?: ""
        val fp = (params["fp"] ?: "").ifBlank { "chrome" }
        return Profile(
            id = newId(),
            name = (params["remarks"] ?: decode(fragment(link))).ifBlank { "$host:$port" },
            protocol = Protocol.TROJAN,
            server = host,
            serverPort = port,
            password = decode(password),
            transport = Transport(
                type = type,
                host = wsHost,
                path = params["path"] ?: "",
                serviceName = params["serviceName"] ?: "",
            ),
            tls = TlsSettings(
                enabled = tlsEnabled,
                serverName = params["sni"] ?: params["peer"] ?: wsHost.ifBlank { host },
                insecure = params["allowInsecure"] == "1" || params["allowInsecure"] == "true" || params["insecure"] == "1",
                alpn = params["alpn"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
                reality = isReality,
                realityPublicKey = params["pbk"] ?: "",
                realityShortId = params["sid"] ?: "",
                utls = isReality || !params["fp"].isNullOrBlank(),
                utlsFingerprint = fp,
            ),
        )
    }

    // ------------------------------------------------------------------- ss

    private fun parseSs(link: String): List<Profile> {
        val rest = stripScheme(link)
        val (authority, query) = splitQuery(rest)
        val params = parseQuery(query)
        val remarkName = (params["remarks"] ?: decode(fragment(link)))

        if (authority.contains("@")) {
            // SIP002: userinfo@host:port where userinfo is either base64(method:password) or method:password
            val rawUser = authority.substringBeforeLast("@")
            val hostPart = authority.substringAfterLast("@")
            val (host, port) = splitHostPort(hostPart)
            val decodedUser = if (rawUser.contains(":")) {
                decode(rawUser)
            } else {
                decodeBase64ToString(rawUser) ?: decode(rawUser)
            }
            val method = decodedUser.substringBefore(":")
            val password = decodedUser.substringAfter(":", "")
            return listOf(
                Profile(
                    id = newId(),
                    name = remarkName.ifBlank { "$host:$port" },
                    protocol = Protocol.SHADOWSOCKS,
                    server = host,
                    serverPort = port,
                    method = method,
                    password = password,
                    plugin = params["plugin"]?.substringBefore(";") ?: "",
                    pluginOptions = params["plugin"]?.substringAfter(";", "") ?: "",
                ),
            )
        } else {
            // Legacy: the whole authority is base64(method:password@host:port)
            val whole = decodeBase64ToString(authority.substringBefore("/"))
                ?: throw ParseException("cannot decode ss link")
            val cred = whole.substringBeforeLast("@")
            val hostPart = whole.substringAfterLast("@")
            val (host, port) = splitHostPort(hostPart)
            return listOf(
                Profile(
                    id = newId(),
                    name = remarkName.ifBlank { "$host:$port" },
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
        val (host, port) = splitHostPort(hostPart, 1080)
        val decodedUser = if (!userInfo.contains(":") && userInfo.isNotBlank()) {
            decodeBase64ToString(userInfo) ?: userInfo
        } else {
            userInfo
        }
        val user = if (decodedUser.contains(":")) decodedUser.substringBefore(":") else ""
        val pass = if (decodedUser.contains(":")) decodedUser.substringAfter(":") else decodedUser
        return Profile(
            id = newId(),
            name = (params["remarks"] ?: decode(fragment(link))).ifBlank { "$host:$port" },
            protocol = Protocol.SOCKS,
            server = host,
            serverPort = port,
            username = decode(user),
            password = decode(pass),
        )
    }

    private fun parseHttpProxy(link: String): Profile {
        val isHttps = link.startsWith("https://", ignoreCase = true)
        val rest = stripScheme(link)
        val (authority, query) = splitQuery(rest)
        val params = parseQuery(query)
        val (userInfo, hostPart) = splitUserInfo(authority)
        val (host, port) = splitHostPort(hostPart, if (isHttps) 443 else 8080)
        val user = if (userInfo.contains(":")) userInfo.substringBefore(":") else ""
        val pass = if (userInfo.contains(":")) userInfo.substringAfter(":") else userInfo
        return Profile(
            id = newId(),
            name = (params["remarks"] ?: decode(fragment(link))).ifBlank { "$host:$port" },
            protocol = Protocol.HTTP,
            server = host,
            serverPort = port,
            username = decode(user),
            password = decode(pass),
            tls = TlsSettings(enabled = isHttps, serverName = params["sni"] ?: host),
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
            name = (params["remarks"] ?: decode(fragment(link))).ifBlank { "$host:$port" },
            protocol = Protocol.HYSTERIA2,
            server = host,
            serverPort = port,
            password = decode(password),
            upMbps = params["upmbps"]?.toIntOrNull() ?: 0,
            downMbps = params["downmbps"]?.toIntOrNull() ?: 0,
            tls = TlsSettings(
                enabled = true,
                serverName = params["sni"] ?: host,
                insecure = params["insecure"] == "1" || params["allowInsecure"] == "1" || params["insecure"] == "true",
                alpn = params["alpn"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
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
        val decodedUserInfo = decode(userInfo)
        val uuid = if (decodedUserInfo.contains(":")) decodedUserInfo.substringBefore(":") else decodedUserInfo
        val token = if (decodedUserInfo.contains(":")) decodedUserInfo.substringAfter(":") else (params["password"] ?: "")
        return Profile(
            id = newId(),
            name = (params["remarks"] ?: decode(fragment(link))).ifBlank { "$host:$port" },
            protocol = Protocol.TUIC,
            server = host,
            serverPort = port,
            uuid = uuid,
            password = token,
            tls = TlsSettings(
                enabled = true,
                serverName = params["sni"] ?: host,
                insecure = params["allow_insecure"] == "1" || params["allowInsecure"] == "1" || params["insecure"] == "1",
                alpn = params["alpn"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
            ),
        )
    }

    // ------------------------------------------------------------ wireguard

    private fun parseWireGuard(link: String): Profile {
        val rest = stripScheme(link)
        val (authority, query) = splitQuery(rest)
        val params = parseQuery(query)
        val (privateKey, hostPart) = splitUserInfo(authority)
        val (host, port) = splitHostPort(hostPart, 51820)
        return Profile(
            id = newId(),
            name = (params["remarks"] ?: decode(fragment(link))).ifBlank { "$host:$port" },
            protocol = Protocol.WIREGUARD,
            server = host,
            serverPort = port,
            privateKey = decode(privateKey.ifBlank { params["privateKey"] ?: params["secretKey"] ?: "" }),
            peerPublicKey = params["publicKey"] ?: params["pubkey"] ?: params["peerPublicKey"] ?: "",
            preSharedKey = params["presharedKey"] ?: params["psk"] ?: "",
            localAddresses = (params["address"] ?: params["ip"] ?: "")
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { if (it.contains("/")) it else if (it.contains(":")) "$it/128" else "$it/32" },
            mtu = params["mtu"]?.toIntOrNull() ?: 1420,
            reserved = params["reserved"]?.split(",", "-")?.mapNotNull { it.trim().toIntOrNull() }
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
            name = (params["remarks"] ?: decode(fragment(link))).ifBlank { "$host:$port" },
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

    private fun parseNaive(link: String): Profile {
        val rest = stripScheme(link)
        val (authority, query) = splitQuery(rest)
        val params = parseQuery(query)
        val (userInfo, hostPart) = splitUserInfo(authority)
        val (host, port) = splitHostPort(hostPart, 443)
        val user = if (userInfo.contains(":")) userInfo.substringBefore(":") else userInfo
        val pass = if (userInfo.contains(":")) userInfo.substringAfter(":") else ""
        return Profile(
            id = newId(),
            name = (params["remarks"] ?: decode(fragment(link))).ifBlank { "$host:$port" },
            protocol = Protocol.NAIVE,
            server = host,
            serverPort = port,
            username = decode(user),
            password = decode(pass),
            tls = TlsSettings(enabled = true, serverName = params["sni"] ?: host),
        )
    }

    // ------------------------------------------------------- custom / sing-box / xray json

    private fun parseJsonArray(json: String): List<Profile> {
        val parsed = Json.parse(json) as? List<*> ?: return emptyList()
        val result = mutableListOf<Profile>()
        for (item in parsed) {
            val map = item as? Map<*, *> ?: continue
            val itemJson = Json.any(map)
            runCatching { result.addAll(parseJsonDocument(itemJson)) }
        }
        return result
    }

    /**
     * Parses a JSON object into one or more [Profile] entries.
     *
     *  - If it is an Xray / v2rayNG config (outbounds have `protocol` + `settings`),
     *    converts the Xray outbound into a native [Profile] (or custom sing-box JSON).
     *  - If it is a sing-box config (full config with `outbounds`/`inbounds`/`route`/`dns`/`endpoints`
     *    or a single sing-box outbound with `type`), stores the entire JSON in
     *    [Profile.customConfig] with [Protocol.CUSTOM] so [app.nebulabox.config.ConfigBuilder]
     *    preserves all custom fields and rules.
     */
    fun parseJsonDocument(json: String): List<Profile> {
        val trimmed = json.trim()
        val root = Json.miniMap(trimmed)
        if (root.isEmpty()) throw ParseException("invalid JSON document")

        @Suppress("UNCHECKED_CAST")
        val outbounds = root["outbounds"] as? List<Map<String, Any?>>

        // Check if this is an Xray/V2Ray JSON config (uses `protocol` + `settings` inside `outbounds`)
        if (outbounds != null && outbounds.any { it.containsKey("protocol") && it.containsKey("settings") }) {
            val xrayProfile = runCatching { parseXrayJson(root, outbounds) }.getOrNull()
            if (xrayProfile != null) return listOf(xrayProfile)
        }

        // Extract display metadata from sing-box full config or single outbound
        val ignoredTypes = setOf("direct", "block", "dns", "selector", "urltest")
        val primaryOutbound: Map<String, Any?>? = when {
            outbounds != null -> outbounds.firstOrNull {
                val t = it["type"]?.toString()?.lowercase() ?: ""
                t.isNotEmpty() && t !in ignoredTypes
            } ?: outbounds.firstOrNull()
            root.containsKey("type") -> root
            else -> null
        }

        @Suppress("UNCHECKED_CAST")
        val endpoints = root["endpoints"] as? List<Map<String, Any?>>
        val primaryEndpoint = endpoints?.firstOrNull()

        val server = primaryOutbound?.get("server")?.toString()
            ?: primaryEndpoint?.get("address")?.toString()
            ?: ""
        val serverPort = (primaryOutbound?.get("server_port") as? Number)?.toInt()
            ?: primaryOutbound?.get("server_port")?.toString()?.toIntOrNull()
            ?: 0

        val rawName = root["remarks"]?.toString()
            ?: root["name"]?.toString()
            ?: primaryOutbound?.get("tag")?.toString()?.takeIf { it !in setOf("proxy", "out", "direct") }
            ?: primaryEndpoint?.get("tag")?.toString()
            ?: if (server.isNotBlank()) {
                if (serverPort > 0) "$server:$serverPort" else server
            } else {
                "Custom JSON"
            }

        return listOf(
            Profile(
                id = newId(),
                name = rawName,
                protocol = Protocol.CUSTOM,
                server = server,
                serverPort = serverPort,
                customConfig = trimmed,
            ),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseXrayJson(
        root: Map<String, Any?>,
        outbounds: List<Map<String, Any?>>,
    ): Profile? {
        val proxy = outbounds.firstOrNull {
            val p = it["protocol"]?.toString()?.lowercase() ?: ""
            p in setOf("vless", "vmess", "trojan", "shadowsocks", "socks", "http", "wireguard")
        } ?: return null

        val protoStr = proxy["protocol"]?.toString()?.lowercase() ?: return null
        val protocol = Protocol.fromWire(protoStr) ?: return null
        val settings = proxy["settings"] as? Map<String, Any?> ?: emptyMap()
        val stream = proxy["streamSettings"] as? Map<String, Any?> ?: emptyMap()

        var server = ""
        var port = 443
        var uuid = ""
        var password = ""
        var method = ""
        var flow = ""
        var security = "auto"

        val vnext = (settings["vnext"] as? List<Map<String, Any?>>)?.firstOrNull()
        val servers = (settings["servers"] as? List<Map<String, Any?>>)?.firstOrNull()
        if (vnext != null) {
            server = vnext["address"]?.toString() ?: ""
            port = (vnext["port"] as? Number)?.toInt() ?: vnext["port"]?.toString()?.toIntOrNull() ?: 443
            val user = (vnext["users"] as? List<Map<String, Any?>>)?.firstOrNull()
            if (user != null) {
                uuid = user["id"]?.toString() ?: ""
                flow = user["flow"]?.toString() ?: ""
                security = user["security"]?.toString() ?: "auto"
            }
        } else if (servers != null) {
            server = servers["address"]?.toString() ?: ""
            port = (servers["port"] as? Number)?.toInt() ?: servers["port"]?.toString()?.toIntOrNull() ?: 443
            password = servers["password"]?.toString() ?: ""
            method = servers["method"]?.toString() ?: ""
        }

        val rawNet = (stream["network"]?.toString() ?: "tcp").lowercase()
        val netType = when (rawNet) {
            "h2" -> "http"
            "xhttp", "splithttp" -> "httpupgrade"
            else -> rawNet
        }
        val wsSettings = stream["wsSettings"] as? Map<String, Any?>
        val httpUpgradeSettings = (stream["httpupgradeSettings"] ?: stream["xhttpSettings"]) as? Map<String, Any?>
        val grpcSettings = stream["grpcSettings"] as? Map<String, Any?>
        val wsHeaders = wsSettings?.get("headers") as? Map<String, Any?>
        val hostHeader = wsHeaders?.get("Host")?.toString()
            ?: wsHeaders?.get("host")?.toString()
            ?: wsSettings?.get("host")?.toString()
            ?: httpUpgradeSettings?.get("host")?.toString()
            ?: ""
        val path = wsSettings?.get("path")?.toString()
            ?: httpUpgradeSettings?.get("path")?.toString()
            ?: ""
        val serviceName = grpcSettings?.get("serviceName")?.toString() ?: ""

        val streamSec = (stream["security"]?.toString() ?: "").lowercase()
        val tlsSettings = stream["tlsSettings"] as? Map<String, Any?>
        val realitySettings = stream["realitySettings"] as? Map<String, Any?>
        val isReality = streamSec == "reality" || realitySettings != null
        val isTls = streamSec == "tls" || isReality
        val sni = realitySettings?.get("serverName")?.toString()
            ?: tlsSettings?.get("serverName")?.toString()
            ?: hostHeader.ifBlank { server }
        val fp = realitySettings?.get("fingerprint")?.toString()
            ?: tlsSettings?.get("fingerprint")?.toString()
            ?: "chrome"
        val alpn = (tlsSettings?.get("alpn") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()

        val remark = root["remarks"]?.toString()
            ?: proxy["tag"]?.toString()?.takeIf { it != "proxy" }
            ?: "$server:$port"

        return Profile(
            id = newId(),
            name = remark,
            protocol = protocol,
            server = server,
            serverPort = port,
            uuid = uuid,
            password = password,
            method = method,
            flow = flow,
            security = security,
            transport = Transport(
                type = netType,
                host = hostHeader,
                path = path,
                serviceName = serviceName,
            ),
            tls = TlsSettings(
                enabled = isTls,
                serverName = sni,
                insecure = tlsSettings?.get("allowInsecure") == true,
                alpn = alpn,
                reality = isReality,
                realityPublicKey = realitySettings?.get("publicKey")?.toString() ?: "",
                realityShortId = realitySettings?.get("shortId")?.toString() ?: "",
                utls = isReality || fp.isNotBlank(),
                utlsFingerprint = fp.ifBlank { "chrome" },
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
            if (idx <= 0) null else part.substring(0, idx).trim() to decode(part.substring(idx + 1))
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
        // Strip any trailing path (e.g. "example.com:443/" -> "example.com:443")
        val cleaned = hostPart.substringBefore("/").trim()
        if (cleaned.startsWith("[")) {
            val host = cleaned.substringAfter("[").substringBefore("]")
            val portStr = cleaned.substringAfter("]:", "").substringBefore(",")
            val port = portStr.toIntOrNull() ?: defaultPort
            return host to port
        }
        val idx = cleaned.lastIndexOf(':')
        return if (idx > 0) {
            val host = cleaned.substring(0, idx)
            val portPart = cleaned.substring(idx + 1).substringBefore(",").substringBefore("-")
            host to (portPart.toIntOrNull() ?: defaultPort)
        } else {
            cleaned to defaultPort
        }
    }

    private fun decodeBase64ToString(input: String): String? {
        val clean = input.trim().replace("\n", "").replace("\r", "").replace(" ", "")
        if (clean.isEmpty()) return null
        val padded = when (clean.length % 4) {
            2 -> "$clean=="
            3 -> "$clean="
            else -> clean
        }
        val flagsToTry = intArrayOf(
            Base64.DEFAULT,
            Base64.URL_SAFE or Base64.NO_WRAP,
            Base64.NO_WRAP,
        )
        for (flags in flagsToTry) {
            val decoded = runCatching {
                String(Base64.decode(padded, flags), Charsets.UTF_8)
            }.getOrNull()
            if (!decoded.isNullOrBlank()) return decoded
        }
        return null
    }

    private fun decode(value: String): String = runCatching {
        URLDecoder.decode(value, "UTF-8")
    }.getOrDefault(value)

    internal fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
