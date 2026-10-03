package app.nebulabox.config

import app.nebulabox.data.AppSettings
import app.nebulabox.data.Profile
import app.nebulabox.data.Protocol
import app.nebulabox.util.AppLogger
import app.nebulabox.util.ShareLinkParser
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.dto.V2rayConfig.OutboundBean.OutSettingsBean
import com.v2ray.ang.dto.V2rayConfig.OutboundBean.StreamSettingsBean
import com.v2ray.ang.enums.NetworkType
import com.v2ray.ang.util.JsonUtil
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Translates a [Profile] + [AppSettings] pair into an Xray-core v1.260327.1
 * (`2dust/AndroidLibXrayLite v26.9.30` / `v2rayNG 2.3.10`) JSON configuration.
 *
 * Built directly on `v2rayNG 2.3.10`'s [V2rayConfig], [JsonUtil], `CoreOutboundBuilder`,
 * and `CoreConfigManager`:
 *  1. Custom configs (`Protocol.CUSTOM`): Matches `CoreConfigManager.buildV2rayCustomConfig` &
 *     `getV2rayConfig4Speedtest` (`if (configContext.isCustom) return buildV2rayCustomConfig`),
 *     preserving original `inbounds` (`mixed-in`, `dns-in`), `outbounds`, `finalmask`, `echConfigList`,
 *     `dns`, and `routing` when `useHevTun == true`.
 *  2. Speedtest configs (`buildForSpeedtest`): Matches `CoreConfigManager.buildV2raySpeedtestConfig` +
 *     `postProcessForSpeedtest` (`inbounds.clear()`, `routing.rules.clear()`, `dns = null`, `mux = null`,
 *     and does NOT call `resolveOutboundDomainsToHosts`).
 *  3. Normal configs (`build`): Matches `CoreConfigManager.buildV2rayNormalConfig`, including
 *     `configureOutbounds`, `configureRouting`, `configureDns`, `configureLocalDns`, and
 *     `resolveOutboundDomainsToHosts` (with `HappyEyeballsBean.maxConcurrentTry = 4` so Xray's
 *     `TcpRaceDial` is always active).
 */
object ConfigBuilder {

    fun build(profile: Profile, settings: AppSettings): String {
        val s = settings.normalized()
        if (profile.protocol == Protocol.CUSTOM && profile.customConfig.isNotBlank()) {
            return buildV2rayCustomConfig(profile, s)
        }
        return buildV2rayNormalConfig(profile, s, includeGeoRules = true)
    }

    /**
     * Fallback config without any `geosite:` / `geoip:` rules in case geo asset files
     * are unavailable on disk.
     */
    fun buildWithoutGeoRules(profile: Profile, settings: AppSettings): String {
        val s = settings.normalized()
        if (profile.protocol == Protocol.CUSTOM && profile.customConfig.isNotBlank()) {
            return buildV2rayCustomConfig(profile, s)
        }
        return buildV2rayNormalConfig(profile, s, includeGeoRules = false)
    }

    /**
     * Matches `v2rayNG 2.3.10` `CoreConfigManager.getV2rayConfig4Speedtest`:
     *  - If `profile.protocol == Protocol.CUSTOM`, returns `buildV2rayCustomConfig` untouched!
     *  - Otherwise returns `buildV2raySpeedtestConfig`.
     */
    fun buildForSpeedtest(profile: Profile, settings: AppSettings): String {
        val s = settings.normalized()
        if (profile.protocol == Protocol.CUSTOM && profile.customConfig.isNotBlank()) {
            return buildV2rayCustomConfig(profile, s)
        }
        return buildV2raySpeedtestConfig(profile, s)
    }

    // --------------------------------------------------------- initV2rayConfig

