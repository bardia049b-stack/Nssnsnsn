package app.nebulabox.config

import app.nebulabox.data.AppSettings
import app.nebulabox.data.Profile
import app.nebulabox.data.Protocol
import app.nebulabox.util.AppLogger
import app.nebulabox.util.Json
import app.nebulabox.util.ShareLinkParser
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Translates a [Profile] + [AppSettings] pair into an Xray-core v1.260327.1
 * (`2dust/AndroidLibXrayLite v26.9.30` / `v2rayNG 2.3.10`) JSON configuration.
 *
 * Key alignments with v2rayNG (`CoreConfigManager.kt` & `CoreOutboundBuilder.kt`):
 *  - Simplified flat outbound settings (`address`, `port`, `id`/`password`, `level = 8`)
 *  - WebSocket path preserves `?ed=2560` so Xray's `WebSocketConfig.Build()` enables 0-RTT early data
 *  - Pre-resolves outbound proxy server domain via Android system DNS into `dns.hosts`
 *    and enables `sockopt.domainStrategy = "UseIP"` + `happyEyeballs` (250ms race)
 *  - Intercepts UDP port 53 via `"protocol": "dns"` (`dns-out`) so TCP-only Cloudflare Workers
 *    resolve all DNS queries reliably over DoH/TCP
 *  - Supports Xray `finalmask` TLS Fragment & UDP Noise for bypassing ISP SNI/DPI filtering
 *  - Supports both `hev-socks5-tunnel` mode (`socks` inbound on 127.0.0.1:10808) and
 *    Xray Native TUN mode (`"protocol": "tun"` inbound)
 */
object ConfigBuilder {

    private fun mapObj(vararg pairs: Pair<String, Any?>): Map<String, Any?> {
        val m = LinkedHashMap<String, Any?>()
        for ((k, v) in pairs) {
            if (v != null) m[k] = v
        }
        return m
    }

    fun build(profile: Profile, settings: AppSettings): String {
        val s = settings.normalized()
        if (profile.protocol == Protocol.CUSTOM && profile.customConfig.isNotBlank()) {
            return buildCustomConfig(profile, s, forSpeedtest = false)
        }
        return buildStandardXrayConfig(profile, s, forSpeedtest = false)
    }

    /**
     * Generates a lightweight Xray config with no inbounds for `Libv2ray.measureOutboundDelay`,
     * matching v2rayNG's `CoreConfigManager.getV2rayConfig4Speedtest`.
     */
    fun buildForSpeedtest(profile: Profile, settings: AppSettings): String {
        val s = settings.normalized()
        if (profile.protocol == Protocol.CUSTOM && profile.customConfig.isNotBlank()) {
            return buildCustomConfig(profile, s, forSpeedtest = true)
        }
        return buildStandardXrayConfig(profile, s, forSpeedtest = true)
    }

    private fun buildStandardXrayConfig(
        profile: Profile,
        settings: AppSettings,
        forSpeedtest: Boolean,
    ): String {
        val dnsHosts = linkedMapOf<String, Any?>(
            "domain:googleapis.cn" to "googleapis.com",
        )

        val proxyOutbound = buildOutbound(profile, settings, dnsHosts)
        val directOutbound = mapObj(
            "tag" to "direct",
            "protocol" to "freedom",
            "settings" to emptyMap<String, Any?>(),
            "streamSettings" to mapObj(
                "sockopt" to mapObj(
                    "domainStrategy" to "UseIP",
                    "happyEyeballs" to mapObj(
                        "tryDelayMs" to 250,
                        "interleave" to 2,
                    ),
                ),
            ),
        )
        val blockOutbound = mapObj(
            "tag" to "block",
            "protocol" to "blackhole",
            "settings" to mapObj(
                "response" to mapObj("type" to "http"),
            ),
        )
        val dnsOutbound = mapObj(
            "tag" to "dns-out",
            "protocol" to "dns",
        )

        val outbounds = if (forSpeedtest) {
            listOf(proxyOutbound, directOutbound, blockOutbound)
        } else {
            listOf(proxyOutbound, directOutbound, blockOutbound, dnsOutbound)
        }

        val root = linkedMapOf<String, Any?>()
        root["remarks"] = profile.displayName
        root["log"] = mapObj(
            "loglevel" to if (forSpeedtest) "error" else settings.logLevel,
        )

        if (!forSpeedtest) {
            root["stats"] = emptyMap<String, Any?>()
            root["policy"] = mapObj(
                "levels" to mapObj(
                    "8" to mapObj(
                        "handshake" to 4,
                        "connIdle" to 300,
                        "uplinkOnly" to 1,
                        "downlinkOnly" to 1,
                    ),
                ),
                "system" to mapObj(
                    "statsOutboundUplink" to true,
                    "statsOutboundDownlink" to true,
                ),
            )
            root["inbounds"] = buildInbounds(settings)
            if (settings.fakeDns) {
                root["fakedns"] = listOf(
                    mapObj(
                        "ipPool" to "198.18.0.0/15",
                        "poolSize" to 10000,
                    ),
                )
            }
        }

        root["dns"] = buildDns(settings, dnsHosts)
        root["outbounds"] = outbounds
        root["routing"] = buildRouting(settings, forSpeedtest)

        return Json.any(root)
    }

