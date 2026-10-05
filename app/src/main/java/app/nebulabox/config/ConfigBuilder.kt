package app.nebulabox.config

import app.nebulabox.data.AppSettings
import app.nebulabox.data.Profile
import app.nebulabox.data.Protocol
import app.nebulabox.util.AppLogger
import app.nebulabox.util.ShareLinkParser
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import app.nebulabox.core.TunnelConstants
import app.nebulabox.core.model.CoreConfig
import app.nebulabox.core.model.CoreConfig.OutboundBean.OutSettingsBean
import app.nebulabox.core.model.CoreConfig.OutboundBean.StreamSettingsBean
import app.nebulabox.core.enums.TransportType
import app.nebulabox.core.serializer.JsonSerializer
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

object ConfigBuilder {

    const val LOCAL_USER = "javidtun"


    fun build(profile: Profile, settings: AppSettings): String {
        val s = settings.normalized()
        if (profile.protocol == Protocol.CUSTOM && profile.customConfig.isNotBlank()) {
            return buildCustomConfig(profile, s)
        }
        return buildNormalConfig(profile, s, includeGeoRules = true)
    }

    fun buildWithoutGeoRules(profile: Profile, settings: AppSettings): String {
        val s = settings.normalized()
        if (profile.protocol == Protocol.CUSTOM && profile.customConfig.isNotBlank()) {
            return buildCustomConfig(profile, s, includeGeoRules = false)
        }
        return buildNormalConfig(profile, s, includeGeoRules = false)
    }

    fun buildForSpeedtest(profile: Profile, settings: AppSettings): String {
        val s = settings.normalized()
        if (profile.protocol == Protocol.CUSTOM && profile.customConfig.isNotBlank()) {
            return buildCustomConfig(profile, s)
        }
        return buildSpeedtestConfig(profile, s)
    }