    private fun initV2rayConfig(settings: AppSettings): V2rayConfig {
        val inbounds = arrayListOf(
            V2rayConfig.InboundBean(
                tag = "socks",
                port = settings.socksPort,
                protocol = "socks",
                listen = if (settings.allowLan) "0.0.0.0" else AppConfig.LOOPBACK,
                settings = V2rayConfig.InboundBean.InSettingsBean(
                    auth = "noauth",
                    udp = true,
                    userLevel = 8,
                ),
                sniffing = V2rayConfig.InboundBean.SniffingBean(
                    enabled = settings.sniffing || settings.fakeDns,
                    destOverride = buildList {
                        if (settings.sniffing) {
                            add("http")
                            add("tls")
                            add("quic")
                        }
                        if (settings.fakeDns) {
                            add("fakedns")
                        }
                    }.let { ArrayList(it.ifEmpty { listOf("http", "tls", "quic") }) },
                    routeOnly = settings.routeOnly,
                ),
            ),
        )

        if (!settings.useHevTun) {
            inbounds.add(
                V2rayConfig.InboundBean(
                    tag = "tun",
                    port = 0,
                    protocol = "tun",
                    settings = V2rayConfig.InboundBean.InSettingsBean(
                        name = "xray0",
                        mtu = settings.mtu,
                        userLevel = 8,
                    ),
                    sniffing = V2rayConfig.InboundBean.SniffingBean(
                        enabled = settings.sniffing || settings.fakeDns,
                        destOverride = buildList {
                            if (settings.sniffing) {
                                add("http")
                                add("tls")
                                add("quic")
                            }
                            if (settings.fakeDns) {
                                add("fakedns")
                            }
                        }.let { ArrayList(it.ifEmpty { listOf("http", "tls", "quic") }) },
                        routeOnly = settings.routeOnly,
                    ),
                ),
            )
        }

        val directOutbound = V2rayConfig.OutboundBean(
            tag = AppConfig.TAG_DIRECT,
            protocol = "freedom",
            settings = OutSettingsBean(),
            streamSettings = StreamSettingsBean(
                network = null,
                sockopt = StreamSettingsBean.SockoptBean(
                    domainStrategy = "UseIP",
                    happyEyeballs = StreamSettingsBean.HappyEyeballsBean(
                        tryDelayMs = 250,
                        interleave = 2,
                        maxConcurrentTry = 4,
                    ),
                ),
            ),
            mux = null,
        )

        val blockOutbound = V2rayConfig.OutboundBean(
            tag = AppConfig.TAG_BLOCKED,
            protocol = "blackhole",
            settings = OutSettingsBean(),
            streamSettings = null,
            mux = null,
        )

        return V2rayConfig(
            remarks = null,
            stats = emptyMap<String, Any>(),
            log = V2rayConfig.LogBean(loglevel = settings.logLevel),
            policy = V2rayConfig.PolicyBean(
                levels = mapOf(
                    "8" to V2rayConfig.PolicyBean.LevelBean(
                        handshake = 4,
                        connIdle = 300,
                        uplinkOnly = 1,
                        downlinkOnly = 1,
                    ),
                ),
                system = mapOf(
                    "statsOutboundUplink" to true,
                    "statsOutboundDownlink" to true,
                ),
            ),
            inbounds = inbounds,
            outbounds = arrayListOf(directOutbound, blockOutbound),
            dns = V2rayConfig.DnsBean(
                hosts = LinkedHashMap(),
                servers = ArrayList(),
            ),
            routing = V2rayConfig.RoutingBean(
                domainStrategy = settings.domainStrategy.ifBlank { "AsIs" },
                rules = ArrayList(),
            ),
        )
    }

    // --------------------------------------------------- buildV2rayNormalConfig

    private fun buildV2rayNormalConfig(
        profile: Profile,
        settings: AppSettings,
        includeGeoRules: Boolean,
    ): String {
        val v2rayConfig = initV2rayConfig(settings)
        v2rayConfig.remarks = profile.displayName
        v2rayConfig.log.loglevel = settings.logLevel

        if (settings.fakeDns) {
            v2rayConfig.fakedns = listOf(V2rayConfig.FakednsBean())
        }

        val proxyOutbound = buildOutboundWithGlobalSettings(profile, settings)
        v2rayConfig.outbounds.add(0, proxyOutbound)

        configureRouting(profile, settings, v2rayConfig, includeGeoRules)
        configureDns(settings, v2rayConfig, includeGeoRules)
        configureLocalDns(settings, v2rayConfig)
        if (!settings.useHevTun) {
            configureRootModeDns(v2rayConfig)
        }

        resolveOutboundDomainsToHosts(settings, v2rayConfig)

        return JsonUtil.toJsonPretty(v2rayConfig) ?: "{}"
    }

    // ------------------------------------------------- buildV2raySpeedtestConfig

    /**
     * Exact port of `v2rayNG 2.3.10` `CoreConfigManager.buildV2raySpeedtestConfig` + `postProcessForSpeedtest`:
     *  - Builds outbound via `buildOutboundWithGlobalSettings`
     *  - Does NOT call `resolveOutboundDomainsToHosts` (because `MeasureOutboundDelay` strips `dns`)
     *  - Clears `inbounds`, `routing.rules`, `dns`, `fakedns`, `stats`, `policy`, and `mux`.
     */
    private fun buildV2raySpeedtestConfig(
        profile: Profile,
        settings: AppSettings,
    ): String {
        val v2rayConfig = initV2rayConfig(settings)
        v2rayConfig.remarks = profile.displayName
        v2rayConfig.log.loglevel = settings.logLevel

        val proxyOutbound = buildOutboundWithGlobalSettings(profile, settings)
        v2rayConfig.outbounds.add(0, proxyOutbound)

        // postProcessForSpeedtest
        v2rayConfig.inbounds.clear()
        v2rayConfig.routing.domainStrategy = "AsIs"
        v2rayConfig.routing.rules.clear()
        v2rayConfig.dns = null
        v2rayConfig.fakedns = null
        v2rayConfig.stats = null
        v2rayConfig.policy = null
        v2rayConfig.outbounds.forEach { outbound ->
            outbound.mux = null
        }

        return JsonUtil.toJsonPretty(v2rayConfig) ?: "{}"
    }

    // --------------------------------------------------- buildV2rayCustomConfig

