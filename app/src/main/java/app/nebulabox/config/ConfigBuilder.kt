package app.nebulabox.config

import android.net.Uri
import app.nebulabox.data.AppSettings
import app.nebulabox.data.Profile
import app.nebulabox.data.Protocol
import org.json.JSONArray
import org.json.JSONObject

/**
 * Translates a [Profile] plus [AppSettings] into a complete sing-box v1.14.2 configuration.
 * Also automatically migrates legacy sing-box (1.8–1.13) custom JSON configs to 1.14.2 schema.
 */
object ConfigBuilder {

    fun build(profile: Profile, settings: AppSettings): String {
        if (profile.protocol == Protocol.CUSTOM) {
            return buildCustom(profile, settings)
        }

        val root = JSONObject()

        root.put(
            "log",
            JSONObject().apply {
                put("level", settings.logLevel)
                put("timestamp", true)
            },
        )

        val directDnsSpec = settings.directDns.ifBlank { "223.5.5.5" }
        val remoteDnsSpec = settings.remoteDns.ifBlank { "https://1.1.1.1/dns-query" }

        val dnsServers = JSONArray().apply {
            put(
                buildDnsServerJson(
                    tag = "dns-remote",
                    rawSpec = remoteDnsSpec,
                    detour = if (settings.routeMode == "direct") null else "proxy",
                    domainResolver = "dns-direct",
                ),
            )
            put(
                buildDnsServerJson(
                    tag = "dns-direct",
                    rawSpec = directDnsSpec,
                    detour = null,
                    domainResolver = null,
                ),
            )
        }

        val dnsObj = JSONObject().apply {
            put("servers", dnsServers)
            put("final", if (settings.routeMode == "direct") "dns-direct" else "dns-remote")
        }
        root.put("dns", dnsObj)

        val tunAddresses = JSONArray().apply {
            put("172.19.0.1/30")
            if (settings.ipv6) put("fdfe:dcba:9876::1/126")
        }

        val inbounds = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("type", "tun")
                    put("tag", "tun-in")
                    put("interface_name", "tun0")
                    put("address", tunAddresses)
                    put("mtu", settings.mtu)
                    put("auto_route", true)
                    put("strict_route", false)
                    put("stack", "mixed")
                },
            )
            put(
                JSONObject().apply {
                    put("type", "mixed")
                    put("tag", "mixed-in")
                    put("listen", "127.0.0.1")
                    put("listen_port", settings.mixedPort)
                },
            )
        }
        root.put("inbounds", inbounds)

        val outbounds = JSONArray().apply {
            put(buildOutbound(profile, tag = "proxy", domainResolver = "dns-direct"))
            put(JSONObject().put("type", "direct").put("tag", "direct"))
        }
        root.put("outbounds", outbounds)

        val routeRules = JSONArray().apply {
            put(JSONObject().put("action", "sniff"))
            put(
                JSONObject().apply {
                    put("protocol", "dns")
                    put("action", "hijack-dns")
                },
            )
            if (settings.bypassLan) {
                put(
                    JSONObject().apply {
                        put("ip_is_private", true)
                        put("outbound", "direct")
                    },
                )
            }
        }

        val route = JSONObject().apply {
            put("auto_detect_interface", true)
            put("default_domain_resolver", "dns-direct")
            put("final", if (settings.routeMode == "direct") "direct" else "proxy")
            put("rules", routeRules)
        }
        root.put("route", route)

        return root.toString(2)
    }

    /**
     * Converts a DNS server string (`https://1.1.1.1/dns-query`, `tls://8.8.8.8`, `223.5.5.5`, `local`, etc.)
     * into the sing-box 1.14.2 typed DNS server JSON object.
     */
    private fun buildDnsServerJson(
        tag: String,
        rawSpec: String,
        detour: String?,
        domainResolver: String?,
    ): JSONObject {
        val spec = rawSpec.trim()
        val obj = JSONObject()
        obj.put("tag", tag)

        when {
            spec.equals("local", ignoreCase = true) || spec.equals("system", ignoreCase = true) -> {
                obj.put("type", "local")
                return obj
            }
            spec.equals("fakeip", ignoreCase = true) -> {
                obj.put("type", "fakeip")
                obj.put("inet4_range", "198.18.0.0/15")
                obj.put("inet6_range", "fc00::/18")
                return obj
            }
            spec.contains("://") -> {
                val uri = runCatching { Uri.parse(spec) }.getOrNull()
                val scheme = uri?.scheme?.lowercase() ?: "udp"
                val host = uri?.host?.removeSurrounding("[", "]")?.takeIf { it.isNotBlank() } ?: "1.1.1.1"
                val port = uri?.port ?: -1
                val path = uri?.path?.takeIf { it.isNotBlank() }

                val type = when (scheme) {
                    "https", "doh" -> "https"
                    "h3", "http3" -> "h3"
                    "tls", "dot" -> "tls"
                    "quic", "doq" -> "quic"
                    "tcp" -> "tcp"
                    "udp", "dns" -> "udp"
                    else -> "udp"
                }
                obj.put("type", type)
                obj.put("server", host)
                if (port > 0) {
                    obj.put("server_port", port)
                }
                if ((type == "https" || type == "h3") && path != null && path != "/dns-query") {
                    obj.put("path", path)
                }
                if (!detour.isNullOrBlank()) {
                    obj.put("detour", detour)
                }
                if (!domainResolver.isNullOrBlank() && domainResolver != tag && !isIpLiteral(host)) {
                    obj.put("domain_resolver", domainResolver)
                }
            }
            else -> {
                val hostPort = spec.substringBefore('/')
                val host: String
                val port: Int
                if (hostPort.startsWith("[") && hostPort.contains("]:")) {
                    host = hostPort.substringAfter("[").substringBefore("]:")
                    port = hostPort.substringAfter("]:").toIntOrNull() ?: -1
                } else if (hostPort.count { it == ':' } == 1) {
                    host = hostPort.substringBefore(':')
                    port = hostPort.substringAfter(':').toIntOrNull() ?: -1
                } else {
                    host = hostPort.removeSurrounding("[", "]")
                    port = -1
                }
                obj.put("type", "udp")
                obj.put("server", host.ifBlank { "223.5.5.5" })
                if (port > 0 && port != 53) {
                    obj.put("server_port", port)
                }
                if (!detour.isNullOrBlank()) {
                    obj.put("detour", detour)
                }
                if (!domainResolver.isNullOrBlank() && domainResolver != tag && !isIpLiteral(host)) {
                    obj.put("domain_resolver", domainResolver)
                }
            }
        }
        return obj
    }

    private fun isIpLiteral(host: String): Boolean {
        val h = host.trim().removeSurrounding("[", "]")
        if (h.isEmpty()) return false
        if (h.contains(':')) return true // IPv6 literal
        val parts = h.split('.')
        return parts.size == 4 && parts.all { p -> p.toIntOrNull()?.let { it in 0..255 } == true }
    }

    /**
     * Adapts a user-supplied custom JSON config or single outbound JSON object so that it runs
     * cleanly inside Android's VpnService (`tun0`) on sing-box v1.14.2.
     */
    private fun buildCustom(profile: Profile, settings: AppSettings): String {
        val raw = profile.customConfig.trim()
        require(raw.isNotEmpty()) { "Custom JSON config is empty" }
        val parsed = JSONObject(raw)

        // Case 1: Single outbound object (has "type" and no "outbounds" array)
        if (!parsed.has("outbounds") && parsed.optString("type").isNotBlank()) {
            val root = JSONObject(build(profile.copy(protocol = Protocol.SOCKS, address = "127.0.0.1"), settings))
            val outboundCopy = JSONObject(parsed.toString())
            if (outboundCopy.optString("tag").isBlank()) {
                outboundCopy.put("tag", "proxy")
            }
            stripLegacyInboundFields(outboundCopy)
            outboundCopy.remove("domain_strategy")
            val primaryTag = outboundCopy.optString("tag", "proxy")
            val outbounds = JSONArray().apply {
                put(outboundCopy)
                if (primaryTag != "direct") {
                    put(JSONObject().put("type", "direct").put("tag", "direct"))
                }
            }
            root.put("outbounds", outbounds)
            root.optJSONObject("route")?.put("final", if (settings.routeMode == "direct") "direct" else primaryTag)
            root.optJSONObject("dns")?.optJSONArray("servers")?.let { servers ->
                for (i in 0 until servers.length()) {
                    val s = servers.optJSONObject(i) ?: continue
                    if (s.optString("detour") == "proxy") s.put("detour", primaryTag)
                }
            }
            return root.toString(2)
        }

        // Case 2: Full sing-box config JSON -> upgrade any 1.8–1.13 legacy fields to 1.14.2
        val root = JSONObject(parsed.toString())

        if (!root.has("log")) {
            root.put(
                "log",
                JSONObject().apply {
                    put("level", settings.logLevel)
                    put("timestamp", true)
                },
            )
        }

        // Remove top-level experimental deprecated fields if incompatible
        root.optJSONObject("experimental")?.optJSONObject("cache_file")?.remove("store_rdrc")

        // 1. Inspect and migrate outbounds
        val rawOutbounds = root.optJSONArray("outbounds") ?: JSONArray()
        val cleanOutbounds = JSONArray()
        val dnsOutboundTags = mutableSetOf<String>("dns-out")
        val blockOutboundTags = mutableSetOf<String>("block", "block-out")
        val emptyDirectTags = mutableSetOf<String>()
        var hasDirect = false
        var firstProxyTag: String? = null

        for (i in 0 until rawOutbounds.length()) {
            val ob = rawOutbounds.optJSONObject(i) ?: continue
            val type = ob.optString("type")
            val tag = ob.optString("tag")
            when (type) {
                "dns" -> {
                    if (tag.isNotBlank()) dnsOutboundTags.add(tag)
                    continue // Removed in sing-box 1.13.0
                }
                "block" -> {
                    if (tag.isNotBlank()) blockOutboundTags.add(tag)
                    continue // Removed in sing-box 1.13.0
                }
                "direct" -> {
                    hasDirect = true
                    val effectiveTag = tag.ifBlank { "direct" }
                    ob.put("tag", effectiveTag)
                    stripLegacyInboundFields(ob)
                    ob.remove("domain_strategy")
                    // Check if this direct outbound is empty (only has type & tag)
                    val keys = ob.keys().asSequence().toSet() - setOf("type", "tag")
                    if (keys.isEmpty()) {
                        emptyDirectTags.add(effectiveTag)
                    }
                    cleanOutbounds.put(ob)
                }
                else -> {
                    if (firstProxyTag == null && tag.isNotBlank()) {
                        firstProxyTag = tag
                    }
                    stripLegacyInboundFields(ob)
                    ob.remove("domain_strategy")
                    cleanOutbounds.put(ob)
                }
            }
        }

        if (!hasDirect) {
            cleanOutbounds.put(JSONObject().put("type", "direct").put("tag", "direct"))
            emptyDirectTags.add("direct")
        }

        // Strip detour to empty direct outbounds on all outbounds
        for (i in 0 until cleanOutbounds.length()) {
            val ob = cleanOutbounds.optJSONObject(i) ?: continue
            val detour = ob.optString("detour")
            if (detour.isNotBlank() && detour in emptyDirectTags) {
                ob.remove("detour")
            }
        }
        root.put("outbounds", cleanOutbounds)

        // 2. Migrate inbounds -> ensure Android tun0 inbound & strip legacy InboundOptions
        val tunAddresses = JSONArray().apply {
            put("172.19.0.1/30")
            if (settings.ipv6) put("fdfe:dcba:9876::1/126")
        }
        val tunInbound = JSONObject().apply {
            put("type", "tun")
            put("tag", "tun-in")
            put("interface_name", "tun0")
            put("address", tunAddresses)
            put("mtu", settings.mtu)
            put("auto_route", true)
            put("strict_route", false)
            put("stack", "mixed")
        }

        val existingInbounds = root.optJSONArray("inbounds")
        val newInbounds = JSONArray()
        var hasTun = false
        if (existingInbounds != null) {
            for (i in 0 until existingInbounds.length()) {
                val ib = existingInbounds.optJSONObject(i) ?: continue
                if (ib.optString("type") == "tun") {
                    if (!hasTun) {
                        val tag = ib.optString("tag").ifBlank { "tun-in" }
                        tunInbound.put("tag", tag)
                        newInbounds.put(tunInbound)
                        hasTun = true
                    }
                } else {
                    stripLegacyInboundFields(ib)
                    if (ib.optString("listen").isBlank()) {
                        ib.put("listen", "127.0.0.1")
                    }
                    newInbounds.put(ib)
                }
            }
        }
        if (!hasTun) {
            val merged = JSONArray()
            merged.put(tunInbound)
            for (i in 0 until newInbounds.length()) {
                merged.put(newInbounds.get(i))
            }
            root.put("inbounds", merged)
        } else {
            root.put("inbounds", newInbounds)
        }

        // 3. Migrate DNS to sing-box 1.14.2 format
        val dnsObj = root.optJSONObject("dns") ?: JSONObject()
        val legacyFakeIp = dnsObj.optJSONObject("fakeip")
        val fakeIpInet4 = legacyFakeIp?.optString("inet4_range")?.takeIf { it.isNotBlank() } ?: "198.18.0.0/15"
        val fakeIpInet6 = legacyFakeIp?.optString("inet6_range")?.takeIf { it.isNotBlank() } ?: "fc00::/18"
        dnsObj.remove("fakeip")
        dnsObj.remove("independent_cache")

        val rawDnsServers = dnsObj.optJSONArray("servers") ?: JSONArray()
        val cleanDnsServers = JSONArray()
        val blockDnsServerTags = mutableSetOf<String>("block", "dns-block", "rcode://success", "rcode://refused")
        var directDnsTag: String? = null
        var firstDnsTag: String? = null

        for (i in 0 until rawDnsServers.length()) {
            val s = rawDnsServers.optJSONObject(i) ?: continue
            val tag = s.optString("tag").ifBlank { "dns-$i" }
            val address = s.optString("address")
            val type = s.optString("type")

            if (address.startsWith("rcode://", ignoreCase = true) || type.equals("rcode", ignoreCase = true)) {
                blockDnsServerTags.add(tag)
                continue
            }

            val migrated = if (type.isBlank() || type == "legacy" || address.isNotBlank()) {
                val rawDetour = s.optString("detour").takeIf { it.isNotBlank() && it !in emptyDirectTags && it !in dnsOutboundTags && it !in blockOutboundTags }
                val rawResolver = s.optString("domain_resolver").ifBlank { s.optString("address_resolver") }.takeIf { it.isNotBlank() }
                if (address.equals("fakeip", ignoreCase = true) || type == "fakeip") {
                    JSONObject().apply {
                        put("type", "fakeip")
                        put("tag", tag)
                        put("inet4_range", s.optString("inet4_range").ifBlank { fakeIpInet4 })
                        put("inet6_range", s.optString("inet6_range").ifBlank { fakeIpInet6 })
                    }
                } else {
                    buildDnsServerJson(
                        tag = tag,
                        rawSpec = address.ifBlank { s.optString("server").ifBlank { "1.1.1.1" } },
                        detour = rawDetour,
                        domainResolver = rawResolver,
                    )
                }
            } else {
                val copy = JSONObject(s.toString())
                copy.put("tag", tag)
                copy.remove("address")
                copy.remove("address_resolver")
                copy.remove("address_strategy")
                copy.remove("strategy")
                val d = copy.optString("detour")
                if (d.isNotBlank() && (d in emptyDirectTags || d in dnsOutboundTags || d in blockOutboundTags)) {
                    copy.remove("detour")
                }
                copy
            }

            if (firstDnsTag == null && migrated.optString("type") != "fakeip") {
                firstDnsTag = tag
            }
            if (directDnsTag == null && migrated.optString("detour").isBlank() && migrated.optString("type") != "fakeip") {
                directDnsTag = tag
            }
            cleanDnsServers.put(migrated)
        }

        // Ensure at least one direct DNS server exists for domain resolution
        if (directDnsTag == null) {
            directDnsTag = "dns-direct"
            cleanDnsServers.put(
                buildDnsServerJson(
                    tag = directDnsTag,
                    rawSpec = settings.directDns.ifBlank { "223.5.5.5" },
                    detour = null,
                    domainResolver = null,
                ),
            )
        }
        if (firstDnsTag == null) {
            firstDnsTag = directDnsTag
        }

        // Ensure any domain-based DNS server has domain_resolver set
        val validDnsTags = mutableSetOf<String>()
        for (i in 0 until cleanDnsServers.length()) {
            cleanDnsServers.optJSONObject(i)?.optString("tag")?.takeIf { it.isNotBlank() }?.let { validDnsTags.add(it) }
        }
        for (i in 0 until cleanDnsServers.length()) {
            val s = cleanDnsServers.optJSONObject(i) ?: continue
            val serverHost = s.optString("server")
            val existingResolver = s.optString("domain_resolver")
            if (existingResolver.isNotBlank() && existingResolver !in validDnsTags) {
                s.put("domain_resolver", directDnsTag)
            } else if (serverHost.isNotBlank() && !isIpLiteral(serverHost) && s.optString("domain_resolver").isBlank() && s.optString("tag") != directDnsTag) {
                s.put("domain_resolver", directDnsTag)
            }
        }

        dnsObj.put("servers", cleanDnsServers)
        val dnsFinal = dnsObj.optString("final")
        if (dnsFinal.isBlank() || dnsFinal !in validDnsTags) {
            dnsObj.put("final", firstDnsTag ?: directDnsTag)
        }

        // Migrate dns.rules
        val rawDnsRules = dnsObj.optJSONArray("dns_rules") ?: dnsObj.optJSONArray("rules")
        if (rawDnsRules != null) {
            val cleanDnsRules = JSONArray()
            for (i in 0 until rawDnsRules.length()) {
                val r = rawDnsRules.optJSONObject(i) ?: continue
                val cleaned = sanitizeDnsRule(r, validDnsTags, blockDnsServerTags, blockOutboundTags)
                if (cleaned != null) {
                    cleanDnsRules.put(cleaned)
                }
            }
            dnsObj.put("rules", cleanDnsRules)
        }
        root.put("dns", dnsObj)

        // 4. Migrate route
        val route = root.optJSONObject("route") ?: JSONObject()
        route.put("auto_detect_interface", true)
        route.remove("geoip")
        route.remove("geosite")

        val existingDefaultResolver = route.optString("default_domain_resolver")
        if (existingDefaultResolver.isBlank() || (route.optJSONObject("default_domain_resolver") == null && existingDefaultResolver !in validDnsTags)) {
            route.put("default_domain_resolver", directDnsTag)
        }

        val validOutboundTags = mutableSetOf<String>()
        for (i in 0 until cleanOutbounds.length()) {
            cleanOutbounds.optJSONObject(i)?.optString("tag")?.takeIf { it.isNotBlank() }?.let { validOutboundTags.add(it) }
        }
        val routeFinal = route.optString("final")
        if (routeFinal.isBlank() || routeFinal !in validOutboundTags) {
            route.put("final", firstProxyTag ?: "direct")
        }

        val rawRouteRules = route.optJSONArray("rules") ?: JSONArray()
        val cleanRouteRules = JSONArray()
        cleanRouteRules.put(JSONObject().put("action", "sniff"))
        cleanRouteRules.put(JSONObject().put("protocol", "dns").put("action", "hijack-dns"))

        for (i in 0 until rawRouteRules.length()) {
            val r = rawRouteRules.optJSONObject(i) ?: continue
            val cleaned = sanitizeRouteRule(r, validOutboundTags, dnsOutboundTags, blockOutboundTags)
            if (cleaned != null) {
                cleanRouteRules.put(cleaned)
            }
        }
        route.put("rules", cleanRouteRules)
        root.put("route", route)

        return root.toString(2)
    }

    private fun stripLegacyInboundFields(obj: JSONObject) {
        obj.remove("sniff")
        obj.remove("sniff_override_destination")
        obj.remove("sniff_timeout")
        obj.remove("domain_strategy")
        obj.remove("udp_disable_domain_unmapping")
        obj.remove("inet4_address")
        obj.remove("inet6_address")
        obj.remove("inet4_route_address")
        obj.remove("inet6_route_address")
        obj.remove("inet4_route_exclude_address")
        obj.remove("inet6_route_exclude_address")
        obj.remove("gso")
        obj.remove("endpoint_independent_nat")
    }

    private fun sanitizeDnsRule(
        rule: JSONObject,
        validDnsTags: Set<String>,
        blockDnsServerTags: Set<String>,
        blockOutboundTags: Set<String>,
    ): JSONObject? {
        val copy = JSONObject(rule.toString())
        copy.remove("geosite")
        copy.remove("geoip")
        copy.remove("source_geoip")
        copy.remove("outbound")
        copy.remove("rule_set_ip_cidr_accept_empty")
        copy.remove("rule_set_ipcidr_match_source")
        copy.remove("strategy")

        val server = copy.optString("server")
        val action = copy.optString("action")
        if (server in blockDnsServerTags || server in blockOutboundTags) {
            copy.remove("server")
            copy.put("action", "reject")
        } else if (action.isBlank() || action == "route") {
            if (server.isBlank() || server !in validDnsTags) {
                return null
            }
        }

        val conditionKeys = copy.keys().asSequence().toSet() - setOf(
            "action", "server", "disable_cache", "rewrite_ttl", "client_subnet", "type", "invert", "method", "no_drop",
        )
        if (conditionKeys.isEmpty()) return null
        return copy
    }

    private fun sanitizeRouteRule(
        rule: JSONObject,
        validOutboundTags: Set<String>,
        dnsOutboundTags: Set<String>,
        blockOutboundTags: Set<String>,
    ): JSONObject? {
        val copy = JSONObject(rule.toString())
        copy.remove("geosite")
        copy.remove("geoip")
        copy.remove("source_geoip")
        copy.remove("rule_set_ipcidr_match_source")

        val action = copy.optString("action")
        val outbound = copy.optString("outbound")

        if (action == "sniff" || action == "hijack-dns") {
            // Already prepended at the start of route.rules
            val conditionKeys = copy.keys().asSequence().toSet() - setOf("action", "protocol", "port")
            if (conditionKeys.isEmpty()) return null
        }

        if (outbound in dnsOutboundTags || (copy.optString("protocol") == "dns" && action.isBlank())) {
            copy.remove("outbound")
            copy.put("action", "hijack-dns")
            val conditionKeys = copy.keys().asSequence().toSet() - setOf("action", "protocol", "port")
            if (conditionKeys.isEmpty()) return null
        } else if (outbound in blockOutboundTags) {
            copy.remove("outbound")
            copy.put("action", "reject")
        } else if (action.isBlank() || action == "route") {
            if (outbound.isBlank() || outbound !in validOutboundTags) {
                return null
            }
        }

        val conditionKeys = copy.keys().asSequence().toSet() - setOf(
            "action", "outbound", "type", "invert", "method", "no_drop", "sniffer", "timeout", "strategy", "server",
        )
        if (conditionKeys.isEmpty()) return null
        return copy
    }

    fun buildOutbound(profile: Profile, tag: String = profile.id, domainResolver: String? = null): JSONObject {
        val o = JSONObject()
        o.put("tag", tag)
        if (!domainResolver.isNullOrBlank() && !isIpLiteral(profile.address)) {
            o.put("domain_resolver", domainResolver)
        }
        when (profile.protocol) {
            Protocol.VLESS -> {
                o.put("type", "vless")
                o.put("server", profile.address)
                o.put("server_port", profile.port)
                o.put("uuid", profile.uuid)
                if (profile.flow.isNotBlank()) o.put("flow", profile.flow)
                o.put("packet_encoding", "xudp")
                attachTls(o, profile)
                attachTransport(o, profile)
            }

            Protocol.VMESS -> {
                o.put("type", "vmess")
                o.put("server", profile.address)
                o.put("server_port", profile.port)
                o.put("uuid", profile.uuid)
                o.put("security", profile.method.ifBlank { "auto" })
                o.put("alter_id", profile.alterId)
                attachTls(o, profile)
                attachTransport(o, profile)
            }

            Protocol.TROJAN -> {
                o.put("type", "trojan")
                o.put("server", profile.address)
                o.put("server_port", profile.port)
                o.put("password", profile.password)
                attachTls(o, profile, forceTls = true)
                attachTransport(o, profile)
            }

            Protocol.SHADOWSOCKS -> {
                o.put("type", "shadowsocks")
                o.put("server", profile.address)
                o.put("server_port", profile.port)
                o.put("method", profile.method.ifBlank { "2022-blake3-aes-128-gcm" })
                o.put("password", profile.password)
            }

            Protocol.HYSTERIA2 -> {
                o.put("type", "hysteria2")
                o.put("server", profile.address)
                o.put("server_port", profile.port)
                o.put("password", profile.password)
                if (profile.upMbps > 0) o.put("up_mbps", profile.upMbps)
                if (profile.downMbps > 0) o.put("down_mbps", profile.downMbps)
                if (profile.obfsPassword.isNotBlank()) {
                    o.put(
                        "obfs",
                        JSONObject().apply {
                            put("type", "salamander")
                            put("password", profile.obfsPassword)
                        },
                    )
                }
                attachTls(o, profile, forceTls = true)
            }

            Protocol.TUIC -> {
                o.put("type", "tuic")
                o.put("server", profile.address)
                o.put("server_port", profile.port)
                o.put("uuid", profile.uuid)
                o.put("password", profile.password)
                o.put("congestion_control", profile.congestionControl.ifBlank { "bbr" })
                o.put("udp_relay_mode", profile.udpRelayMode.ifBlank { "native" })
                attachTls(o, profile, forceTls = true)
            }

            Protocol.WIREGUARD -> {
                o.put("type", "wireguard")
                o.put("server", profile.address)
                o.put("server_port", profile.port)
                o.put("local_address", JSONArray(profile.wgLocalAddress.split(",").map { it.trim() }.filter { it.isNotEmpty() }))
                o.put("private_key", profile.wgPrivateKey)
                o.put("peer_public_key", profile.wgPeerPublicKey)
                if (profile.wgPreSharedKey.isNotBlank()) o.put("pre_shared_key", profile.wgPreSharedKey)
                if (profile.wgReserved.isNotBlank()) {
                    val bytes = profile.wgReserved.split(",").mapNotNull { it.trim().toIntOrNull() }
                    if (bytes.size == 3) o.put("reserved", JSONArray(bytes))
                }
                o.put("mtu", profile.wgMtu)
            }

            Protocol.SOCKS -> {
                o.put("type", "socks")
                o.put("server", profile.address)
                o.put("server_port", profile.port)
                o.put("version", "5")
                if (profile.username.isNotBlank()) o.put("username", profile.username)
                if (profile.password.isNotBlank()) o.put("password", profile.password)
            }

            Protocol.HTTP -> {
                o.put("type", "http")
                o.put("server", profile.address)
                o.put("server_port", profile.port)
                if (profile.username.isNotBlank()) o.put("username", profile.username)
                if (profile.password.isNotBlank()) o.put("password", profile.password)
                attachTls(o, profile)
            }

            Protocol.CUSTOM -> {
                val parsed = JSONObject(profile.customConfig)
                return parsed.put("tag", tag)
            }
        }
        return o
    }

    private fun attachTls(target: JSONObject, p: Profile, forceTls: Boolean = false) {
        val wantTls = forceTls || p.tls == "tls" || p.tls == "reality"
        if (!wantTls) return
        val tls = JSONObject().apply {
            put("enabled", true)
            val sni = p.sni.ifBlank { p.address }
            if (sni.isNotBlank()) put("server_name", sni)
            if (p.allowInsecure) put("insecure", true)
            if (p.alpn.isNotBlank()) {
                put("alpn", JSONArray(p.alpn.split(",").map { it.trim() }.filter { it.isNotEmpty() }))
            }
            if (p.fingerprint.isNotBlank()) {
                put(
                    "utls",
                    JSONObject().apply {
                        put("enabled", true)
                        put("fingerprint", p.fingerprint)
                    },
                )
            }
            if (p.tls == "reality" && p.realityPublicKey.isNotBlank()) {
                put(
                    "reality",
                    JSONObject().apply {
                        put("enabled", true)
                        put("public_key", p.realityPublicKey)
                        if (p.realityShortId.isNotBlank()) put("short_id", p.realityShortId)
                    },
                )
            }
        }
        target.put("tls", tls)
    }

    private fun attachTransport(target: JSONObject, p: Profile) {
        when (p.transport) {
            "ws" -> target.put(
                "transport",
                JSONObject().apply {
                    put("type", "ws")
                    if (p.wsPath.isNotBlank()) put("path", p.wsPath)
                    if (p.wsHost.isNotBlank()) {
                        put("headers", JSONObject().put("Host", p.wsHost))
                    }
                },
            )

            "grpc" -> target.put(
                "transport",
                JSONObject().apply {
                    put("type", "grpc")
                    if (p.grpcServiceName.isNotBlank()) put("service_name", p.grpcServiceName)
                },
            )

            "http" -> target.put(
                "transport",
                JSONObject().apply {
                    put("type", "http")
                    if (p.wsPath.isNotBlank()) put("path", p.wsPath)
                    if (p.wsHost.isNotBlank()) {
                        put("host", JSONArray(p.wsHost.split(",").map { it.trim() }))
                    }
                },
            )

            "httpupgrade" -> target.put(
                "transport",
                JSONObject().apply {
                    put("type", "httpupgrade")
                    if (p.wsPath.isNotBlank()) put("path", p.wsPath)
                    if (p.wsHost.isNotBlank()) put("host", p.wsHost)
                },
            )
        }
    }

    /** Basic sanity check so broken profiles fail fast with a clear message. */
    fun validate(config: String): String? = try {
        val root = JSONObject(config)
        val outbounds = root.optJSONArray("outbounds")
        if (outbounds == null || outbounds.length() == 0) {
            "no outbounds defined"
        } else {
            val first = outbounds.getJSONObject(0)
            val type = first.optString("type")
            if (type.isBlank()) {
                "outbound type is missing"
            } else if (type != "direct" && type != "selector" && type != "urltest") {
                val server = first.optString("server")
                val port = first.optInt("server_port", 0)
                if (server.isNotBlank() && port !in 1..65535) {
                    "server port must be between 1 and 65535"
                } else null
            } else null
        }
    } catch (e: Exception) {
        e.message ?: "invalid JSON"
    }
}