    private fun initCoreConfig(settings: AppSettings): CoreConfig {
        val inbounds = arrayListOf(
            CoreConfig.InboundBean(
                tag = "socks",
                port = settings.socksPort,
                protocol = "socks",
                listen = if (settings.allowLan) "0.0.0.0" else TunnelConstants.LOOPBACK,
                settings = CoreConfig.InboundBean.InSettingsBean(
                    auth = if (settings.socksAuth) "password" else "noauth",
                    udp = true,
                    userLevel = 8,
                    accounts = if (settings.socksAuth) {
                        listOf(
                            CoreConfig.InboundBean.InSettingsBean.SocksAccountBean(
                                user = LOCAL_USER,
                                pass = settings.socksPassword,
                            ),
                        )
                    } else {
                        null
                    },
                ),
                sniffing = CoreConfig.InboundBean.SniffingBean(
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
                CoreConfig.InboundBean(
                    tag = "tun",
                    port = 0,
                    protocol = "tun",
                    settings = CoreConfig.InboundBean.InSettingsBean(
                        name = "xray0",
                        mtu = settings.mtu,
                        userLevel = 8,
                    ),
                    sniffing = CoreConfig.InboundBean.SniffingBean(
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

        val directOutbound = CoreConfig.OutboundBean(
            tag = TunnelConstants.TAG_DIRECT,
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

        val blockOutbound = CoreConfig.OutboundBean(
            tag = TunnelConstants.TAG_BLOCKED,
            protocol = "blackhole",
            settings = OutSettingsBean(),
            streamSettings = null,
            mux = null,
        )

        return CoreConfig(
            remarks = null,
            stats = emptyMap<String, Any>(),
            log = CoreConfig.LogBean(loglevel = settings.logLevel),
            policy = CoreConfig.PolicyBean(
                levels = mapOf(
                    "8" to CoreConfig.PolicyBean.LevelBean(
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
            dns = CoreConfig.DnsBean(
                hosts = LinkedHashMap(),
                servers = ArrayList(),
            ),
            routing = CoreConfig.RoutingBean(
                domainStrategy = settings.domainStrategy.ifBlank { "AsIs" },
                rules = ArrayList(),
            ),
        )
    }

    private fun buildNormalConfig(
        profile: Profile,
        settings: AppSettings,
        includeGeoRules: Boolean,
    ): String {
        val coreConfig = initCoreConfig(settings)
        coreConfig.remarks = profile.displayName
        coreConfig.log.loglevel = settings.logLevel

        if (settings.fakeDns) {
            coreConfig.fakedns = listOf(CoreConfig.FakednsBean())
        }

        val proxyOutbound = OutboundConfigBuilder.buildOutboundWithGlobalSettings(profile, settings)
        coreConfig.outbounds.add(0, proxyOutbound)

        RoutingConfigBuilder.configureRouting(profile, settings, coreConfig, includeGeoRules)
        DnsConfigBuilder.configureDns(settings, coreConfig, includeGeoRules)
        DnsConfigBuilder.configureLocalDns(settings, coreConfig)
        if (!settings.useHevTun) {
            DnsConfigBuilder.configureRootModeDns(coreConfig)
        }

        HostResolver.resolveOutboundDomainsToHosts(settings, coreConfig)

        return finish(coreConfig)
    }

    private fun finish(coreConfig: CoreConfig): String {
        val json = JsonSerializer.parseString(JsonSerializer.toJsonPretty(coreConfig) ?: "{}")
        if (json == null) return "{}"
        GeoCatalog.sanitize(json)
        return JsonSerializer.toJsonPretty(json) ?: "{}"
    }

    private fun buildSpeedtestConfig(
        profile: Profile,
        settings: AppSettings,
    ): String {
        val coreConfig = initCoreConfig(settings)
        coreConfig.remarks = profile.displayName
        coreConfig.log.loglevel = settings.logLevel

        val proxyOutbound = OutboundConfigBuilder.buildOutboundWithGlobalSettings(profile, settings)
        coreConfig.outbounds.add(0, proxyOutbound)

        coreConfig.inbounds.clear()
        coreConfig.routing.domainStrategy = "AsIs"
        coreConfig.routing.rules.clear()
        coreConfig.dns = null
        coreConfig.fakedns = null
        coreConfig.stats = null
        coreConfig.policy = null
        coreConfig.outbounds.forEach { outbound ->
            outbound.mux = null
        }

        return JsonSerializer.toJsonPretty(coreConfig) ?: "{}"
    }

    private fun buildCustomConfig(
        profile: Profile,
        settings: AppSettings,
        includeGeoRules: Boolean = true,
    ): String {
        val raw = profile.customConfig.trim()
        val json = JsonSerializer.parseString(raw) ?: return raw

        if (!json.has("outbounds") && json.has("protocol") && json.has("settings")) {
            val outboundBean = JsonSerializer.fromJsonSafe(raw, CoreConfig.OutboundBean::class.java)
            if (outboundBean != null) {
                outboundBean.tag = TunnelConstants.TAG_PROXY
                val coreConfig = initCoreConfig(settings)
                coreConfig.remarks = profile.displayName
                coreConfig.outbounds.add(0, outboundBean)
                RoutingConfigBuilder.configureRouting(profile, settings, coreConfig, includeGeoRules)
                DnsConfigBuilder.configureDns(settings, coreConfig, includeGeoRules)
                return finish(coreConfig)
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

        if (settings.useHevTun) {
            GeoCatalog.sanitize(json)
            return JsonSerializer.toJsonPretty(json) ?: raw
        }

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

        GeoCatalog.sanitize(json)
        return JsonSerializer.toJsonPretty(json) ?: raw
    }

    fun stripAllGeoRules(config: String): String {
        val json = JsonSerializer.parseString(config) ?: return config
        GeoCatalog.stripAll(json)
        return JsonSerializer.toJsonPretty(json) ?: config
    }

    fun isGeoError(message: String): Boolean {
        val text = message.lowercase()
        return text.contains("geodata") ||
            text.contains("geosite") ||
            text.contains("geoip") ||
            text.contains("failed to check code")
    }

    fun validate(config: String): String? = runCatching {
        val obj = JsonSerializer.parseString(config) ?: return "invalid JSON"
        val outbounds = obj.getAsJsonArray("outbounds")
        if (outbounds == null || outbounds.size() == 0) return "no outbounds block"
        null
    }.getOrElse { it.message ?: "invalid JSON" }
}
