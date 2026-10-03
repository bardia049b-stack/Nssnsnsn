package app.nebulabox.config

import app.nebulabox.data.AppSettings
import app.nebulabox.data.Profile
import app.nebulabox.data.Protocol
import app.nebulabox.util.Json
import java.net.URI

/**
 * Turns a [Profile] plus [AppSettings] into a sing-box v1.14+ configuration document.
 *
 * Also supports [Protocol.CUSTOM] (raw sing-box JSON configs — either full configs or
 * single outbound objects) and automatically migrates legacy sing-box 1.8–1.13 fields
 * (such as `dns`/`block` outbounds, legacy inbound `sniff`/`domain_strategy` fields,
 * and legacy DNS `address` server entries) so they run cleanly on sing-box v1.14.2.
 */
object ConfigBuilder {

    private const val TUN_TAG = "tun-in"
    private const val OUT_TAG = "out"
    private const val DIRECT_TAG = "direct"

    private val LEGACY_INBOUND_FIELDS = setOf(
        "sniff",
        "sniff_override_destination",
        "sniff_timeout",
        "domain_strategy",
        "udp_disable_domain_unmapping",
        "inet4_address",
        "inet6_address",
        "inet4_route_address",
        "inet6_route_address",
        "inet4_route_exclude_address",
        "inet6_route_exclude_address",
        "endpoint_independent_nat",
        "gso",
    )

    fun build(profile: Profile, settings: AppSettings): String {
        if (profile.protocol == Protocol.CUSTOM || profile.customConfig.isNotBlank()) {
            return buildCustom(profile.customConfig, settings)
        }
        val primaryOutbound = outboundMap(profile, settings)
        return buildWithPrimaryOutbound(primaryOutbound, OUT_TAG, settings)
    }

