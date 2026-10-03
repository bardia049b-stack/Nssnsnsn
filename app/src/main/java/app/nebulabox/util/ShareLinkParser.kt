package app.nebulabox.util

import android.util.Base64
import app.nebulabox.data.Profile
import app.nebulabox.data.Protocol
import app.nebulabox.data.TlsSettings
import app.nebulabox.data.Transport
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID

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

    fun parseMany(text: String): List<Profile> {
        val trimmed = text.trim().removePrefix("\uFEFF")
        if (trimmed.isEmpty()) return emptyList()

        if (trimmed.startsWith("{")) {
            runCatching { return parseJsonDocument(trimmed) }
        }
        if (trimmed.startsWith("[")) {
            runCatching {
                val list = parseJsonArray(trimmed)
                if (list.isNotEmpty()) return list
            }
        }

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

    fun toShareUri(profile: Profile): String {
        val hostFormatted = if (profile.server.contains(":") && !profile.server.startsWith("[")) {
            "[${profile.server}]"
        } else {
            profile.server
        }
        val remarkEncoded = encode(profile.displayName)
        return when (profile.protocol) {
            Protocol.VLESS -> {
                val q = buildCommonQuery(profile).apply {
                    put("encryption", profile.encryption.ifBlank { "none" })
                    if (profile.flow.isNotBlank()) put("flow", profile.flow)
                }
                val qs = formatQuery(q)
                "vless://${encode(profile.uuid)}@$hostFormatted:${profile.serverPort}$qs#$remarkEncoded"
            }

            Protocol.VMESS -> {
                val net = profile.transport.type.ifBlank { "tcp" }
                val pathWithEd = buildWsPathWithEd(profile.transport)
                val vmessMap = linkedMapOf<String, Any?>(
                    "v" to "2",
                    "ps" to profile.displayName,
                    "add" to profile.server,
                    "port" to profile.serverPort.toString(),
                    "id" to profile.uuid,
                    "aid" to profile.alterId.toString(),
                    "scy" to profile.security.ifBlank { "auto" },
                    "net" to net,
                    "type" to profile.transport.headerType.ifBlank { "none" },
                    "host" to profile.transport.host,
                    "path" to if (net == "grpc") profile.transport.serviceName else pathWithEd,
                    "tls" to when {
                        profile.tls.reality -> "reality"
                        profile.tls.enabled -> "tls"
                        else -> ""
                    },
                    "sni" to profile.tls.serverName,
                    "alpn" to profile.tls.alpn.joinToString(","),
                    "fp" to profile.tls.utlsFingerprint.ifBlank { "chrome" },
                    "insecure" to if (profile.tls.insecure) "1" else "0",
                )
                val jsonBytes = Json.any(vmessMap).toByteArray(Charsets.UTF_8)
                val b64 = Base64.encodeToString(jsonBytes, Base64.NO_WRAP)
                "vmess://$b64"
            }

            Protocol.TROJAN -> {
                val q = buildCommonQuery(profile).apply {
                    if (profile.flow.isNotBlank()) put("flow", profile.flow)
                }
                val qs = formatQuery(q)
                "trojan://${encode(profile.password)}@$hostFormatted:${profile.serverPort}$qs#$remarkEncoded"
            }

            Protocol.SHADOWSOCKS -> {
                val rawCred = "${profile.method}:${profile.password}"
                val b64Cred = Base64.encodeToString(rawCred.toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
                "ss://$b64Cred@$hostFormatted:${profile.serverPort}#$remarkEncoded"
            }

            Protocol.HYSTERIA2 -> {
                val q = linkedMapOf<String, String>()
                q["security"] = "tls"
                if (profile.tls.serverName.isNotBlank()) q["sni"] = profile.tls.serverName
                if (profile.tls.alpn.isNotEmpty()) q["alpn"] = profile.tls.alpn.joinToString(",")
                q["insecure"] = if (profile.tls.insecure) "1" else "0"
                if (profile.obfsPassword.isNotBlank()) {
                    q["obfs"] = "salamander"
                    q["obfs-password"] = profile.obfsPassword
                }
                if (profile.portHopping.isNotBlank()) q["mport"] = profile.portHopping
                if (profile.tls.pinnedCA256.isNotBlank()) q["pinSHA256"] = profile.tls.pinnedCA256
                val qs = formatQuery(q)
                "hysteria2://${encode(profile.password)}@$hostFormatted:${profile.serverPort}$qs#$remarkEncoded"
            }

            Protocol.WIREGUARD -> {
                val q = linkedMapOf<String, String>()
                if (profile.peerPublicKey.isNotBlank()) q["publicKey"] = profile.peerPublicKey
                if (profile.preSharedKey.isNotBlank()) q["presharedKey"] = profile.preSharedKey
                if (profile.localAddresses.isNotEmpty()) q["address"] = profile.localAddresses.joinToString(",")
                if (profile.mtu > 0) q["mtu"] = profile.mtu.toString()
                if (profile.reserved.isNotEmpty()) q["reserved"] = profile.reserved.joinToString(",")
                val qs = formatQuery(q)
                "wireguard://${encode(profile.privateKey)}@$hostFormatted:${profile.serverPort}$qs#$remarkEncoded"
            }

            Protocol.SOCKS -> {
                val userPass = if (profile.username.isNotBlank()) {
                    val b64 = Base64.encodeToString("${profile.username}:${profile.password}".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                    "$b64@"
                } else ""
                "socks://$userPass$hostFormatted:${profile.serverPort}#$remarkEncoded"
            }

            Protocol.HTTP -> {
                val scheme = if (profile.tls.enabled) "https" else "http"
                val userPass = if (profile.username.isNotBlank()) "${encode(profile.username)}:${encode(profile.password)}@" else ""
                "$scheme://$userPass$hostFormatted:${profile.serverPort}#$remarkEncoded"
            }

            Protocol.CUSTOM -> profile.customConfig.ifBlank { "{}" }

            else -> "${profile.protocol.wire}://$hostFormatted:${profile.serverPort}#$remarkEncoded"
        }
    }

    private fun buildCommonQuery(profile: Profile): LinkedHashMap<String, String> {
        val q = LinkedHashMap<String, String>()
        val net = profile.transport.type.ifBlank { "tcp" }.lowercase()
        q["type"] = net
        q["security"] = when {
            profile.tls.reality -> "reality"
            profile.tls.enabled -> "tls"
            else -> "none"
        }
        if (profile.tls.serverName.isNotBlank()) q["sni"] = profile.tls.serverName
        if (profile.tls.utlsFingerprint.isNotBlank()) q["fp"] = profile.tls.utlsFingerprint
        if (profile.tls.alpn.isNotEmpty()) q["alpn"] = profile.tls.alpn.joinToString(",")
        if (profile.tls.enabled && !profile.tls.reality) {
            val ins = if (profile.tls.insecure) "1" else "0"
            q["insecure"] = ins
            q["allowInsecure"] = ins
        }
        if (profile.tls.reality) {
            if (profile.tls.realityPublicKey.isNotBlank()) q["pbk"] = profile.tls.realityPublicKey
            if (profile.tls.realityShortId.isNotBlank()) q["sid"] = profile.tls.realityShortId
            if (profile.tls.realitySpiderX.isNotBlank()) q["spx"] = profile.tls.realitySpiderX
        }
        if (profile.tls.echConfigList.isNotBlank()) q["ech"] = profile.tls.echConfigList
        if (profile.tls.pinnedCA256.isNotBlank()) q["pcs"] = profile.tls.pinnedCA256
        if (profile.tls.verifyPeerCertByName.isNotBlank()) q["vcn"] = profile.tls.verifyPeerCertByName
        if (profile.tls.mldsa65Verify.isNotBlank()) q["pqv"] = profile.tls.mldsa65Verify
        if (profile.finalMask.isNotBlank()) q["fm"] = profile.finalMask

        val pathWithEd = buildWsPathWithEd(profile.transport)
        when (net) {
            "tcp" -> {
                q["headerType"] = profile.transport.headerType.ifBlank { "none" }
                if (profile.transport.host.isNotBlank()) q["host"] = profile.transport.host
                if (profile.transport.path.isNotBlank()) q["path"] = profile.transport.path
            }
            "ws", "httpupgrade" -> {
                if (profile.transport.host.isNotBlank()) q["host"] = profile.transport.host
                if (pathWithEd.isNotBlank()) q["path"] = pathWithEd
            }
            "xhttp", "splithttp" -> {
                q["type"] = "xhttp"
                if (profile.transport.host.isNotBlank()) q["host"] = profile.transport.host
                if (pathWithEd.isNotBlank()) q["path"] = pathWithEd
                if (profile.transport.xhttpMode.isNotBlank()) q["mode"] = profile.transport.xhttpMode
                if (profile.transport.xhttpExtra.isNotBlank()) q["extra"] = profile.transport.xhttpExtra
            }
            "grpc" -> {
                if (profile.transport.serviceName.isNotBlank()) q["serviceName"] = profile.transport.serviceName
                if (profile.transport.authority.isNotBlank()) q["authority"] = profile.transport.authority
                if (profile.transport.grpcMode.isNotBlank()) q["mode"] = profile.transport.grpcMode
            }
            "kcp" -> {
                q["headerType"] = profile.transport.headerType.ifBlank { "none" }
                if (profile.transport.seed.isNotBlank()) q["seed"] = profile.transport.seed
            }
        }
        return q
    }

    fun buildWsPathWithEd(transport: Transport): String {
        val rawPath = transport.path.ifBlank { "/" }
        return if (transport.maxEarlyData > 0 && !rawPath.contains("ed=")) {
            val sep = if (rawPath.contains("?")) "&" else "?"
            "${rawPath}${sep}ed=${transport.maxEarlyData}"
        } else {
            rawPath
        }
    }

    private fun formatQuery(q: Map<String, String>): String {
        if (q.isEmpty()) return ""
        return "?" + q.entries.joinToString("&") { (k, v) -> "$k=${encode(v)}" }
    }

    private fun parseVless(link: String): Profile {
        val rest = stripScheme(link)
        val (authority, query) = splitQuery(rest)
        val params = parseQuery(query)
        val (userInfo, hostPart) = splitUserInfo(authority)
        val uuid = decode(userInfo)
        val (host, port) = splitHostPort(hostPart)

        val rawType = (params["type"] ?: params["network"] ?: "tcp").lowercase()
        val type = when (rawType) {
            "splithttp" -> "xhttp"
            else -> rawType
        }
        val wsHost = params["host"] ?: params["authority"] ?: ""
        val rawPath = params["path"] ?: ""
        val (_, parsedEd, parsedEh) = extractWsEarlyData(
            rawPath = rawPath,
            explicitEd = params["ed"]?.toIntOrNull() ?: 0,
            explicitEh = params["eh"] ?: "",
        )
        val transport = Transport(
            type = type,
            host = wsHost,
            path = rawPath,
            headerType = params["headerType"] ?: "none",
            serviceName = params["serviceName"] ?: params["service_name"] ?: "",
            authority = params["authority"] ?: "",
            grpcMode = params["mode"] ?: "gun",
            xhttpMode = params["mode"] ?: "auto",
            xhttpExtra = params["extra"] ?: "",
            seed = params["seed"] ?: "",
            headers = params["headers"]?.let { runCatching { parseHeaders(it) }.getOrNull() }
                ?: emptyMap(),
            maxEarlyData = parsedEd,
            earlyDataHeader = parsedEh,
        )

        val security = (params["security"] ?: "").lowercase()
        val isReality = security == "reality" || !params["pbk"].isNullOrBlank()
        val isTls = security == "tls" || isReality
        val sni = params["sni"] ?: params["peer"] ?: wsHost.ifBlank { if (isTls && !isReality) host else "" }
        val fp = params["fp"] ?: ""
        val tls = TlsSettings(
            enabled = isTls,
            serverName = sni,
            insecure = params["allowInsecure"] == "1" || params["allowInsecure"] == "true" || params["insecure"] == "1" || params["allow_insecure"] == "1",
            alpn = params["alpn"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
            reality = isReality,
            realityPublicKey = params["pbk"] ?: "",
            realityShortId = params["sid"] ?: "",
            realitySpiderX = params["spx"] ?: "",
            utls = isReality || fp.isNotBlank(),
            utlsFingerprint = fp,
            echConfigList = params["ech"] ?: "",
            pinnedCA256 = params["pcs"] ?: params["pinSHA256"] ?: "",
            verifyPeerCertByName = params["vcn"] ?: "",
            mldsa65Verify = params["pqv"] ?: "",
        )

        return Profile(
            id = newId(),
            name = (params["remarks"] ?: decode(fragment(link))).ifBlank { "$host:$port" },
            protocol = Protocol.VLESS,
            server = host,
            serverPort = port,
            uuid = uuid,
            encryption = params["encryption"] ?: "none",
            flow = params["flow"] ?: "",
            finalMask = params["fm"] ?: "",
            transport = transport,
            tls = tls,
        )
    }

    private fun parseVmess(link: String): Profile {
        val payload = stripScheme(link).trim()
        val json = decodeBase64ToString(payload.substringBefore("#"))
        if (json == null || !json.trim().startsWith("{")) {
            return parseVmessUrlStyle(link)
        }
        val map = Json.miniMap(json)

        val rawNet = (map["net"] ?: map["type"] ?: "tcp").toString().lowercase()
        val netType = when (rawNet) {
            "splithttp" -> "xhttp"
            else -> rawNet
        }
        val wsHost = (map["host"] ?: "").toString()
        val rawPath = (map["path"] ?: "").toString()
        val (_, parsedEd, parsedEh) = extractWsEarlyData(rawPath)
        val headerType = (map["type"] ?: "none").toString()
        val transport = Transport(
            type = netType,
            host = wsHost,
            path = rawPath,
            headerType = headerType,
            serviceName = if (netType == "grpc") rawPath else (map["serviceName"] ?: "").toString(),
            authority = if (netType == "grpc") wsHost else "",
            grpcMode = if (netType == "grpc") headerType else "gun",
            seed = if (netType == "kcp") rawPath else "",
            maxEarlyData = parsedEd,
            earlyDataHeader = parsedEh,
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
            insecure = map["allowInsecure"] == "1" || map["allowInsecure"] == true || map["insecure"] == "1" || map["verify_cert"] == false,
            alpn = (map["alpn"] ?: "").toString().split(",").map { it.trim() }.filter { it.isNotEmpty() },
            utls = fp.isNotBlank(),
            utlsFingerprint = fp.ifBlank { "chrome" },
            pinnedCA256 = (map["pcs"] ?: "").toString(),
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
                headerType = params["headerType"] ?: "none",
                serviceName = params["serviceName"] ?: "",
            ),
            tls = TlsSettings(
                enabled = security == "tls",
                serverName = params["sni"] ?: params["host"] ?: host,
                insecure = params["allowInsecure"] == "1" || params["insecure"] == "1",
            ),
        )
    }

    private fun parseTrojan(link: String): Profile {
        val rest = stripScheme(link)
        val (authority, query) = splitQuery(rest)
        val params = parseQuery(query)
        val (password, hostPart) = splitUserInfo(authority)
        val (host, port) = splitHostPort(hostPart)
        val rawType = (params["type"] ?: "tcp").lowercase()
        val type = if (rawType == "splithttp") "xhttp" else rawType
        val security = (params["security"] ?: "tls").lowercase()
        val isReality = security == "reality" || !params["pbk"].isNullOrBlank()
        val tlsEnabled = security != "none"
        val wsHost = params["host"] ?: ""
        val fp = params["fp"] ?: ""
        val rawPath = params["path"] ?: ""
        val (_, parsedEd, parsedEh) = extractWsEarlyData(
            rawPath = rawPath,
            explicitEd = params["ed"]?.toIntOrNull() ?: 0,
            explicitEh = params["eh"] ?: "",
        )
        return Profile(
            id = newId(),
            name = (params["remarks"] ?: decode(fragment(link))).ifBlank { "$host:$port" },
            protocol = Protocol.TROJAN,
            server = host,
            serverPort = port,
            password = decode(password),
            flow = params["flow"] ?: "",
            finalMask = params["fm"] ?: "",
            transport = Transport(
                type = type,
                host = wsHost,
                path = rawPath,
                headerType = params["headerType"] ?: "none",
                serviceName = params["serviceName"] ?: "",
                authority = params["authority"] ?: "",
                grpcMode = params["mode"] ?: "gun",
                xhttpMode = params["mode"] ?: "auto",
                xhttpExtra = params["extra"] ?: "",
                maxEarlyData = parsedEd,
                earlyDataHeader = parsedEh,
            ),
            tls = TlsSettings(
                enabled = tlsEnabled,
                serverName = params["sni"] ?: params["peer"] ?: wsHost.ifBlank { host },
                insecure = params["allowInsecure"] == "1" || params["allowInsecure"] == "true" || params["insecure"] == "1",
                alpn = params["alpn"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
                reality = isReality,
                realityPublicKey = params["pbk"] ?: "",
                realityShortId = params["sid"] ?: "",
                realitySpiderX = params["spx"] ?: "",
                utls = isReality || fp.isNotBlank(),
                utlsFingerprint = fp,
                echConfigList = params["ech"] ?: "",
                pinnedCA256 = params["pcs"] ?: params["pinSHA256"] ?: "",
                verifyPeerCertByName = params["vcn"] ?: "",
                mldsa65Verify = params["pqv"] ?: "",
            ),
        )
    }

    private fun parseSs(link: String): List<Profile> {
        val rest = stripScheme(link)
        val (authority, query) = splitQuery(rest)
        val params = parseQuery(query)
        val remarkName = (params["remarks"] ?: decode(fragment(link)))

        if (authority.contains("@")) {
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
            obfsPassword = params["obfs-password"] ?: "",
            portHopping = params["mport"] ?: "",
            tls = TlsSettings(
                enabled = true,
                serverName = params["sni"] ?: host,
                insecure = params["insecure"] == "1" || params["allowInsecure"] == "1" || params["insecure"] == "true",
                alpn = params["alpn"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: listOf("h3"),
                pinnedCA256 = params["pinSHA256"] ?: params["pcs"] ?: "",
            ),
        )
    }

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

    private fun parseJsonArray(json: String): List<Profile> {
        val result = mutableListOf<Profile>()
        val elements = runCatching {
            com.google.gson.JsonParser.parseString(json).asJsonArray
        }.getOrNull()
        if (elements != null) {
            for (el in elements) {
                if (!el.isJsonObject) continue
                val itemJson = app.nebulabox.core.serializer.JsonSerializer.toJsonPretty(el.asJsonObject) ?: el.toString()
                runCatching { result.addAll(parseJsonDocument(itemJson)) }
            }
            if (result.isNotEmpty()) return result
        }
        val parsed = Json.parse(json) as? List<*> ?: return emptyList()
        for (item in parsed) {
            val map = item as? Map<*, *> ?: continue
            val itemJson = Json.any(map)
            runCatching { result.addAll(parseJsonDocument(itemJson)) }
        }
        return result
    }

    fun parseJsonDocument(json: String): List<Profile> {
        val trimmed = json.trim()
        val root = Json.miniMap(trimmed)
        if (root.isEmpty()) throw ParseException("invalid JSON document")

        val coreConfig = app.nebulabox.core.serializer.JsonSerializer.fromJsonSafe(
            trimmed,
            app.nebulabox.core.model.CoreConfig::class.java,
        )
        val proxyOutbound = coreConfig?.getProxyOutbound()
        if (proxyOutbound != null) {
            val srvAddr = proxyOutbound.getServerAddress().orEmpty()
            val srvPort = proxyOutbound.getServerPort() ?: 0
            val remarks = coreConfig.remarks
                ?.takeIf { it.isNotBlank() }
                ?: proxyOutbound.tag.takeIf { it !in setOf("proxy", "out", "direct") }
                ?: if (srvAddr.isNotBlank()) {
                    if (srvPort > 0) "$srvAddr:$srvPort" else srvAddr
                } else {
                    System.currentTimeMillis().toString()
                }
            return listOf(
                Profile(
                    id = newId(),
                    name = remarks,
                    protocol = Protocol.CUSTOM,
                    server = srvAddr,
                    serverPort = srvPort,
                    customConfig = trimmed,
                ),
            )
        }

        @Suppress("UNCHECKED_CAST")
        val outbounds = root["outbounds"] as? List<Map<String, Any?>>
        val ignoredTypes = setOf("direct", "freedom", "block", "blackhole", "dns", "selector", "urltest")
        val primaryOutbound: Map<String, Any?>? = when {
            outbounds != null -> outbounds.firstOrNull {
                val t = (it["protocol"] ?: it["type"])?.toString()?.lowercase() ?: ""
                t.isNotEmpty() && t !in ignoredTypes
            } ?: outbounds.firstOrNull()
            root.containsKey("type") || root.containsKey("protocol") -> root
            else -> null
        }

        @Suppress("UNCHECKED_CAST")
        val settingsMap = primaryOutbound?.get("settings") as? Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val vnextFirst = (settingsMap?.get("vnext") as? List<Map<String, Any?>>)?.firstOrNull()
        @Suppress("UNCHECKED_CAST")
        val serversFirst = (settingsMap?.get("servers") as? List<Map<String, Any?>>)?.firstOrNull()

        val server = primaryOutbound?.get("server")?.toString()
            ?: settingsMap?.get("address")?.toString()
            ?: vnextFirst?.get("address")?.toString()
            ?: serversFirst?.get("address")?.toString()
            ?: ""
        val serverPort = (primaryOutbound?.get("server_port") as? Number)?.toInt()
            ?: (settingsMap?.get("port") as? Number)?.toInt()
            ?: (vnextFirst?.get("port") as? Number)?.toInt()
            ?: (serversFirst?.get("port") as? Number)?.toInt()
            ?: primaryOutbound?.get("server_port")?.toString()?.toIntOrNull()
            ?: settingsMap?.get("port")?.toString()?.toIntOrNull()
            ?: 0

        val rawName = root["remarks"]?.toString()
            ?: root["name"]?.toString()
            ?: primaryOutbound?.get("tag")?.toString()?.takeIf { it !in setOf("proxy", "out", "direct") }
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
        val proxy = outbounds.firstOrNull() ?: return null
        val protoStr = proxy["protocol"]?.toString()?.lowercase() ?: return null
        val protocol = Protocol.fromWire(protoStr) ?: return null
        val settings = proxy["settings"] as? Map<String, Any?> ?: emptyMap()
        val stream = proxy["streamSettings"] as? Map<String, Any?> ?: emptyMap()

        var server = settings["address"]?.toString() ?: ""
        var port = (settings["port"] as? Number)?.toInt()
            ?: settings["port"]?.toString()?.toIntOrNull()
            ?: 443
        var uuid = settings["id"]?.toString() ?: ""
        var password = settings["password"]?.toString() ?: settings["pass"]?.toString() ?: ""
        var username = settings["user"]?.toString() ?: ""
        var method = settings["method"]?.toString() ?: ""
        var flow = settings["flow"]?.toString() ?: ""
        var security = settings["security"]?.toString() ?: "auto"
        var encryption = settings["encryption"]?.toString() ?: "none"

        val vnext = (settings["vnext"] as? List<Map<String, Any?>>)?.firstOrNull()
        val servers = (settings["servers"] as? List<Map<String, Any?>>)?.firstOrNull()
        if (vnext != null) {
            server = vnext["address"]?.toString() ?: server
            port = (vnext["port"] as? Number)?.toInt() ?: vnext["port"]?.toString()?.toIntOrNull() ?: port
            val user = (vnext["users"] as? List<Map<String, Any?>>)?.firstOrNull()
            if (user != null) {
                uuid = user["id"]?.toString() ?: uuid
                flow = user["flow"]?.toString() ?: flow
                security = user["security"]?.toString() ?: security
                encryption = user["encryption"]?.toString() ?: encryption
            }
        } else if (servers != null) {
            server = servers["address"]?.toString() ?: server
            port = (servers["port"] as? Number)?.toInt() ?: servers["port"]?.toString()?.toIntOrNull() ?: port
            password = servers["password"]?.toString() ?: password
            method = servers["method"]?.toString() ?: method
        }

        val rawNet = (stream["network"]?.toString() ?: "tcp").lowercase()
        val netType = when (rawNet) {
            "splithttp" -> "xhttp"
            else -> rawNet
        }
        val wsSettings = stream["wsSettings"] as? Map<String, Any?>
        val httpUpgradeSettings = stream["httpupgradeSettings"] as? Map<String, Any?>
        val xhttpSettings = (stream["xhttpSettings"] ?: stream["splithttpSettings"]) as? Map<String, Any?>
        val grpcSettings = stream["grpcSettings"] as? Map<String, Any?>
        val hysteriaSettings = stream["hysteriaSettings"] as? Map<String, Any?>
        if (hysteriaSettings != null && password.isBlank()) {
            password = hysteriaSettings["auth"]?.toString() ?: ""
        }
        val wsHeaders = wsSettings?.get("headers") as? Map<String, Any?>
        val hostHeader = wsSettings?.get("host")?.toString()
            ?: wsHeaders?.get("Host")?.toString()
            ?: wsHeaders?.get("host")?.toString()
            ?: httpUpgradeSettings?.get("host")?.toString()
            ?: xhttpSettings?.get("host")?.toString()
            ?: ""
        val path = wsSettings?.get("path")?.toString()
            ?: httpUpgradeSettings?.get("path")?.toString()
            ?: xhttpSettings?.get("path")?.toString()
            ?: ""
        val serviceName = grpcSettings?.get("serviceName")?.toString() ?: ""
        val authority = grpcSettings?.get("authority")?.toString() ?: ""

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
            username = username,
            uuid = uuid,
            password = password,
            method = method,
            flow = flow,
            security = security,
            encryption = encryption,
            transport = Transport(
                type = netType,
                host = hostHeader,
                path = path,
                serviceName = serviceName,
                authority = authority,
                xhttpMode = xhttpSettings?.get("mode")?.toString() ?: "auto",
            ),
            tls = TlsSettings(
                enabled = isTls,
                serverName = sni,
                insecure = tlsSettings?.get("allowInsecure") == true,
                alpn = alpn,
                reality = isReality,
                realityPublicKey = realitySettings?.get("publicKey")?.toString() ?: "",
                realityShortId = realitySettings?.get("shortId")?.toString() ?: "",
                realitySpiderX = realitySettings?.get("spiderX")?.toString() ?: "",
                utls = isReality || fp.isNotBlank(),
                utlsFingerprint = fp.ifBlank { "chrome" },
            ),
        )
    }

    fun extractWsEarlyData(
        rawPath: String,
        explicitEd: Int = 0,
        explicitEh: String = "",
    ): Triple<String, Int, String> {
        if (rawPath.contains("?ed=")) {
            val clean = rawPath.substringBefore("?ed=").ifBlank { "/" }
            val after = rawPath.substringAfter("?ed=").substringBefore("&")
            val ed = after.toIntOrNull() ?: if (explicitEd > 0) explicitEd else 2048
            val eh = explicitEh.ifBlank { "Sec-WebSocket-Protocol" }
            return Triple(clean, ed, eh)
        }
        return Triple(
            rawPath,
            explicitEd,
            if (explicitEd > 0 && explicitEh.isBlank()) "Sec-WebSocket-Protocol" else explicitEh,
        )
    }

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
        URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
    }.getOrDefault(value)

    internal fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
