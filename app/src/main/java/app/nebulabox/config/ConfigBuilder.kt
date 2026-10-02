package app.nebulabox.config

import app.nebulabox.data.AppSettings
import app.nebulabox.data.Profile
import app.nebulabox.data.Protocol
import app.nebulabox.util.Json

/**
 * Turns a [Profile] plus [AppSettings] into a sing-box configuration document.
 *
 * This is the piece that decides whether a given protocol actually tunnels, so
 * it is kept as pure string building with no Android dependency and is covered
 * by unit tests in src/test.
 */
object ConfigBuilder {

    private const val TUN_TAG = "tun-in"
    private const val OUT_TAG = "out"
    private const val DIRECT_TAG = "direct"
    private const val BLOCK_TAG = "block"
    private const val DNS_TAG = "dns-out"

    fun build(profile: Profile, settings: AppSettings): String {
        val outbounds = mutableListOf<Map<String, Any?>>()
        outbounds += outboundMap(profile, settings)
        outbounds += mapOf("type" to "direct", "tag" to DIRECT_TAG)
        outbounds += mapOf("type" to "block", "tag" to BLOCK_TAG)
        outbounds += mapOf("type" to "dns", "tag" to DNS_TAG)

        val rules = mutableListOf<Map<String, Any?>>()

        // sniffed domains of the tunnel endpoint itself must never loop back
        rules += mapOf("action" to "sniff")

        rules += mapOf(
            "port" to 53,
            "outbound" to DNS_TAG,
        )

        if (settings.bypassLan) {
            rules += mapOf(
                "ip_is_private" to true,
                "outbound" to DIRECT_TAG,
            )
        }

        if (settings.blockAds) {
            rules += mapOf(
                "rule_set" to listOf("geosite-category-ads-all"),
                "outbound" to BLOCK_TAG,
            )
        }

        if (settings.bypassChina) {
            rules += mapOf(
                "rule_set" to listOf("geoip-cn", "geosite-cn"),
                "outbound" to DIRECT_TAG,
            )
        }

        if (settings.routeMode == "direct") {
            rules += mapOf("network" to "tcp", "outbound" to DIRECT_TAG)
        }

        val ruleSets = mutableListOf<Map<String, Any?>>()
        if (settings.blockAds) {
            ruleSets += mapOf(
                "type" to "remote",
                "tag" to "geosite-category-ads-all",
                "format" to "binary",
                "url" to "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-category-ads-all.srs",
            )
        }
        if (settings.bypassChina) {
            ruleSets += mapOf(
                "type" to "remote",
                "tag" to "geoip-cn",
                "format" to "binary",
                "url" to "https://raw.githubusercontent.com/SagerNet/sing-geoip/rule-set/geoip-cn.srs",
            )
            ruleSets += mapOf(
                "type" to "remote",
                "tag" to "geosite-cn",
                "format" to "binary",
                "url" to "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-cn.srs",
            )
        }

        val address = mutableListOf<String>()
        val ipv4 = settings.routeMode != "direct"
        if (ipv4) address += "172.19.0.1/30"
        if (settings.ipv6) address += "fdfe:dcba:9876::1/126"

        val tun = mutableMapOf<String, Any?>(
            "type" to "tun",
            "tag" to TUN_TAG,
            "address" to address,
            "auto_route" to true,
            "strict_route" to true,
            "mtu" to settings.mtu,
        )
        if (settings.ipv6) {
            tun["inet6_address"] = listOf("fdfe:dcba:9876::1/126")
        }

        val dns = mutableMapOf<String, Any?>(
            "servers" to listOf(
                mapOf(
                    "type" to "udp",
                    "tag" to "remote",
                    "address" to settings.remoteDns,
                    "detour" to OUT_TAG,
                ),
                mapOf(
                    "type" to "udp",
                    "tag" to "local",
                    "address" to settings.directDns,
                    "detour" to DIRECT_TAG,
                ),
                mapOf(
                    "type" to "hosts",
                    "tag" to "hosts",
                ),
            ),
            "final" to "remote",
            "strategy" to settings.dnsStrategy,
            "independent_cache" to true,
        )

        val route = mutableMapOf<String, Any?>(
            "rules" to rules,
            "auto_detect_interface" to true,
            "final" to OUT_TAG,
        )
        if (ruleSets.isNotEmpty()) {
            route["rule_set"] = ruleSets
        }

        val log = mapOf("level" to settings.logLevel, "timestamp" to true)

        val experimental = if (settings.tcpMux) {
            mapOf(
                "cache_file" to mapOf("enabled" to true, "path" to "cache.db"),
            )
        } else {
            null
        }

        val result = mutableMapOf<String, Any?>(
            "log" to log,
            "dns" to dns,
            "inbounds" to listOf(tun),
            "outbounds" to outbounds,
            "route" to route,
        )
        if (experimental != null) {
            result["experimental"] = experimental
        }

        return Json.obj(*result.map { (k, v) -> k to v }.toTypedArray())
    }

    /** Validates that the document is at least well formed before handing it to the core. */
    fun validate(config: String): String? {
        val parsed = Json.miniMap(config)
        if (parsed.isEmpty()) return "configuration is empty"
        val outbounds = parsed["outbounds"] as? List<*> ?: return "missing outbounds"
        if (outbounds.isEmpty()) return "no outbound configured"
        return null
    }