    private fun buildWithPrimaryOutbound(
        primaryOutbound: Map<String, Any?>,
        primaryTag: String,
        settings: AppSettings,
    ): String {
        val outbounds = mutableListOf<Map<String, Any?>>()
        outbounds += primaryOutbound
        if (primaryOutbound["type"] != "direct") {
            outbounds += mapOf("type" to "direct", "tag" to DIRECT_TAG)
        }

        val rules = mutableListOf<Map<String, Any?>>()
        rules += mapOf("action" to "sniff")
        rules += mapOf("protocol" to "dns", "action" to "hijack-dns")

        if (settings.bypassLan) {
            rules += mapOf(
                "ip_is_private" to true,
                "outbound" to DIRECT_TAG,
            )
        }

        if (settings.blockAds) {
            rules += mapOf(
                "rule_set" to listOf("geosite-category-ads-all"),
                "action" to "reject",
            )
        }

        if (settings.bypassChina) {
            rules += mapOf(
                "rule_set" to listOf("geoip-cn", "geosite-cn"),
                "outbound" to DIRECT_TAG,
            )
        }

        if (settings.routeMode == "direct") {
            rules += mapOf("network" to listOf("tcp", "udp"), "outbound" to DIRECT_TAG)
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

        val tun = defaultTunInbound(settings)

        val dns = mutableMapOf<String, Any?>(
            "servers" to listOf(
                buildDnsServer(settings.remoteDns, "remote", primaryTag, domainResolver = "local"),
                buildDnsServer(settings.directDns, "local", null),
            ),
            "final" to "remote",
            "strategy" to settings.dnsStrategy,
        )

        val route = mutableMapOf<String, Any?>(
            "rules" to rules,
            "auto_detect_interface" to true,
            "default_domain_resolver" to "local",
            "final" to primaryTag,
        )
        if (ruleSets.isNotEmpty()) {
            route["rule_set"] = ruleSets
        }

        val log = mapOf("level" to settings.logLevel, "timestamp" to true)

        val result = LinkedHashMap<String, Any?>()
        result["log"] = log
        result["dns"] = dns
        result["inbounds"] = listOf(tun)
        result["outbounds"] = outbounds
        result["route"] = route

        return Json.obj(*result.map { (k, v) -> k to v }.toTypedArray())
    }

    private fun defaultTunAddresses(settings: AppSettings): List<String> {
        val address = mutableListOf<String>()
        if (settings.routeMode != "direct") address += "172.19.0.1/30"
        if (settings.ipv6) address += "fdfe:dcba:9876::1/126"
        if (address.isEmpty()) address += "172.19.0.1/30"
        return address
    }

    private fun defaultTunInbound(settings: AppSettings): Map<String, Any?> = mapOf(
        "type" to "tun",
        "tag" to TUN_TAG,
        "interface_name" to "tun0",
        "address" to defaultTunAddresses(settings),
        "auto_route" to true,
        "strict_route" to false,
        "stack" to "mixed",
        "mtu" to settings.mtu,
    )

    /**
     * Converts a user-supplied DNS string (e.g. `1.1.1.1`, `https://1.1.1.1/dns-query`,
     * `tls://8.8.8.8`, `tcp://1.1.1.1`, `local`) into a sing-box v1.14 DNS server object.
     */
    private fun buildDnsServer(
        rawAddress: String,
        tag: String,
        detour: String?,
        domainResolver: String? = null,
    ): Map<String, Any?> {
        val addr = rawAddress.trim().ifBlank { "1.1.1.1" }
        val map = LinkedHashMap<String, Any?>()

        when {
            addr.equals("local", ignoreCase = true) || addr.startsWith("local://", true) -> {
                map["type"] = "local"
                map["tag"] = tag
            }
            addr.equals("fakeip", ignoreCase = true) -> {
                map["type"] = "fakeip"
                map["tag"] = tag
                map["inet4_range"] = "198.18.0.0/15"
                map["inet6_range"] = "fc00::/18"
            }
            addr.startsWith("https://", true) || addr.startsWith("h3://", true) -> {
                val isH3 = addr.startsWith("h3://", true)
                val normalized = if (isH3) "https://" + addr.substringAfter("://") else addr
                val uri = runCatching { URI(normalized) }.getOrNull()
                val host = uri?.host?.ifBlank { "1.1.1.1" } ?: "1.1.1.1"
                val port = if (uri != null && uri.port > 0) uri.port else null
                val path = uri?.path?.takeIf { it.isNotBlank() && it != "/dns-query" }
                map["type"] = if (isH3) "h3" else "https"
                map["tag"] = tag
                map["server"] = host
                if (port != null) map["server_port"] = port
                if (path != null) map["path"] = path
            }
            addr.startsWith("tls://", true) || addr.startsWith("quic://", true) ||
                addr.startsWith("tcp://", true) || addr.startsWith("udp://", true) -> {
                val scheme = addr.substringBefore("://").lowercase()
                val rest = addr.substringAfter("://").substringBefore("/")
                val host = rest.substringBefore(":")
                val port = rest.substringAfter(":", "").toIntOrNull()
                map["type"] = scheme
                map["tag"] = tag
                map["server"] = host.ifBlank { "1.1.1.1" }
                if (port != null) map["server_port"] = port
            }
            else -> {
                val clean = addr.substringBefore("/")
                val host = clean.substringBefore(":")
                val port = clean.substringAfter(":", "").toIntOrNull()
                map["type"] = "udp"
                map["tag"] = tag
                map["server"] = host.ifBlank { "1.1.1.1" }
                if (port != null) map["server_port"] = port
            }
        }

        if (!detour.isNullOrBlank() && map["type"] != "local" && map["type"] != "hosts" && map["type"] != "fakeip") {
            map["detour"] = detour
        }
        if (!domainResolver.isNullOrBlank() && domainResolver != tag && map["server"]?.toString()?.any { it.isLetter() } == true) {
            map["domain_resolver"] = domainResolver
        }
        return map
    }

    /**
     * Builds a runnable sing-box v1.14 configuration from a Custom JSON string.
     * Supports both:
     *  - A complete sing-box configuration object (`{"outbounds": [...], ...}`)
     *  - A single sing-box outbound object (`{"type": "vless", ...}`)
     */
    @Suppress("UNCHECKED_CAST")
    private fun buildCustom(rawJson: String, settings: AppSettings): String {
        val trimmed = rawJson.trim()
        val parsed = Json.miniMap(trimmed)
        if (parsed.isEmpty()) return trimmed

        // Case 1: Single outbound JSON object (has "type" but no "outbounds"/"endpoints")
        if (!parsed.containsKey("outbounds") && !parsed.containsKey("endpoints") && parsed.containsKey("type")) {
            val outbound = LinkedHashMap(parsed)
            LEGACY_INBOUND_FIELDS.forEach { outbound.remove(it) }
            val tag = (outbound["tag"] as? String)?.ifBlank { OUT_TAG } ?: OUT_TAG
            outbound["tag"] = tag
            return buildWithPrimaryOutbound(outbound, tag, settings)
        }

        // Case 2: Full sing-box config JSON
        val root = LinkedHashMap<String, Any?>(parsed)

        // 2a. Clean up outbounds: remove deprecated "dns" and "block" outbounds (removed in sing-box 1.13+)
        val rawOutbounds = (root["outbounds"] as? List<*>)?.mapNotNull { it as? Map<String, Any?> }.orEmpty()
        val removedDnsTags = mutableSetOf("dns-out")
        val removedBlockTags = mutableSetOf("block", "block-out")
        val cleanedOutbounds = mutableListOf<Map<String, Any?>>()

        for (ob in rawOutbounds) {
            val type = ob["type"]?.toString()?.lowercase() ?: ""
            val tag = ob["tag"]?.toString() ?: ""
            when (type) {
                "dns" -> {
                    if (tag.isNotEmpty()) removedDnsTags += tag
                }
                "block" -> {
                    if (tag.isNotEmpty()) removedBlockTags += tag
                }
                else -> {
                    val obCopy = LinkedHashMap(ob)
                    LEGACY_INBOUND_FIELDS.forEach { obCopy.remove(it) }
                    cleanedOutbounds += obCopy
                }
            }
        }
        if (cleanedOutbounds.none { it["type"] == "direct" }) {
            cleanedOutbounds += mapOf("type" to "direct", "tag" to DIRECT_TAG)
        }
        val directTag = cleanedOutbounds.firstOrNull { it["type"] == "direct" }?.get("tag")?.toString() ?: DIRECT_TAG
        val primaryProxyTag = cleanedOutbounds.firstOrNull {
            it["type"] != "direct" && it["type"] != "block" && it["type"] != "dns"
        }?.get("tag")?.toString() ?: directTag

        // Remove detour to empty direct outbound on all outbounds
        val finalOutbounds = cleanedOutbounds.map { ob ->
            val copy = LinkedHashMap(ob)
            if (copy["detour"]?.toString() == directTag) {
                copy.remove("detour")
            }
            copy
        }
        if (rawOutbounds.isNotEmpty()) {
            root["outbounds"] = finalOutbounds
        }

        // 2b. Ensure Android TUN inbound exists, uses VpnService address (172.19.0.1/30),
        // and strips legacy InboundOptions (`sniff`, `domain_strategy`, etc.) from ALL inbounds!
        val rawInbounds = (root["inbounds"] as? List<*>)?.mapNotNull { it as? Map<String, Any?> }.orEmpty()
        val hasTun = rawInbounds.any { it["type"] == "tun" }
        val updatedInbounds = if (hasTun) {
            rawInbounds.map { ib ->
                val ibCopy = LinkedHashMap(ib)
                LEGACY_INBOUND_FIELDS.forEach { ibCopy.remove(it) }
                if (ibCopy["type"] == "tun") {
                    ibCopy["interface_name"] = "tun0"
                    ibCopy["address"] = defaultTunAddresses(settings)
                    ibCopy["auto_route"] = true
                    ibCopy["strict_route"] = false
                    ibCopy["stack"] = ibCopy["stack"] ?: "mixed"
                    ibCopy["mtu"] = (ibCopy["mtu"] as? Number)?.toInt() ?: settings.mtu
                }
                ibCopy
            }
        } else {
            listOf(defaultTunInbound(settings)) + rawInbounds.map { ib ->
                val ibCopy = LinkedHashMap(ib)
                LEGACY_INBOUND_FIELDS.forEach { ibCopy.remove(it) }
                ibCopy
            }
        }
        root["inbounds"] = updatedInbounds

        // 2c. Migrate legacy DNS config to sing-box 1.14 format if present
        val rawDns = root["dns"] as? Map<String, Any?>
        var localDnsTag: String? = null
        if (rawDns != null) {
            val dnsCopy = LinkedHashMap(rawDns)
            dnsCopy.remove("independent_cache")
            dnsCopy.remove("fakeip")

            val rawServers = (dnsCopy["servers"] as? List<*>)?.mapNotNull { it as? Map<String, Any?> }
            if (rawServers != null) {
                val migratedServers = mutableListOf<Map<String, Any?>>()
                val rcodeBlockedTags = mutableSetOf<String>()

                for ((idx, srv) in rawServers.withIndex()) {
                    val tag = srv["tag"]?.toString() ?: "dns-$idx"
                    val legacyAddr = srv["address"]?.toString()
                    if (legacyAddr != null) {
                        if (legacyAddr.startsWith("rcode://", true) || legacyAddr.equals("block", true)) {
                            rcodeBlockedTags += tag
                            continue
                        }
                        val detour = srv["detour"]?.toString()?.takeIf {
                            it.isNotBlank() && it != directTag && it !in removedDnsTags && it !in removedBlockTags
                        }
                        val addrResolver = (srv["domain_resolver"] ?: srv["address_resolver"])?.toString()
                        val migrated = buildDnsServer(legacyAddr, tag, detour, addrResolver)
                        if (detour == null || migrated["type"] == "local") {
                            if (localDnsTag == null && migrated["type"] != "fakeip") localDnsTag = tag
                        }
                        migratedServers += migrated
                    } else {
                        val srvCopy = LinkedHashMap(srv)
                        srvCopy.remove("address_resolver")
                        srvCopy.remove("address_strategy")
                        srvCopy.remove("strategy")
                        val detour = srvCopy["detour"]?.toString()
                        if (detour == directTag || detour in removedDnsTags || detour in removedBlockTags) {
                            srvCopy.remove("detour")
                        }
                        val type = srvCopy["type"]?.toString() ?: "udp"
                        srvCopy["type"] = type
                        if (type != "local" && type != "hosts" && type != "fakeip" && !srvCopy.containsKey("server")) {
                            srvCopy["server"] = "1.1.1.1"
                        }
                        if (type == "local" || srvCopy["detour"] == null) {
                            if (localDnsTag == null && type != "hosts" && type != "fakeip") {
                                localDnsTag = tag
                            }
                        }
                        migratedServers += srvCopy
                    }
                }
                if (localDnsTag == null) {
                    localDnsTag = "local-dns-auto"
                    migratedServers += buildDnsServer(settings.directDns, localDnsTag, null)
                }
                dnsCopy["servers"] = migratedServers

                // Migrate legacy DNS rules (e.g. outbound rule or server pointing to rcode://block)
                val rawDnsRules = (dnsCopy["rules"] as? List<*>)?.mapNotNull { it as? Map<String, Any?> }
                if (rawDnsRules != null) {
                    val migratedDnsRules = rawDnsRules.mapNotNull { rule ->
                        // Drop legacy "outbound": "any" DNS rule which was replaced by default_domain_resolver
                        if (rule.containsKey("outbound")) return@mapNotNull null
                        val ruleCopy = LinkedHashMap(rule)
                        ruleCopy.remove("geosite")
                        ruleCopy.remove("geoip")
                        ruleCopy.remove("source_geoip")
                        ruleCopy.remove("rule_set_ip_cidr_accept_empty")
                        ruleCopy.remove("rule_set_ipcidr_match_source")
                        val targetServer = ruleCopy["server"]?.toString()
                        if (targetServer != null && (targetServer in rcodeBlockedTags || targetServer in removedBlockTags)) {
                            ruleCopy.remove("server")
                            ruleCopy["action"] = "reject"
                        }
                        val condKeys = ruleCopy.keys - setOf("action", "server", "disable_cache", "rewrite_ttl", "client_subnet", "type", "invert", "method", "no_drop")
                        if (condKeys.isEmpty()) null else ruleCopy
                    }
                    dnsCopy["rules"] = migratedDnsRules
                }
            }
            root["dns"] = dnsCopy
        }

        // 2d. Ensure route has auto_detect_interface and migrate removed dns/block outbound rules
        val rawRoute = (root["route"] as? Map<String, Any?>) ?: emptyMap()
        val routeCopy = LinkedHashMap(rawRoute)
        routeCopy["auto_detect_interface"] = true
        routeCopy.remove("geoip")
        routeCopy.remove("geosite")
        if (!routeCopy.containsKey("default_domain_resolver") && localDnsTag != null) {
            routeCopy["default_domain_resolver"] = localDnsTag
        }
        if (!routeCopy.containsKey("final")) {
            routeCopy["final"] = primaryProxyTag
        }

        val rawRules = (routeCopy["rules"] as? List<*>)?.mapNotNull { it as? Map<String, Any?> }.orEmpty()
        val migratedRules = mutableListOf<Map<String, Any?>>()
        var hasSniff = false
        var hasDnsHijack = false

        for (rule in rawRules) {
            val ruleCopy = LinkedHashMap(rule)
            ruleCopy.remove("geosite")
            ruleCopy.remove("geoip")
            ruleCopy.remove("source_geoip")
            ruleCopy.remove("rule_set_ipcidr_match_source")

            val action = ruleCopy["action"]?.toString()
            val ob = ruleCopy["outbound"]?.toString()
            if (action == "sniff") {
                hasSniff = true
                migratedRules += ruleCopy
                continue
            }
            if (action == "hijack-dns" || ruleCopy["protocol"] == "dns") {
                hasDnsHijack = true
            }

            if (ob != null && (ob in removedDnsTags || ruleCopy["protocol"] == "dns")) {
                ruleCopy.remove("outbound")
                ruleCopy["action"] = "hijack-dns"
                hasDnsHijack = true
            } else if (ob != null && ob in removedBlockTags) {
                ruleCopy.remove("outbound")
                ruleCopy["action"] = "reject"
            }

            val condKeys = ruleCopy.keys - setOf("action", "outbound", "type", "invert", "method", "no_drop", "sniffer", "timeout", "strategy")
            if (condKeys.isNotEmpty() || ruleCopy["action"] == "sniff" || ruleCopy["action"] == "hijack-dns") {
                migratedRules += ruleCopy
            }
        }

        if (!hasSniff) {
            migratedRules.add(0, mapOf("action" to "sniff"))
        }
        if (!hasDnsHijack) {
            migratedRules.add(1, mapOf("protocol" to "dns", "action" to "hijack-dns"))
        }
        routeCopy["rules"] = migratedRules
        root["route"] = routeCopy

        // 2e. Migrate experimental.cache_file.store_rdrc -> store_dns if present
        val rawExp = root["experimental"] as? Map<String, Any?>
        if (rawExp != null) {
            val expCopy = LinkedHashMap(rawExp)
            val cacheFile = expCopy["cache_file"] as? Map<String, Any?>
            if (cacheFile != null && cacheFile.containsKey("store_rdrc")) {
                val cfCopy = LinkedHashMap(cacheFile)
                val storeRdrc = cfCopy.remove("store_rdrc")
                if (storeRdrc == true) cfCopy["store_dns"] = true
                expCopy["cache_file"] = cfCopy
            }
            root["experimental"] = expCopy
        }

        return Json.obj(*root.map { (k, v) -> k to v }.toTypedArray())
    }

    /** Validates that the document is well formed before handing it to the core. */
    fun validate(config: String): String? {
        val parsed = Json.miniMap(config)
        if (parsed.isEmpty()) return "configuration is empty or invalid JSON"
        val outbounds = parsed["outbounds"] as? List<*>
        val endpoints = parsed["endpoints"] as? List<*>
        if (outbounds.isNullOrEmpty() && endpoints.isNullOrEmpty()) {
            return "missing outbounds in configuration"
        }
        return null
    }

    private fun outboundMap(profile: Profile, settings: AppSettings): Map<String, Any?> {
        val base = mutableListOf<Pair<String, Any?>>(
            "tag" to OUT_TAG,
            "type" to profile.protocol.wire,
        )

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

            Protocol.CUSTOM, Protocol.DIRECT -> Unit
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
                if (t.host.isNotBlank()) {
                    fields += if (t.type == "httpupgrade") {
                        "host" to t.host
                    } else {
                        "headers" to mapOf("Host" to t.host)
                    }
                }
                if (t.type == "ws" && t.maxEarlyData > 0) {
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
        if (tls.maxVersion.isNotBlank()) fields += "max_version" to tls.minVersion

        if (tls.reality) {
            fields += "reality" to mapOf(
                "enabled" to true,
                "public_key" to tls.realityPublicKey,
                "short_id" to tls.realityShortId,
            )
        }
        if (tls.utls || tls.reality) {
            fields += "utls" to mapOf(
                "enabled" to true,
                "fingerprint" to tls.utlsFingerprint.ifBlank { "chrome" },
            )
        }
        return fields.toMap()
    }
}