    // --------------------------------------------------------------- Inbounds

    private fun buildInbounds(settings: AppSettings): List<Map<String, Any?>> {
        val list = mutableListOf<Map<String, Any?>>()
        val sniffingEnabled = settings.sniffing || settings.fakeDns
        val destOverride = mutableListOf<String>()
        if (settings.sniffing) {
            destOverride.addAll(listOf("http", "tls", "quic"))
        }
        if (settings.fakeDns) {
            if (settings.sniffing) {
                destOverride.add("fakedns+others")
                destOverride.remove("http")
                destOverride.remove("tls")
                destOverride.remove("quic")
            } else {
                destOverride.add("fakedns")
            }
        }

        val sniffingObj = mapObj(
            "enabled" to sniffingEnabled,
            "destOverride" to destOverride.ifEmpty { listOf("http", "tls", "quic") },
            "routeOnly" to settings.routeOnly,
        )

        // 1. Local SOCKS5 + HTTP proxy inbound (v2rayNG default port 10808)
        list.add(
            mapObj(
                "tag" to "socks",
                "port" to settings.socksPort,
                "listen" to if (settings.allowLan) "0.0.0.0" else "127.0.0.1",
                "protocol" to "socks",
                "settings" to mapObj(
                    "auth" to "noauth",
                    "udp" to true,
                    "userLevel" to 8,
                ),
                "sniffing" to sniffingObj,
            ),
        )

        // 2. Native Xray TUN inbound (used when useHevTun == false)
        if (!settings.useHevTun) {
            list.add(
                mapObj(
                    "tag" to "tun",
                    "port" to 0,
                    "protocol" to "tun",
                    "settings" to mapObj(
                        "name" to "xray0",
                        "MTU" to settings.mtu,
                        "userLevel" to 8,
                    ),
                    "sniffing" to sniffingObj,
                ),
            )
        }

        return list
    }

    // ------------------------------------------------------------------- DNS

    private fun buildDns(
        settings: AppSettings,
        dnsHosts: Map<String, Any?>,
    ): Map<String, Any?> {
        val servers = mutableListOf<Any?>()
        if (settings.fakeDns) {
            servers.add("fakedns")
        }

        val remote = settings.remoteDns.trim().ifBlank { "https://cloudflare-dns.com/dns-query" }
        servers.add(remote)

        val direct = settings.directDns.trim().ifBlank { "8.8.8.8" }
        if (settings.routeMode == "white_iran") {
            servers.add(
                mapObj(
                    "address" to direct,
                    "domains" to listOf("geosite:ir", "regexp:.*\\.ir$"),
                    "skipFallback" to true,
                    "tag" to "domestic-dns",
                ),
            )
        } else if (settings.bypassChina) {
            servers.add(
                mapObj(
                    "address" to direct,
                    "domains" to listOf("geosite:cn"),
                    "skipFallback" to true,
                    "tag" to "domestic-dns",
                ),
            )
        } else {
            servers.add(direct)
        }

        val queryStrategy = when {
            !settings.ipv6 || settings.dnsStrategy == "ipv4_only" -> "UseIPv4"
            settings.dnsStrategy == "ipv6_only" -> "UseIPv6"
            else -> "UseIP"
        }

        return mapObj(
            "hosts" to dnsHosts,
            "servers" to servers,
            "queryStrategy" to queryStrategy,
            "tag" to "dns-module",
        )
    }

