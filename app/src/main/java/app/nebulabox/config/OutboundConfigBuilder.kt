package app.nebulabox.config

import app.nebulabox.core.TunnelConstants
import app.nebulabox.core.enums.TransportType
import app.nebulabox.core.model.CoreConfig
import app.nebulabox.core.model.CoreConfig.OutboundBean.OutSettingsBean
import app.nebulabox.core.model.CoreConfig.OutboundBean.StreamSettingsBean
import app.nebulabox.core.serializer.JsonSerializer
import app.nebulabox.data.AppSettings
import app.nebulabox.data.Profile
import app.nebulabox.data.Protocol
import app.nebulabox.util.AppLogger
import app.nebulabox.util.ShareLinkParser
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.net.Inet6Address
import java.net.InetAddress

internal object OutboundConfigBuilder {
    fun buildOutboundWithGlobalSettings(
        p: Profile,
        settings: AppSettings,
    ): CoreConfig.OutboundBean {
        val outbound = buildOutbound(p, settings)
        updateOutboundWithGlobalSettings(p, settings, outbound)
        return outbound
    }

    private fun buildOutbound(
        p: Profile,
        settings: AppSettings,
    ): CoreConfig.OutboundBean {
        val serverHost = p.server.trim()
        return when (p.protocol) {
            Protocol.VLESS -> {
                val outbound = CoreConfig.OutboundBean(
                    tag = TunnelConstants.TAG_PROXY,
                    protocol = "vless",
                    settings = OutSettingsBean(
                        address = serverHost,
                        port = p.serverPort,
                        id = p.uuid.trim(),
                        encryption = p.encryption.ifBlank { "none" },
                        flow = p.flow.takeIf { it.isNotBlank() },
                        level = TunnelConstants.DEFAULT_LEVEL,
                    ),
                    streamSettings = StreamSettingsBean(),
                    mux = CoreConfig.OutboundBean.MuxBean(false),
                )
                populateTransportAndTls(p, outbound)
                outbound
            }

            Protocol.VMESS -> {
                val outbound = CoreConfig.OutboundBean(
                    tag = TunnelConstants.TAG_PROXY,
                    protocol = "vmess",
                    settings = OutSettingsBean(
                        address = serverHost,
                        port = p.serverPort,
                        id = p.uuid.trim(),
                        security = p.security.ifBlank { TunnelConstants.DEFAULT_SECURITY },
                        level = TunnelConstants.DEFAULT_LEVEL,
                    ),
                    streamSettings = StreamSettingsBean(),
                    mux = CoreConfig.OutboundBean.MuxBean(false),
                )
                populateTransportAndTls(p, outbound)
                outbound
            }

            Protocol.TROJAN -> {
                val outbound = CoreConfig.OutboundBean(
                    tag = TunnelConstants.TAG_PROXY,
                    protocol = "trojan",
                    settings = OutSettingsBean(
                        address = serverHost,
                        port = p.serverPort,
                        password = p.password,
                        flow = p.flow.takeIf { it.isNotBlank() },
                        level = TunnelConstants.DEFAULT_LEVEL,
                    ),
                    streamSettings = StreamSettingsBean(),
                    mux = CoreConfig.OutboundBean.MuxBean(false),
                )
                populateTransportAndTls(p, outbound)
                outbound
            }

            Protocol.SHADOWSOCKS -> {
                val outbound = CoreConfig.OutboundBean(
                    tag = TunnelConstants.TAG_PROXY,
                    protocol = "shadowsocks",
                    settings = OutSettingsBean(
                        address = serverHost,
                        port = p.serverPort,
                        password = p.password,
                        method = p.method.ifBlank { "chacha20-ietf-poly1305" },
                        level = TunnelConstants.DEFAULT_LEVEL,
                    ),
                    streamSettings = StreamSettingsBean(),
                    mux = CoreConfig.OutboundBean.MuxBean(false),
                )
                populateTransportAndTls(p, outbound)
                outbound
            }

            Protocol.SOCKS -> {
                CoreConfig.OutboundBean(
                    tag = TunnelConstants.TAG_PROXY,
                    protocol = "socks",
                    settings = OutSettingsBean(
                        address = serverHost,
                        port = p.serverPort,
                        user = p.username.takeIf { it.isNotBlank() },
                        pass = p.password.takeIf { it.isNotBlank() },
                        level = TunnelConstants.DEFAULT_LEVEL,
                    ),
                    streamSettings = StreamSettingsBean(),
                    mux = CoreConfig.OutboundBean.MuxBean(false),
                )
            }

            Protocol.HTTP -> {
                CoreConfig.OutboundBean(
                    tag = TunnelConstants.TAG_PROXY,
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
                    network = TransportType.HYSTERIA.type,
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

                CoreConfig.OutboundBean(
                    tag = TunnelConstants.TAG_PROXY,
                    protocol = "hysteria",
                    settings = OutSettingsBean(
                        address = serverHost,
                        port = p.serverPort,
                        version = 2,
                    ),
                    streamSettings = streamSettings,
                    mux = CoreConfig.OutboundBean.MuxBean(false),
                )
            }

            Protocol.WIREGUARD -> {
                val endpointHost = if (serverHost.contains(":") && !serverHost.startsWith("[")) {
                    "[$serverHost]"
                } else {
                    serverHost
                }
                val addrs = p.localAddresses.ifEmpty { listOf(TunnelConstants.WIREGUARD_LOCAL_ADDRESS_V4) }
                CoreConfig.OutboundBean(
                    tag = TunnelConstants.TAG_PROXY,
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

            else -> CoreConfig.OutboundBean(
                tag = TunnelConstants.TAG_PROXY,
                protocol = "freedom",
                settings = OutSettingsBean(),
                streamSettings = null,
                mux = null,
            )
        }
    }

    private fun populateTransportAndTls(
        p: Profile,
        outbound: CoreConfig.OutboundBean,
    ) {
        val stream = outbound.streamSettings ?: return
        val sni = populateTransportSettings(p, stream)
        populateTlsSettings(p, stream, sni)
        if (p.finalMask.isNotBlank()) {
            stream.finalmask = JsonSerializer.parseString(p.finalMask)
        }
    }

    private fun populateTransportSettings(
        p: Profile,
        streamSettings: StreamSettingsBean,
    ): String? {
        val transport = p.transport.type.ifBlank { TunnelConstants.DEFAULT_NETWORK }.lowercase()
        var sni: String? = null
        streamSettings.network = when (transport) {
            "splithttp" -> TransportType.XHTTP.type
            "http", "h2" -> TransportType.XHTTP.type
            else -> transport
        }

        when (streamSettings.network) {
            TransportType.TCP.type -> {
                val tcpSetting = StreamSettingsBean.TcpSettingsBean()
                if (p.transport.headerType.equals(TunnelConstants.HEADER_TYPE_HTTP, true)) {
                    tcpSetting.header.type = TunnelConstants.HEADER_TYPE_HTTP
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

            TransportType.KCP.type -> {
                streamSettings.kcpSettings = StreamSettingsBean.KcpSettingsBean()
            }

            TransportType.WS.type -> {
                val wsPath = ShareLinkParser.buildWsPathWithEd(p.transport)
                sni = p.transport.host
                streamSettings.wsSettings = StreamSettingsBean.WsSettingsBean(
                    host = p.transport.host,
                    path = wsPath,
                    headers = p.transport.headers.takeIf { it.isNotEmpty() },
                )
            }

            TransportType.HTTP_UPGRADE.type -> {
                val huPath = ShareLinkParser.buildWsPathWithEd(p.transport)
                sni = p.transport.host
                streamSettings.httpupgradeSettings = StreamSettingsBean.HttpupgradeSettingsBean(
                    host = p.transport.host,
                    path = huPath,
                )
            }

            TransportType.XHTTP.type -> {
                sni = p.transport.host
                streamSettings.xhttpSettings = StreamSettingsBean.XhttpSettingsBean(
                    host = p.transport.host,
                    path = p.transport.path.ifBlank { "/" },
                    mode = p.transport.xhttpMode.ifBlank { "auto" },
                    extra = JsonSerializer.parseString(p.transport.xhttpExtra.takeIf { it.isNotBlank() }),
                )
            }

            TransportType.GRPC.type -> {
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
            isReality -> TunnelConstants.REALITY
            isTls -> TunnelConstants.TLS
            else -> ""
        }
        streamSettings.security = streamSecurity.ifBlank { null }
        if (streamSettings.security == null) return

        val sniExt = when {
            p.tls.serverName.isNotBlank() -> p.tls.serverName
            !sni.isNullOrBlank() && !HostResolver.isPureIpAddress(sni) -> sni
            p.server.isNotBlank() && !HostResolver.isPureIpAddress(p.server) -> p.server
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

        if (streamSettings.security == TunnelConstants.TLS) {
            streamSettings.tlsSettings = tlsSetting
            streamSettings.realitySettings = null
        } else if (streamSettings.security == TunnelConstants.REALITY) {
            streamSettings.tlsSettings = null
            streamSettings.realitySettings = tlsSetting
        }
    }

    private fun updateOutboundWithGlobalSettings(
        p: Profile,
        settings: AppSettings,
        outbound: CoreConfig.OutboundBean,
    ) {
        updateOutboundFragment(settings, outbound)

        val allowMux = settings.tcpMux &&
            p.protocol in setOf(Protocol.VLESS, Protocol.VMESS) &&
            outbound.streamSettings?.network != TransportType.XHTTP.type
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
                listOf(TunnelConstants.WIREGUARD_LOCAL_ADDRESS_V4)
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
        outbound: CoreConfig.OutboundBean,
    ): Boolean {
        if (outbound.streamSettings?.security != TunnelConstants.TLS &&
            outbound.streamSettings?.security != TunnelConstants.REALITY
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
        if (outbound.streamSettings?.security == TunnelConstants.REALITY && packets == "tlshello") {
            packets = "1-3"
        } else if (outbound.streamSettings?.security == TunnelConstants.TLS && packets != "tlshello") {
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

}