    /**
     * Exact port of `v2rayNG 2.3.10` `CoreConfigManager.buildV2rayCustomConfig`:
     * Preserves the Custom JSON's original `inbounds` (e.g. BPB Panel's `mixed-in` on port 10808
     * and `dns-in`), `outbounds`, `finalmask`, `echConfigList`, `dns`, and `routing` when
     * `useHevTun == true`, only injecting `stats` and `policy` for traffic counters.
     */
    private fun buildV2rayCustomConfig(
        profile: Profile,
        settings: AppSettings,
    ): String {
        val raw = profile.customConfig.trim()
        val json = JsonUtil.parseString(raw) ?: return raw

        // If the user pasted a single outbound object instead of a full config, wrap it
        if (!json.has("outbounds") && json.has("protocol") && json.has("settings")) {
            val outboundBean = JsonUtil.fromJsonSafe(raw, V2rayConfig.OutboundBean::class.java)
            if (outboundBean != null) {
                outboundBean.tag = AppConfig.TAG_PROXY
                val v2rayConfig = initV2rayConfig(settings)
                v2rayConfig.remarks = profile.displayName
                v2rayConfig.outbounds.add(0, outboundBean)
                configureRouting(profile, settings, v2rayConfig, includeGeoRules = true)
                configureDns(settings, v2rayConfig, includeGeoRules = true)
                return JsonUtil.toJsonPretty(v2rayConfig) ?: raw
            }
        }

        if (!json.has("stats")) {
            json.add("stats", JsonObject())
        }
        if (!json.has("policy")) {
            val policyObj = JsonObject()
            val systemObj = JsonObject()
            systemObj.addProperty("statsOutboundUplink", true)
            systemObj.addProperty("statsOutboundDownlink", true)
            policyObj.add("system", systemObj)
            json.add("policy", policyObj)
        }

        // When using hev-socks5-tunnel (!needTun()), return the custom JSON with its original inbounds/routing intact
        if (settings.useHevTun) {
            return JsonUtil.toJsonPretty(json) ?: raw
        }

        // Native TUN mode: inject "tun" inbound if missing
        val inbounds = json.getAsJsonArray("inbounds") ?: JsonArray().also { json.add("inbounds", it) }
        val hasTun = inbounds.any {
            it.isJsonObject && it.asJsonObject.get("tag")?.asString == "tun"
        }
        if (!hasTun) {
            val tunInbound = JsonObject().apply {
                addProperty("tag", "tun")
                addProperty("port", 0)
                addProperty("protocol", "tun")
                add("settings", JsonObject().apply {
                    addProperty("name", "xray0")
                    addProperty("MTU", settings.mtu)
                    addProperty("userLevel", 8)
                })
                add("sniffing", JsonObject().apply {
                    addProperty("enabled", true)
                    add("destOverride", JsonArray().apply {
                        add(JsonPrimitive("http"))
                        add(JsonPrimitive("tls"))
                        add(JsonPrimitive("quic"))
                    })
                })
            }
            inbounds.add(tunInbound)
        }

        return JsonUtil.toJsonPretty(json) ?: raw
    }

    // ------------------------------------------ CoreOutboundBuilder (v2rayNG 2.3.10)

    private fun buildOutboundWithGlobalSettings(
        p: Profile,
        settings: AppSettings,
    ): V2rayConfig.OutboundBean {
        val outbound = buildOutbound(p, settings)
        updateOutboundWithGlobalSettings(p, settings, outbound)
        return outbound
    }