    // --------------------------------------------------------------- Routing

    private fun buildRouting(
        settings: AppSettings,
        forSpeedtest: Boolean,
    ): Map<String, Any?> {
        val rules = mutableListOf<Map<String, Any?>>()

        if (!forSpeedtest) {
            // Route UDP port 53 DNS packets through Xray's internal dns-out handler
            // so DNS resolves reliably over TCP/DoH even when the proxy server is TCP-only (e.g. Cloudflare Workers)
            rules.add(
                mapObj(
                    "type" to "field",
                    "port" to "53",
                    "network" to "udp",
                    "outboundTag" to "dns-out",
                ),
            )
        }

        // Route Xray's remote DNS module through proxy, domestic DNS through direct
        rules.add(
            mapObj(
                "type" to "field",
                "inboundTag" to listOf("dns-module"),
                "outboundTag" to "proxy",
            ),
        )
        rules.add(
            mapObj(
                "type" to "field",
                "inboundTag" to listOf("domestic-dns"),
                "outboundTag" to "direct",
            ),
        )

        if (settings.blockAds) {
            rules.add(
                mapObj(
                    "type" to "field",
                    "domain" to listOf("geosite:category-ads-all"),
                    "outboundTag" to "block",
                ),
            )
        }

        if (settings.bypassLan) {
            rules.add(
                mapObj(
                    "type" to "field",
                    "ip" to listOf("geoip:private"),
                    "outboundTag" to "direct",
                ),
            )
            rules.add(
                mapObj(
                    "type" to "field",
                    "domain" to listOf("geosite:private"),
                    "outboundTag" to "direct",
                ),
            )
        }

        when (settings.routeMode) {
            "white_iran" -> {
                // v2rayNG custom_routing_white_iran preset
                rules.add(
                    mapObj(
                        "type" to "field",
                        "ip" to listOf("geoip:ir"),
                        "outboundTag" to "direct",
                    ),
                )
                rules.add(
                    mapObj(
                        "type" to "field",
                        "domain" to listOf(
                            "geosite:ir",
                            "regexp:.*\\.ir$",
                            "ext:iran.dat:ir",
                        ).filter { !it.startsWith("ext:") },
                        "outboundTag" to "direct",
                    ),
                )
            }

            "rule" -> {
                if (settings.bypassChina) {
                    rules.add(
                        mapObj(
                            "type" to "field",
                            "ip" to listOf("geoip:cn"),
                            "outboundTag" to "direct",
                        ),
                    )
                    rules.add(
                        mapObj(
                            "type" to "field",
                            "domain" to listOf("geosite:cn"),
                            "outboundTag" to "direct",
                        ),
                    )
                }
            }

            "direct" -> {
                rules.add(
                    mapObj(
                        "type" to "field",
                        "network" to "tcp,udp",
                        "outboundTag" to "direct",
                    ),
                )
            }
        }

        // Catch-all rule -> proxy (matches v2rayNG default catch-all)
        rules.add(
            mapObj(
                "type" to "field",
                "network" to "tcp,udp",
                "outboundTag" to "proxy",
            ),
        )

        return mapObj(
            "domainStrategy" to settings.domainStrategy.ifBlank { "AsIs" },
            "rules" to rules,
        )
    }

    // -------------------------------------------------------------- Outbound

