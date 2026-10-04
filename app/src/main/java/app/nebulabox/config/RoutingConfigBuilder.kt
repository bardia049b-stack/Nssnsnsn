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

internal object RoutingConfigBuilder {
    fun configureRouting(
        profile: Profile,
        settings: AppSettings,
        coreConfig: CoreConfig,
        includeGeoRules: Boolean,
    ) {
        coreConfig.routing.domainStrategy = settings.domainStrategy.ifBlank { "AsIs" }
        val rules = coreConfig.routing.rules

        val isUdpNativeProtocol = profile.protocol in setOf(Protocol.HYSTERIA2, Protocol.TUIC, Protocol.WIREGUARD)
        if (!isUdpNativeProtocol) {
            rules.add(
                CoreConfig.RoutingBean.RulesBean(
                    port = "443",
                    network = "udp",
                    outboundTag = TunnelConstants.TAG_BLOCKED,
                ),
            )
        }

        if (includeGeoRules && settings.blockAds) {
            rules.add(
                CoreConfig.RoutingBean.RulesBean(
                    domain = listOf("geosite:category-ads", "geosite:category-ads-ir"),
                    outboundTag = TunnelConstants.TAG_BLOCKED,
                ),
            )
        }

        if (settings.bypassLan) {
            if (includeGeoRules) {
                rules.add(
                    CoreConfig.RoutingBean.RulesBean(
                        ip = listOf("ext:geoip-only-cn-private.dat:private"),
                        outboundTag = TunnelConstants.TAG_DIRECT,
                    ),
                )
                rules.add(
                    CoreConfig.RoutingBean.RulesBean(
                        domain = listOf("geosite:private"),
                        outboundTag = TunnelConstants.TAG_DIRECT,
                    ),
                )
            } else {
                rules.add(
                    CoreConfig.RoutingBean.RulesBean(
                        ip = listOf(
                            "10.0.0.0/8",
                            "127.0.0.0/8",
                            "172.16.0.0/12",
                            "192.168.0.0/16",
                            "169.254.0.0/16",
                        ),
                        outboundTag = TunnelConstants.TAG_DIRECT,
                    ),
                )
            }
        }

        when (settings.routeMode) {
            "white_iran" -> {
                if (includeGeoRules) {
                    rules.add(
                        CoreConfig.RoutingBean.RulesBean(
                            domain = listOf("domain:ir", "geosite:category-ir"),
                            outboundTag = TunnelConstants.TAG_DIRECT,
                        ),
                    )
                    rules.add(
                        CoreConfig.RoutingBean.RulesBean(
                            ip = listOf("geoip:ir"),
                            outboundTag = TunnelConstants.TAG_DIRECT,
                        ),
                    )
                } else {
                    rules.add(
                        CoreConfig.RoutingBean.RulesBean(
                            domain = listOf("domain:ir"),
                            outboundTag = TunnelConstants.TAG_DIRECT,
                        ),
                    )
                }
            }

            "rule" -> {
                if (includeGeoRules && settings.bypassChina) {
                    rules.add(
                        CoreConfig.RoutingBean.RulesBean(
                            ip = listOf("ext:geoip-only-cn-private.dat:cn"),
                            outboundTag = TunnelConstants.TAG_DIRECT,
                        ),
                    )
                    rules.add(
                        CoreConfig.RoutingBean.RulesBean(
                            domain = listOf("geosite:cn"),
                            outboundTag = TunnelConstants.TAG_DIRECT,
                        ),
                    )
                }
            }

            "direct" -> {
                rules.add(
                    CoreConfig.RoutingBean.RulesBean(
                        port = "0-65535",
                        outboundTag = TunnelConstants.TAG_DIRECT,
                    ),
                )
            }

            else -> {

                rules.add(
                    CoreConfig.RoutingBean.RulesBean(
                        port = "0-65535",
                        outboundTag = TunnelConstants.TAG_PROXY,
                    ),
                )
            }
        }
    }

}