    private fun buildOutbound(
        p: Profile,
        settings: AppSettings,
    ): V2rayConfig.OutboundBean {
        val serverHost = p.server.trim()
        return when (p.protocol) {
            Protocol.VLESS -> {
                val outbound = V2rayConfig.OutboundBean(
                    tag = AppConfig.TAG_PROXY,
                    protocol = "vless",
                    settings = OutSettingsBean(
                        address = serverHost,
                        port = p.serverPort,
                        id = p.uuid.trim(),
                        encryption = p.encryption.ifBlank { "none" },
                        flow = p.flow.takeIf { it.isNotBlank() },
                        level = AppConfig.DEFAULT_LEVEL,
                    ),
                    streamSettings = StreamSettingsBean(),
                    mux = V2rayConfig.OutboundBean.MuxBean(false),
                )
                populateTransportAndTls(p, outbound)
                outbound
            }

            Protocol.VMESS -> {
                val outbound = V2rayConfig.OutboundBean(
                    tag = AppConfig.TAG_PROXY,
                    protocol = "vmess",
                    settings = OutSettingsBean(
                        address = serverHost,
                        port = p.serverPort,
                        id = p.uuid.trim(),
                        security = p.security.ifBlank { AppConfig.DEFAULT_SECURITY },
                        level = AppConfig.DEFAULT_LEVEL,
                    ),
                    streamSettings = StreamSettingsBean(),
                    mux = V2rayConfig.OutboundBean.MuxBean(false),
                )
                populateTransportAndTls(p, outbound)
                outbound
            }

            Protocol.TROJAN -> {
                val outbound = V2rayConfig.OutboundBean(
                    tag = AppConfig.TAG_PROXY,
                    protocol = "trojan",
                    settings = OutSettingsBean(
                        address = serverHost,
                        port = p.serverPort,
                        password = p.password,
                        flow = p.flow.takeIf { it.isNotBlank() },
                        level = AppConfig.DEFAULT_LEVEL,
                    ),
                    streamSettings = StreamSettingsBean(),
                    mux = V2rayConfig.OutboundBean.MuxBean(false),
                )
                populateTransportAndTls(p, outbound)
                outbound
            }

            Protocol.SHADOWSOCKS -> {
                val outbound = V2rayConfig.OutboundBean(
                    tag = AppConfig.TAG_PROXY,
                    protocol = "shadowsocks",
                    settings = OutSettingsBean(
                        address = serverHost,
                        port = p.serverPort,
                        password = p.password,
                        method = p.method.ifBlank { "chacha20-ietf-poly1305" },
                        level = AppConfig.DEFAULT_LEVEL,
                    ),
                    streamSettings = StreamSettingsBean(),
                    mux = V2rayConfig.OutboundBean.MuxBean(false),
                )
                populateTransportAndTls(p, outbound)
                outbound
            }

            Protocol.SOCKS -> {
                V2rayConfig.OutboundBean(
                    tag = AppConfig.TAG_PROXY,
                    protocol = "socks",
                    settings = OutSettingsBean(
                        address = serverHost,
                        port = p.serverPort,
                        user = p.username.takeIf { it.isNotBlank() },
                        pass = p.password.takeIf { it.isNotBlank() },
                        level = AppConfig.DEFAULT_LEVEL,
                    ),
                    streamSettings = StreamSettingsBean(),
                    mux = V2rayConfig.OutboundBean.MuxBean(false),
                )
            }

            Protocol.HTTP -> {
                V2rayConfig.OutboundBean(
                    tag = AppConfig.TAG_PROXY,
                    protocol = "http",
                    settings = OutSettingsBean(
                        address = serverHost,
                        port = p.serverPort,
                        user = p.username.takeIf { it.isNotBlank() },
                        pass = p.password.takeIf { it.isNotBlank() },
                    ),
                    streamSettings = null,
                    mux = null,
                )
            }

            Protocol.HYSTERIA2, Protocol.TUIC -> {
                val streamSettings = StreamSettingsBean(
                    network = NetworkType.HYSTERIA.type,
                    hysteriaSettings = StreamSettingsBean.HysteriaSettingsBean(
                        version = 2,
                        auth = p.password,
                    ),
                )
                val sni = populateTransportSettings(p, streamSettings)
                populateTlsSettings(
                    p = p,
                    streamSettings = streamSettings,
                    sni = sni,
                    forceTls = true,
                    defaultAlpn = "h3",
                )

                val quicParams = StreamSettingsBean.FinalMaskBean.QuicParamsBean()
                if (p.upMbps > 0) quicParams.brutalUp = "${p.upMbps} mbps"
                if (p.downMbps > 0) quicParams.brutalDown = "${p.downMbps} mbps"
                if (!quicParams.brutalUp.isNullOrEmpty() && !quicParams.brutalDown.isNullOrEmpty()) {
                    quicParams.congestion = "brutal"
                }
                if (p.portHopping.isNotBlank()) {
                    quicParams.udpHop = StreamSettingsBean.FinalMaskBean.QuicParamsBean.UdpHopBean(
                        ports = p.portHopping,
                        interval = p.portHoppingInterval.ifBlank { "30" },
                    )
                }

                val hasQuicParams = quicParams.congestion != null ||
                    quicParams.brutalUp != null ||
                    quicParams.brutalDown != null ||
                    quicParams.udpHop != null
                val udpMasks = if (p.obfsPassword.isNotBlank()) {
                    listOf(
                        StreamSettingsBean.FinalMaskBean.MaskBean(
                            type = "salamander",
                            settings = StreamSettingsBean.FinalMaskBean.MaskBean.MaskSettingsBean(
                                password = p.obfsPassword,
                            ),
                        ),
                    )
                } else null

                if (hasQuicParams || udpMasks != null) {
                    streamSettings.finalmask = StreamSettingsBean.FinalMaskBean(
                        udp = udpMasks,
                        quicParams = if (hasQuicParams) quicParams else null,
                    )
                }

                V2rayConfig.OutboundBean(
                    tag = AppConfig.TAG_PROXY,
                    protocol = "hysteria",
                    settings = OutSettingsBean(
                        address = serverHost,
                        port = p.serverPort,
                        version = 2,
                    ),
                    streamSettings = streamSettings,
                    mux = V2rayConfig.OutboundBean.MuxBean(false),
                )
            }

            Protocol.WIREGUARD -> {
                val endpointHost = if (serverHost.contains(":") && !serverHost.startsWith("[")) {
                    "[$serverHost]"
                } else {
                    serverHost
                }
                val addrs = p.localAddresses.ifEmpty { listOf(AppConfig.WIREGUARD_LOCAL_ADDRESS_V4) }
                V2rayConfig.OutboundBean(
                    tag = AppConfig.TAG_PROXY,
                    protocol = "wireguard",
                    settings = OutSettingsBean(
                        secretKey = p.privateKey,
                        address = addrs,
                        peers = listOf(
                            OutSettingsBean.WireGuardBean(
                                publicKey = p.peerPublicKey,
                                preSharedKey = p.preSharedKey.takeIf { it.isNotBlank() },
                                endpoint = "$endpointHost:${p.serverPort}",
                            ),
                        ),
                        mtu = if (p.mtu in 1280..1500) p.mtu else 1420,
                        reserved = p.reserved.takeIf { it.isNotEmpty() },
                    ),
                    streamSettings = null,
                    mux = null,
                )
            }

            else -> V2rayConfig.OutboundBean(
                tag = AppConfig.TAG_PROXY,
                protocol = "freedom",
                settings = OutSettingsBean(),
                streamSettings = null,
                mux = null,
            )
        }
    }