    private fun buildOutbound(
        p: Profile,
        settings: AppSettings,
        dnsHosts: MutableMap<String, Any?>,
    ): Map<String, Any?> {
        if (p.protocol == Protocol.DIRECT) {
            return mapObj("tag" to "proxy", "protocol" to "freedom")
        }

        // Pre-resolve server domain to IP(s) via Android system DNS (matching v2rayNG resolveOutboundDomainsToHosts)
        val serverHost = p.server.trim()
        var resolvedStrategy: String? = null
        var happyEyeballs: Map<String, Any?>? = null
        if (serverHost.isNotBlank() && !isPureIpAddress(serverHost) && settings.outboundDomainResolve != "asis") {
            val resolvedIps = resolveDomainToIps(serverHost, preferIpv6 = settings.preferIpv6)
            if (resolvedIps.isNotEmpty()) {
                dnsHosts[serverHost] = if (resolvedIps.size == 1) resolvedIps.first() else resolvedIps
                resolvedStrategy = "UseIP"
                happyEyeballs = mapObj(
                    "tryDelayMs" to 250,
                    "interleave" to 2,
                    "prioritizeIPv6" to settings.preferIpv6,
                )
            } else {
                resolvedStrategy = if (settings.ipv6) "UseIP" else "UseIPv4"
            }
        }

        val protocolName = when (p.protocol) {
            Protocol.VLESS -> "vless"
            Protocol.VMESS -> "vmess"
            Protocol.TROJAN -> "trojan"
            Protocol.SHADOWSOCKS -> "shadowsocks"
            Protocol.SOCKS -> "socks"
            Protocol.HTTP -> "http"
            Protocol.HYSTERIA2, Protocol.TUIC -> "hysteria"
            Protocol.WIREGUARD -> "wireguard"
            else -> "vless"
        }

        val outSettings: Map<String, Any?> = when (p.protocol) {
            Protocol.VLESS -> mapObj(
                "address" to serverHost,
                "port" to p.serverPort,
                "id" to p.uuid.trim(),
                "encryption" to p.encryption.ifBlank { "none" },
                "flow" to p.flow.takeIf { it.isNotBlank() },
                "level" to 8,
            )

            Protocol.VMESS -> mapObj(
                "address" to serverHost,
                "port" to p.serverPort,
                "id" to p.uuid.trim(),
                "security" to p.security.ifBlank { "auto" },
                "level" to 8,
            )

            Protocol.TROJAN -> mapObj(
                "address" to serverHost,
                "port" to p.serverPort,
                "password" to p.password,
                "flow" to p.flow.takeIf { it.isNotBlank() },
                "level" to 8,
            )

            Protocol.SHADOWSOCKS -> mapObj(
                "address" to serverHost,
                "port" to p.serverPort,
                "password" to p.password,
                "method" to p.method.ifBlank { "chacha20-ietf-poly1305" },
                "level" to 8,
            )

            Protocol.SOCKS, Protocol.HTTP -> mapObj(
                "address" to serverHost,
                "port" to p.serverPort,
                "user" to p.username.takeIf { it.isNotBlank() },
                "pass" to p.password.takeIf { it.isNotBlank() },
                "level" to 8,
            )

            Protocol.HYSTERIA2, Protocol.TUIC -> mapObj(
                "address" to serverHost,
                "port" to p.serverPort,
                "version" to 2,
            )

            Protocol.WIREGUARD -> {
                val addrs = p.localAddresses.ifEmpty { listOf("172.16.0.2/32") }
                    .let { list -> if (settings.ipv6) list else list.filter { !it.contains(":") }.ifEmpty { listOf("172.16.0.2/32") } }
                val endpointHost = if (serverHost.contains(":") && !serverHost.startsWith("[")) "[$serverHost]" else serverHost
                mapObj(
                    "secretKey" to p.privateKey,
                    "address" to addrs,
                    "peers" to listOf(
                        mapObj(
                            "publicKey" to p.peerPublicKey,
                            "preSharedKey" to p.preSharedKey.takeIf { it.isNotBlank() },
                            "endpoint" to "$endpointHost:${p.serverPort}",
                        ),
                    ),
                    "mtu" to if (p.mtu in 1280..1500) p.mtu else 1420,
                    "reserved" to p.reserved.takeIf { it.isNotEmpty() },
                )
            }

            else -> emptyMap()
        }

        val streamSettings = if (p.protocol == Protocol.WIREGUARD) {
            if (resolvedStrategy != null) {
                mapObj(
                    "sockopt" to mapObj(
                        "domainStrategy" to resolvedStrategy,
                        "happyEyeballs" to happyEyeballs,
                    ),
                )
            } else {
                null
            }
        } else {
            buildStreamSettings(p, settings, resolvedStrategy, happyEyeballs)
        }

        // Mux configuration (matching v2rayNG CoreOutboundBuilder.updateOutboundWithGlobalSettings)
        val allowMux = settings.tcpMux &&
            p.protocol in setOf(Protocol.VLESS, Protocol.VMESS) &&
            p.transport.type.lowercase() != "xhttp"
        val muxObj = if (allowMux) {
            val concurrency = if (p.protocol == Protocol.VLESS && p.flow.isNotBlank()) -1 else settings.muxConcurrency
            mapObj(
                "enabled" to true,
                "concurrency" to concurrency,
                "xudpConcurrency" to settings.muxXudpConcurrency,
                "xudpProxyUDP443" to settings.muxXudpQuic,
            )
        } else {
            mapObj(
                "enabled" to false,
                "concurrency" to -1,
            )
        }

        return mapObj(
            "tag" to "proxy",
            "protocol" to protocolName,
            "settings" to outSettings,
            "streamSettings" to streamSettings,
            "mux" to muxObj,
        )
    }