    private fun outboundMap(profile: Profile, settings: AppSettings): Map<String, Any?> {
        val base = mutableListOf<Pair<String, Any?>>(
            "tag" to OUT_TAG,
            "type" to profile.protocol.wire,
        )

        // wireguard and ssh do not use the shared stream/mux options
        val supportsMux = profile.protocol in setOf(
            Protocol.VLESS, Protocol.VMESS, Protocol.TROJAN, Protocol.SHADOWSOCKS,
        )

        if (profile.server.isNotBlank()) {
            base += "server" to profile.server
            base += "server_port" to profile.serverPort
        }

        when (profile.protocol) {
            Protocol.VLESS -> {
                base += "uuid" to profile.uuid
                if (profile.flow.isNotBlank()) base += "flow" to profile.flow
            }

            Protocol.VMESS -> {
                base += "uuid" to profile.uuid
                base += "alter_id" to profile.alterId
                if (profile.security.isNotBlank()) base += "security" to profile.security
            }

            Protocol.TROJAN -> base += "password" to profile.password

            Protocol.SHADOWSOCKS -> {
                base += "method" to profile.method
                base += "password" to profile.password
                if (profile.plugin.isNotBlank()) {
                    base += "plugin" to profile.plugin
                    if (profile.pluginOptions.isNotBlank()) {
                        base += "plugin_opts" to profile.pluginOptions
                    }
                }
            }

            Protocol.SOCKS, Protocol.HTTP -> {
                if (profile.username.isNotBlank()) base += "username" to profile.username
                if (profile.password.isNotBlank()) base += "password" to profile.password
            }

            Protocol.HYSTERIA2 -> {
                base += "password" to profile.password
                if (profile.upMbps > 0) base += "up_mbps" to profile.upMbps
                if (profile.downMbps > 0) base += "down_mbps" to profile.downMbps
            }

            Protocol.TUIC -> {
                base += "uuid" to profile.uuid
                if (profile.password.isNotBlank()) base += "password" to profile.password
            }

            Protocol.WIREGUARD -> {
                base += "private_key" to profile.privateKey
                base += "peer_public_key" to profile.peerPublicKey
                if (profile.preSharedKey.isNotBlank()) {
                    base += "pre_shared_key" to profile.preSharedKey
                }
                if (profile.localAddresses.isNotEmpty()) {
                    base += "local_address" to profile.localAddresses
                }
                if (profile.reserved.isNotEmpty()) base += "reserved" to profile.reserved
                base += "mtu" to profile.mtu
            }

            Protocol.SSH -> {
                if (profile.username.isNotBlank()) base += "user" to profile.username
                if (profile.password.isNotBlank()) base += "password" to profile.password
                base += "client_version" to profile.clientVersion
                if (profile.hostKeyAlgorithms.isNotEmpty()) {
                    base += "host_key_algorithms" to profile.hostKeyAlgorithms
                }
            }

            Protocol.NAIVE -> {
                base += "username" to profile.username
                base += "password" to profile.password
            }

            Protocol.DIRECT -> Unit
        }

        if (supportsMux && settings.tcpMux) {
            base += "multiplex" to mapOf(
                "enabled" to true,
                "protocol" to "h2mux",
                "max_connections" to 4,
                "padding" to settings.tcpMuxPadding,
            )
        }

        if (settings.tcpFastOpen) base += "tcp_fast_open" to true
        if (settings.sniffing) base += "domain_strategy" to "prefer_ipv4"

        if (profile.transport.type != "tcp" && profile.transport.type.isNotBlank()) {
            base += "transport" to transportMap(profile)
        }

        if (profile.tls.enabled) {
            base += "tls" to tlsMap(profile)
        }

        return base.toMap()
    }

    private fun transportMap(profile: Profile): Map<String, Any?> {
        val t = profile.transport
        val fields = mutableListOf<Pair<String, Any?>>("type" to t.type)
        when (t.type) {
            "ws", "httpupgrade" -> {
                if (t.path.isNotBlank()) fields += "path" to t.path
                if (t.host.isNotBlank()) fields += "headers" to mapOf("Host" to t.host)
                if (t.maxEarlyData > 0) {
                    fields += "max_early_data" to t.maxEarlyData
                    fields += "early_data_header_name" to t.earlyDataHeader.ifBlank { "Sec-WebSocket-Protocol" }
                }
            }

            "http" -> {
                if (t.path.isNotBlank()) fields += "path" to t.path
                if (t.host.isNotBlank()) fields += "host" to listOf(t.host)
            }

            "grpc" -> {
                if (t.serviceName.isNotBlank()) fields += "service_name" to t.serviceName
            }
        }
        return fields.toMap()
    }

    private fun tlsMap(profile: Profile): Map<String, Any?> {
        val tls = profile.tls
        val fields = mutableListOf<Pair<String, Any?>>("enabled" to true)
        if (tls.serverName.isNotBlank()) fields += "server_name" to tls.serverName
        if (tls.insecure) fields += "insecure" to true
        if (tls.alpn.isNotEmpty()) fields += "alpn" to tls.alpn
        if (tls.minVersion.isNotBlank()) fields += "min_version" to tls.minVersion
        if (tls.maxVersion.isNotBlank()) fields += "max_version" to tls.maxVersion

        if (tls.reality) {
            fields += "reality" to mapOf(
                "enabled" to true,
                "public_key" to tls.realityPublicKey,
                "short_id" to tls.realityShortId,
            )
        }
        if (tls.utls) {
            fields += "utls" to mapOf(
                "enabled" to true,
                "fingerprint" to tls.utlsFingerprint,
            )
        }
        return fields.toMap()
    }
}