    private fun populateTransportAndTls(
        p: Profile,
        outbound: V2rayConfig.OutboundBean,
    ) {
        val stream = outbound.streamSettings ?: return
        val sni = populateTransportSettings(p, stream)
        populateTlsSettings(p, stream, sni)
        if (p.finalMask.isNotBlank()) {
            stream.finalmask = JsonUtil.parseString(p.finalMask)
        }
    }

    private fun populateTransportSettings(
        p: Profile,
        streamSettings: StreamSettingsBean,
    ): String? {
        val transport = p.transport.type.ifBlank { AppConfig.DEFAULT_NETWORK }.lowercase()
        var sni: String? = null
        streamSettings.network = when (transport) {
            "splithttp" -> NetworkType.XHTTP.type
            "http", "h2" -> NetworkType.XHTTP.type
            else -> transport
        }

        when (streamSettings.network) {
            NetworkType.TCP.type -> {
                val tcpSetting = StreamSettingsBean.TcpSettingsBean()
                if (p.transport.headerType.equals(AppConfig.HEADER_TYPE_HTTP, true)) {
                    tcpSetting.header.type = AppConfig.HEADER_TYPE_HTTP
                    val hosts = p.transport.host.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val paths = p.transport.path.split(",").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf("/") }
                    sni = hosts.firstOrNull()
                    tcpSetting.header.request = StreamSettingsBean.TcpSettingsBean.HeaderBean.RequestBean(
                        path = paths,
                        headers = StreamSettingsBean.TcpSettingsBean.HeaderBean.RequestBean.HeadersBean(
                            Host = hosts.takeIf { it.isNotEmpty() },
                            userAgent = listOf("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.6478.122 Mobile Safari/537.36"),
                            acceptEncoding = listOf("gzip, deflate"),
                            Connection = listOf("keep-alive"),
                            Pragma = "no-cache",
                        ),
                        version = "1.1",
                        method = "GET",
                    )
                } else {
                    tcpSetting.header.type = "none"
                    sni = p.transport.host
                }
                streamSettings.tcpSettings = tcpSetting
            }

            NetworkType.KCP.type -> {
                streamSettings.kcpSettings = StreamSettingsBean.KcpSettingsBean()
            }

            NetworkType.WS.type -> {
                val wsPath = ShareLinkParser.buildWsPathWithEd(p.transport)
                sni = p.transport.host
                streamSettings.wsSettings = StreamSettingsBean.WsSettingsBean(
                    host = p.transport.host,
                    path = wsPath,
                    headers = p.transport.headers.takeIf { it.isNotEmpty() },
                )
            }

            NetworkType.HTTP_UPGRADE.type -> {
                val huPath = ShareLinkParser.buildWsPathWithEd(p.transport)
                sni = p.transport.host
                streamSettings.httpupgradeSettings = StreamSettingsBean.HttpupgradeSettingsBean(
                    host = p.transport.host,
                    path = huPath,
                )
            }

            NetworkType.XHTTP.type -> {
                sni = p.transport.host
                streamSettings.xhttpSettings = StreamSettingsBean.XhttpSettingsBean(
                    host = p.transport.host,
                    path = p.transport.path.ifBlank { "/" },
                    mode = p.transport.xhttpMode.ifBlank { "auto" },
                    extra = JsonUtil.parseString(p.transport.xhttpExtra.takeIf { it.isNotBlank() }),
                )
            }

            NetworkType.GRPC.type -> {
                val grpcMode = p.transport.grpcMode
                sni = p.transport.authority.ifBlank { p.transport.host }
                streamSettings.grpcSettings = StreamSettingsBean.GrpcSettingsBean(
                    serviceName = p.transport.serviceName.ifBlank { p.transport.path },
                    authority = sni?.takeIf { it.isNotBlank() },
                    multiMode = grpcMode.equals("multi", true),
                    idle_timeout = 60,
                    health_check_timeout = 20,
                )
            }
        }
        return sni
    }

    private fun populateTlsSettings(
        p: Profile,
        streamSettings: StreamSettingsBean,
        sni: String?,
        forceTls: Boolean = false,
        defaultAlpn: String? = null,
    ) {
        val isReality = p.tls.reality
        val isTls = p.tls.enabled || forceTls
        val streamSecurity = when {
            isReality -> AppConfig.REALITY
            isTls -> AppConfig.TLS
            else -> ""
        }
        streamSettings.security = streamSecurity.ifBlank { null }
        if (streamSettings.security == null) return

        val sniExt = when {
            p.tls.serverName.isNotBlank() -> p.tls.serverName
            !sni.isNullOrBlank() && !isPureIpAddress(sni) -> sni
            p.server.isNotBlank() && !isPureIpAddress(p.server) -> p.server
            else -> sni
        }

        val alpnList = when {
            p.tls.alpn.isNotEmpty() -> p.tls.alpn
            !defaultAlpn.isNullOrBlank() -> listOf(defaultAlpn)
            else -> null
        }

        val tlsSetting = StreamSettingsBean.TlsSettingsBean(
            allowInsecure = p.tls.insecure && p.tls.pinnedCA256.isBlank(),
            serverName = sniExt?.takeIf { it.isNotBlank() },
            fingerprint = p.tls.utlsFingerprint.takeIf { it.isNotBlank() }
                ?: if (isReality) "chrome" else null,
            alpn = alpnList,
            echConfigList = p.tls.echConfigList.takeIf { it.isNotBlank() },
            pinnedPeerCertSha256 = p.tls.pinnedCA256.takeIf { it.isNotBlank() },
            verifyPeerCertByName = p.tls.verifyPeerCertByName.takeIf { it.isNotBlank() },
            publicKey = p.tls.realityPublicKey.takeIf { it.isNotBlank() },
            shortId = p.tls.realityShortId.takeIf { it.isNotBlank() },
            spiderX = p.tls.realitySpiderX.takeIf { it.isNotBlank() },
            mldsa65Verify = p.tls.mldsa65Verify.takeIf { it.isNotBlank() },
        )

        if (streamSettings.security == AppConfig.TLS) {
            streamSettings.tlsSettings = tlsSetting
            streamSettings.realitySettings = null
        } else if (streamSettings.security == AppConfig.REALITY) {
            streamSettings.tlsSettings = null
            streamSettings.realitySettings = tlsSetting
        }
    }

    private fun updateOutboundWithGlobalSettings(
        p: Profile,
        settings: AppSettings,
        outbound: V2rayConfig.OutboundBean,
    ) {
        updateOutboundFragment(settings, outbound)

        val allowMux = settings.tcpMux &&
            p.protocol in setOf(Protocol.VLESS, Protocol.VMESS) &&
            outbound.streamSettings?.network != NetworkType.XHTTP.type
        if (allowMux) {
            val concurrency = if (p.protocol == Protocol.VLESS && p.flow.isNotBlank()) -1 else settings.muxConcurrency
            outbound.mux?.enabled = true
            outbound.mux?.concurrency = concurrency
            outbound.mux?.xudpConcurrency = settings.muxXudpConcurrency
            outbound.mux?.xudpProxyUDP443 = settings.muxXudpQuic
        } else {
            outbound.mux?.enabled = false
            outbound.mux?.concurrency = -1
        }

        if (outbound.protocol.equals("wireguard", true)) {
            val localTunAddr = if (outbound.settings?.address == null) {
                listOf(AppConfig.WIREGUARD_LOCAL_ADDRESS_V4)
            } else {
                outbound.settings?.address as List<*>
            }
            if (!settings.ipv6) {
                outbound.settings?.address = listOf(localTunAddr.first())
            }
        }
    }

    private fun updateOutboundFragment(
        settings: AppSettings,
        outbound: V2rayConfig.OutboundBean,
    ): Boolean {
        if (outbound.streamSettings?.security != AppConfig.TLS &&
            outbound.streamSettings?.security != AppConfig.REALITY
        ) {
            return false
        }
        if (outbound.streamSettings?.finalmask != null) {
            return false
        }

        val sniOrHost = (outbound.streamSettings?.tlsSettings?.serverName
            ?: outbound.getServerAddress()
            ?: "").lowercase()
        val isCloudflareWorkerDomain = (sniOrHost.endsWith(".workers.dev") || sniOrHost.endsWith(".pages.dev")) &&
            outbound.streamSettings?.tlsSettings?.echConfigList.isNullOrBlank()

        if (!settings.fragmentEnabled && !isCloudflareWorkerDomain) {
            return false
        }

        if (!settings.fragmentEnabled && isCloudflareWorkerDomain) {
            // Matches BPB-Worker-Panel default TLS fragment for *.workers.dev / *.pages.dev
            val tcpMask = StreamSettingsBean.FinalMaskBean.MaskBean(
                type = "fragment",
                settings = StreamSettingsBean.FinalMaskBean.MaskBean.MaskSettingsBean(
                    packets = "tlshello",
                    length = "100-200",
                    delay = "1-2",
                ),
            )
            outbound.streamSettings?.finalmask = StreamSettingsBean.FinalMaskBean(
                tcp = listOf(tcpMask),
            )
            return true
        }

        var packets = settings.fragmentPackets.ifBlank { "tlshello" }
        if (outbound.streamSettings?.security == AppConfig.REALITY && packets == "tlshello") {
            packets = "1-3"
        } else if (outbound.streamSettings?.security == AppConfig.TLS && packets != "tlshello") {
            packets = "tlshello"
        }

        val tcpMask = StreamSettingsBean.FinalMaskBean.MaskBean(
            type = "fragment",
            settings = StreamSettingsBean.FinalMaskBean.MaskBean.MaskSettingsBean(
                packets = packets,
                length = settings.fragmentLength.ifBlank { "50-100" },
                delay = settings.fragmentInterval.ifBlank { "10-20" },
            ),
        )
        val udpMask = StreamSettingsBean.FinalMaskBean.MaskBean(
            type = "noise",
            settings = StreamSettingsBean.FinalMaskBean.MaskBean.MaskSettingsBean(
                noise = listOf(
                    StreamSettingsBean.FinalMaskBean.MaskBean.MaskSettingsBean.NoiseMaskBean(
                        rand = "10-20",
                        delay = "10-16",
                    ),
                ),
            ),
        )
        outbound.streamSettings?.finalmask = StreamSettingsBean.FinalMaskBean(
            tcp = listOf(tcpMask),
            udp = listOf(udpMask),
        )
        return true
    }

    // --------------------------------------------------- Routing (v2rayNG 2.3.10)

    private fun configureRouting(
        profile: Profile,
        settings: AppSettings,
        v2rayConfig: V2rayConfig,
        includeGeoRules: Boolean,
    ) {
        v2rayConfig.routing.domainStrategy = settings.domainStrategy.ifBlank { "AsIs" }
        val rules = v2rayConfig.routing.rules

        // 1. Block UDP 443 on non-UDP protocols (matches v2rayNG custom_routing_global / custom_routing_white_iran)
        val isUdpNativeProtocol = profile.protocol in setOf(Protocol.HYSTERIA2, Protocol.TUIC, Protocol.WIREGUARD)
        if (!isUdpNativeProtocol) {
            rules.add(
                V2rayConfig.RoutingBean.RulesBean(
                    port = "443",
                    network = "udp",
                    outboundTag = AppConfig.TAG_BLOCKED,
                ),
            )
        }

        // 2. Optional Ad Blocking
        if (includeGeoRules && settings.blockAds) {
            rules.add(
                V2rayConfig.RoutingBean.RulesBean(
                    domain = listOf("geosite:category-ads-all"),
                    outboundTag = AppConfig.TAG_BLOCKED,
                ),
            )
        }

        // 3. Bypass LAN IP & domains
        if (settings.bypassLan) {
            if (includeGeoRules) {
                rules.add(
                    V2rayConfig.RoutingBean.RulesBean(
                        ip = listOf("ext:geoip-only-cn-private.dat:private"),
                        outboundTag = AppConfig.TAG_DIRECT,
                    ),
                )
                rules.add(
                    V2rayConfig.RoutingBean.RulesBean(
                        domain = listOf("geosite:private"),
                        outboundTag = AppConfig.TAG_DIRECT,
                    ),
                )
            } else {
                rules.add(
                    V2rayConfig.RoutingBean.RulesBean(
                        ip = listOf(
                            "10.0.0.0/8",
                            "127.0.0.0/8",
                            "172.16.0.0/12",
                            "192.168.0.0/16",
                            "169.254.0.0/16",
                        ),
                        outboundTag = AppConfig.TAG_DIRECT,
                    ),
                )
            }
        }

        // 4. Regional routing presets (matches v2rayNG custom_routing_white_iran / custom_routing_white / custom_routing_global)
        when (settings.routeMode) {
            "white_iran" -> {
                if (includeGeoRules) {
                    rules.add(
                        V2rayConfig.RoutingBean.RulesBean(
                            domain = listOf("domain:ir", "geosite:category-ir"),
                            outboundTag = AppConfig.TAG_DIRECT,
                        ),
                    )
                    rules.add(
                        V2rayConfig.RoutingBean.RulesBean(
                            ip = listOf("geoip:ir"),
                            outboundTag = AppConfig.TAG_DIRECT,
                        ),
                    )
                } else {
                    rules.add(
                        V2rayConfig.RoutingBean.RulesBean(
                            domain = listOf("domain:ir"),
                            outboundTag = AppConfig.TAG_DIRECT,
                        ),
                    )
                }
            }

            "rule" -> {
                if (includeGeoRules && settings.bypassChina) {
                    rules.add(
                        V2rayConfig.RoutingBean.RulesBean(
                            ip = listOf("ext:geoip-only-cn-private.dat:cn"),
                            outboundTag = AppConfig.TAG_DIRECT,
                        ),
                    )
                    rules.add(
                        V2rayConfig.RoutingBean.RulesBean(
                            domain = listOf("geosite:cn"),
                            outboundTag = AppConfig.TAG_DIRECT,
                        ),
                    )
                }
            }

            "direct" -> {
                rules.add(
                    V2rayConfig.RoutingBean.RulesBean(
                        port = "0-65535",
                        outboundTag = AppConfig.TAG_DIRECT,
                    ),
                )
            }

            else -> {
                // "global": matches custom_routing_global
                rules.add(
                    V2rayConfig.RoutingBean.RulesBean(
                        port = "0-65535",
                        outboundTag = AppConfig.TAG_PROXY,
                    ),
                )
            }
        }
    }

    // ------------------------------------------------------- DNS (v2rayNG 2.3.10)

    private fun configureDns(
        settings: AppSettings,
        v2rayConfig: V2rayConfig,
        includeGeoRules: Boolean,
    ) {
        val hosts = linkedMapOf<String, Any>(
            "domain:googleapis.cn" to "googleapis.com",
            "dns.alidns.com" to arrayListOf("223.5.5.5", "223.6.6.6", "2400:3200::1", "2400:3200:baba::1"),
            "one.one.one.one" to arrayListOf("1.1.1.1", "1.0.0.1", "2606:4700:4700::1111", "2606:4700:4700::1001"),
            "1dot1dot1dot1.cloudflare-dns.com" to arrayListOf("1.1.1.1", "1.0.0.1", "2606:4700:4700::1111", "2606:4700:4700::1001"),
            "dns.cloudflare.com" to arrayListOf("162.159.61.8", "172.64.41.8", "2a06:98c1:52::8", "2803:f800:53::8"),
            "cloudflare-dns.com" to arrayListOf("104.16.248.249", "104.16.249.249", "2606:4700::6810:f8f9", "2606:4700::6810:f9f9"),
            "dns.google" to arrayListOf("8.8.8.8", "8.8.4.4", "2001:4860:4860::8888", "2001:4860:4860::8844"),
            "dns.quad9.net" to arrayListOf("9.9.9.9", "149.112.112.112", "2620:fe::fe", "2620:fe::9"),
        )

        val servers = ArrayList<Any>()
        if (settings.fakeDns) {
            servers.add("fakedns")
        }

        val remoteDns = settings.remoteDns.trim().ifBlank { AppConfig. DELAY_TEST_URL }.let {
            if (it.isBlank()) "https://cloudflare-dns.com/dns-query" else settings.remoteDns.trim().ifBlank { "https://cloudflare-dns.com/dns-query" }
        }
        servers.add(remoteDns)

        val directDns = settings.directDns.trim().ifBlank { "223.5.5.5" }
        val domainDirectList = buildList {
            if (includeGeoRules && settings.routeMode == "white_iran") {
                add("domain:ir")
                add("geosite:category-ir")
            } else if (includeGeoRules && settings.bypassChina) {
                add("geosite:cn")
            }
        }
        if (domainDirectList.isNotEmpty()) {
            servers.add(
                V2rayConfig.DnsBean.ServersBean(
                    address = directDns,
                    domains = domainDirectList,
                    skipFallback = true,
                    tag = AppConfig.TAG_DOMESTIC_DNS,
                ),
            )
        }

        v2rayConfig.dns = V2rayConfig.DnsBean(
            servers = servers,
            hosts = hosts,
            tag = AppConfig.TAG_DNS,
        )

        // DNS routing rules at index 0 (exact port of v2rayNG CoreConfigManager.configureDns)
        if (domainDirectList.isNotEmpty()) {
            v2rayConfig.routing.rules.add(
                0,
                V2rayConfig.RoutingBean.RulesBean(
                    inboundTag = listOf(AppConfig.TAG_DOMESTIC_DNS),
                    outboundTag = AppConfig.TAG_DIRECT,
                ),
            )
        }
        v2rayConfig.routing.rules.add(
            0,
            V2rayConfig.RoutingBean.RulesBean(
                inboundTag = listOf(AppConfig.TAG_DNS),
                outboundTag = AppConfig.TAG_PROXY,
            ),
        )
    }

    /**
     * Exact port of `v2rayNG 2.3.10` `CoreConfigManager.configureLocalDns`:
     * Only hijacks port 53 to `dns-out` when `localDnsEnabled == true` (false by default!).
     */
    private fun configureLocalDns(
        settings: AppSettings,
        v2rayConfig: V2rayConfig,
    ) {
        if (!settings.localDnsEnabled) {
            return
        }
        if (v2rayConfig.outbounds.none { it.protocol == "dns" && it.tag == "dns-out" }) {
            v2rayConfig.outbounds.add(
                V2rayConfig.OutboundBean(
                    protocol = "dns",
                    tag = "dns-out",
                    settings = null,
                    streamSettings = null,
                    mux = null,
                ),
            )
        }
        v2rayConfig.routing.rules.add(
            0,
            V2rayConfig.RoutingBean.RulesBean(
                inboundTag = listOf("socks"),
                outboundTag = "dns-out",
                port = "53",
            ),
        )
    }

    private fun configureRootModeDns(v2rayConfig: V2rayConfig) {
        if (v2rayConfig.outbounds.none { it.protocol == "dns" && it.tag == "dns-out" }) {
            v2rayConfig.outbounds.add(
                V2rayConfig.OutboundBean(
                    protocol = "dns",
                    tag = "dns-out",
                    settings = null,
                    streamSettings = null,
                    mux = null,
                ),
            )
        }
        v2rayConfig.routing.rules.add(
            0,
            V2rayConfig.RoutingBean.RulesBean(
                inboundTag = listOf("tun"),
                outboundTag = "dns-out",
                port = "53",
            ),
        )
    }

    // ----------------------------- resolveOutboundDomainsToHosts (v2rayNG 2.3.10)

    private fun resolveOutboundDomainsToHosts(
        settings: AppSettings,
        v2rayConfig: V2rayConfig,
    ) {
        val proxyOutboundList = v2rayConfig.getAllProxyOutbound()
        val dns = v2rayConfig.dns ?: return
        val newHosts = dns.hosts?.toMutableMap() ?: mutableMapOf()
        val preferIpv6 = settings.preferIpv6

        for (item in proxyOutboundList) {
            val domain = item.getServerAddress()
            if (domain.isNullOrBlank() || isPureIpAddress(domain)) continue

            val resolvedIps = resolveDomainToIps(domain, preferIpv6)
            if (resolvedIps.isEmpty()) continue

            val sockopt = item.ensureSockopt()
            sockopt.domainStrategy = "UseIP"
            sockopt.happyEyeballs = StreamSettingsBean.HappyEyeballsBean(
                tryDelayMs = 250,
                prioritizeIPv6 = preferIpv6,
                interleave = 2,
                maxConcurrentTry = 4,
            )
            newHosts[domain] = if (resolvedIps.size == 1) {
                resolvedIps.first()
            } else {
                resolvedIps
            }
        }

        dns.hosts = newHosts
    }

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
        val obj = JsonUtil.parseString(config) ?: return "invalid JSON"
        val outbounds = obj.getAsJsonArray("outbounds")
        if (outbounds == null || outbounds.size() == 0) return "no outbounds block"
        null
    }.getOrElse { it.message ?: "invalid JSON" }
}