    private fun buildStreamSettings(
        p: Profile,
        settings: AppSettings,
        resolvedStrategy: String?,
        happyEyeballs: Map<String, Any?>?,
    ): Map<String, Any?> {
        val stream = linkedMapOf<String, Any?>()
        val rawNet = if (p.protocol == Protocol.HYSTERIA2 || p.protocol == Protocol.TUIC) {
            "hysteria"
        } else {
            p.transport.type.ifBlank { "tcp" }.lowercase()
        }

        // Map deprecated h2/http transport in Xray v1.26+ to xhttp or ws if needed
        val network = when (rawNet) {
            "splithttp" -> "xhttp"
            "http", "h2" -> "xhttp"
            else -> rawNet
        }
        stream["network"] = network

        var transportSni = ""
        when (network) {
            "tcp" -> {
                if (p.transport.headerType.equals("http", ignoreCase = true)) {
                    val hosts = p.transport.host.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val paths = p.transport.path.split(",").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf("/") }
                    transportSni = hosts.firstOrNull().orEmpty()
                    stream["tcpSettings"] = mapObj(
                        "header" to mapObj(
                            "type" to "http",
                            "request" to mapObj(
                                "version" to "1.1",
                                "method" to "GET",
                                "path" to paths,
                                "headers" to mapObj(
                                    "Host" to hosts.takeIf { it.isNotEmpty() },
                                    "User-Agent" to listOf("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.6478.122 Mobile Safari/537.36"),
                                    "Accept-Encoding" to listOf("gzip, deflate"),
                                    "Connection" to listOf("keep-alive"),
                                    "Pragma" to "no-cache",
                                ),
                            ),
                        ),
                    )
                } else {
                    transportSni = p.transport.host
                    stream["tcpSettings"] = mapObj(
                        "header" to mapObj("type" to "none"),
                    )
                }
            }

            "ws" -> {
                // Exactly like v2rayNG CoreOutboundBuilder:
                // wsSettings.host = host, wsSettings.path = path (preserving ?ed=2560!)
                val wsHost = p.transport.host
                val wsPath = ShareLinkParser.buildWsPathWithEd(p.transport)
                transportSni = wsHost
                stream["wsSettings"] = mapObj(
                    "host" to wsHost,
                    "path" to wsPath,
                    "headers" to p.transport.headers.takeIf { it.isNotEmpty() },
                )
            }

            "httpupgrade" -> {
                val huHost = p.transport.host
                val huPath = ShareLinkParser.buildWsPathWithEd(p.transport)
                transportSni = huHost
                stream["httpupgradeSettings"] = mapObj(
                    "host" to huHost,
                    "path" to huPath,
                )
            }

            "xhttp" -> {
                val xHost = p.transport.host
                val xPath = p.transport.path.ifBlank { "/" }
                transportSni = xHost
                val extraParsed = if (p.transport.xhttpExtra.isNotBlank()) {
                    runCatching { Json.parse(p.transport.xhttpExtra) }.getOrNull()
                } else null
                stream["xhttpSettings"] = mapObj(
                    "host" to xHost,
                    "path" to xPath,
                    "mode" to p.transport.xhttpMode.ifBlank { "auto" },
                    "extra" to extraParsed,
                )
            }

            "grpc" -> {
                transportSni = p.transport.authority.ifBlank { p.transport.host }
                stream["grpcSettings"] = mapObj(
                    "serviceName" to p.transport.serviceName.ifBlank { p.transport.path },
                    "authority" to p.transport.authority.takeIf { it.isNotBlank() },
                    "multiMode" to (p.transport.grpcMode == "multi"),
                    "idle_timeout" to 60,
                    "health_check_timeout" to 20,
                )
            }

            "kcp" -> {
                stream["kcpSettings"] = mapObj(
                    "mtu" to 1350,
                    "tti" to 50,
                    "uplinkCapacity" to 12,
                    "downlinkCapacity" to 100,
                    "congestion" to false,
                    "readBufferSize" to 1,
                    "writeBufferSize" to 1,
                )
            }

            "hysteria" -> {
                stream["hysteriaSettings"] = mapObj(
                    "version" to 2,
                    "auth" to p.password,
                )
                val quicParams = linkedMapOf<String, Any?>()
                if (p.upMbps > 0) quicParams["brutalUp"] = "${p.upMbps} mbps"
                if (p.downMbps > 0) quicParams["brutalDown"] = "${p.downMbps} mbps"
                if (quicParams.isNotEmpty()) quicParams["congestion"] = "brutal"
                if (p.portHopping.isNotBlank()) {
                    quicParams["udpHop"] = mapObj(
                        "ports" to p.portHopping,
                        "interval" to p.portHoppingInterval.ifBlank { "30" },
                    )
                }
                val finalMask = linkedMapOf<String, Any?>()
                if (quicParams.isNotEmpty()) finalMask["quicParams"] = quicParams
                if (p.obfsPassword.isNotBlank()) {
                    finalMask["udp"] = listOf(
                        mapObj(
                            "type" to "salamander",
                            "settings" to mapObj("password" to p.obfsPassword),
                        ),
                    )
                }
                if (finalMask.isNotEmpty()) {
                    stream["finalmask"] = finalMask
                }
            }
        }

        // TLS / REALITY settings
        val isReality = p.tls.reality
        val isTls = p.tls.enabled || p.protocol == Protocol.HYSTERIA2 || p.protocol == Protocol.TUIC
        if (isReality) {
            stream["security"] = "reality"
            val sni = p.tls.serverName.ifBlank { transportSni.ifBlank { p.server } }
            stream["realitySettings"] = mapObj(
                "serverName" to sni,
                "fingerprint" to p.tls.utlsFingerprint.ifBlank { "chrome" },
                "publicKey" to p.tls.realityPublicKey,
                "shortId" to p.tls.realityShortId,
                "spiderX" to p.tls.realitySpiderX.ifBlank { "/" },
            )
        } else if (isTls) {
            stream["security"] = "tls"
            val sni = p.tls.serverName.ifBlank {
                when {
                    transportSni.isNotBlank() && !isPureIpAddress(transportSni) -> transportSni
                    !isPureIpAddress(p.server) -> p.server
                    else -> transportSni
                }
            }
            val alpnList = if (p.protocol == Protocol.HYSTERIA2 || p.protocol == Protocol.TUIC) {
                p.tls.alpn.ifEmpty { listOf("h3") }
            } else {
                p.tls.alpn.takeIf { it.isNotEmpty() }
            }
            stream["tlsSettings"] = mapObj(
                "allowInsecure" to (p.tls.insecure && p.tls.pinnedCA256.isBlank()),
                "serverName" to sni.takeIf { it.isNotBlank() },
                "fingerprint" to p.tls.utlsFingerprint.takeIf { it.isNotBlank() },
                "alpn" to alpnList,
                "echConfigList" to p.tls.echConfigList.takeIf { it.isNotBlank() },
                "pinnedPeerCertSha256" to p.tls.pinnedCA256.takeIf { it.isNotBlank() },
            )
        }

        // Xray TLS Fragment & UDP Noise (`finalmask`) when enabled in Settings
        if (settings.fragmentEnabled && (isTls || isReality) && stream["finalmask"] == null) {
            val packets = if (isReality && settings.fragmentPackets == "tlshello") {
                "1-3"
            } else {
                settings.fragmentPackets.ifBlank { "tlshello" }
            }
            stream["finalmask"] = mapObj(
                "tcp" to listOf(
                    mapObj(
                        "type" to "fragment",
                        "settings" to mapObj(
                            "packets" to packets,
                            "length" to settings.fragmentLength.ifBlank { "50-100" },
                            "delay" to settings.fragmentInterval.ifBlank { "10-20" },
                            "maxSplit" to settings.fragmentMaxSplit.ifBlank { "10" },
                        ),
                    ),
                ),
                "udp" to listOf(
                    mapObj(
                        "type" to "noise",
                        "settings" to mapObj(
                            "noise" to listOf(
                                mapObj(
                                    "rand" to "10-20",
                                    "delay" to "10-16",
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }

        // Sockopt (Happy Eyeballs + UseIP or TFO)
        val sockopt = linkedMapOf<String, Any?>()
        if (resolvedStrategy != null) {
            sockopt["domainStrategy"] = resolvedStrategy
        }
        if (happyEyeballs != null) {
            sockopt["happyEyeballs"] = happyEyeballs
        }
        if (settings.tcpFastOpen) {
            sockopt["tcpFastOpen"] = true
        }
        if (sockopt.isNotEmpty()) {
            stream["sockopt"] = sockopt
        }

        return stream
    }

    // -------------------------------------------------------- Custom JSON

    @Suppress("UNCHECKED_CAST")
    private fun buildCustomConfig(
        profile: Profile,
        settings: AppSettings,
        forSpeedtest: Boolean,
    ): String {
        val raw = profile.customConfig.trim()
        val parsed = runCatching { Json.miniMap(raw) }.getOrNull() ?: return raw

        // If it's already a full Xray config (has `outbounds` with `protocol`)
        val outbounds = parsed["outbounds"] as? List<Map<String, Any?>>
        if (outbounds != null && outbounds.any { it.containsKey("protocol") }) {
            val mutable = LinkedHashMap(parsed)
            if (forSpeedtest) {
                mutable.remove("inbounds")
            } else {
                mutable["inbounds"] = buildInbounds(settings)
                mutable["stats"] = emptyMap<String, Any?>()
                mutable["policy"] = mapObj(
                    "levels" to mapObj(
                        "8" to mapObj(
                            "handshake" to 4,
                            "connIdle" to 300,
                            "uplinkOnly" to 1,
                            "downlinkOnly" to 1,
                        ),
                    ),
                    "system" to mapObj(
                        "statsOutboundUplink" to true,
                        "statsOutboundDownlink" to true,
                    ),
                )
            }
            return Json.any(mutable)
        }

        // Single Xray outbound object `{ "protocol": "vless", ... }`
        if (parsed.containsKey("protocol") && parsed.containsKey("settings")) {
            val outboundMap = LinkedHashMap(parsed)
            outboundMap["tag"] = "proxy"
            val root = linkedMapOf<String, Any?>(
                "log" to mapObj("loglevel" to settings.logLevel),
                "dns" to buildDns(settings, emptyMap()),
                "outbounds" to listOf(
                    outboundMap,
                    mapObj("tag" to "direct", "protocol" to "freedom"),
                    mapObj("tag" to "block", "protocol" to "blackhole"),
                    mapObj("tag" to "dns-out", "protocol" to "dns"),
                ),
                "routing" to buildRouting(settings, forSpeedtest),
            )
            if (!forSpeedtest) {
                root["inbounds"] = buildInbounds(settings)
            }
            return Json.any(root)
        }

        return raw
    }

    // --------------------------------------------------- DNS Resolution Helper

    private fun isPureIpAddress(value: String): Boolean {
        val v = value.trim().removeSurrounding("[", "]")
        if (v.isEmpty()) return false
        val ipv4Regex = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
        if (ipv4Regex.matches(v)) return true
        if (v.contains(":")) return true
        return false
    }

    private fun resolveDomainToIps(domain: String, preferIpv6: Boolean): List<String> {
        return try {
            val addresses = InetAddress.getAllByName(domain)
            val sorted = addresses.sortedWith(
                compareBy { addr ->
                    when {
                        preferIpv6 && addr is Inet6Address -> 0
                        !preferIpv6 && addr is Inet4Address -> 0
                        else -> 1
                    }
                },
            )
            val ips = sorted.mapNotNull { it.hostAddress }.distinct()
            AppLogger.d("DNS", "Resolved $domain -> $ips")
            ips
        } catch (e: Exception) {
            AppLogger.w("DNS", "System DNS could not pre-resolve $domain: ${e.message}")
            emptyList()
        }
    }

    /** Basic sanity check before handing the config to Xray-core. */
    fun validate(config: String): String? = runCatching {
        val map = Json.miniMap(config)
        val outbounds = map["outbounds"] as? List<*>
        if (outbounds.isNullOrEmpty()) return "no outbounds block"
        null
    }.getOrElse { it.message ?: "invalid JSON" }
}
