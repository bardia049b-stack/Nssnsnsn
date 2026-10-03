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

internal object DnsConfigBuilder {
    fun configureDns(
        settings: AppSettings,
        coreConfig: CoreConfig,
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

        val remoteDns = settings.remoteDns.trim().ifBlank { TunnelConstants. DELAY_TEST_URL }.let {
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
                CoreConfig.DnsBean.ServersBean(
                    address = directDns,
                    domains = domainDirectList,
                    skipFallback = true,
                    tag = TunnelConstants.TAG_DOMESTIC_DNS,
                ),
            )
        }

        coreConfig.dns = CoreConfig.DnsBean(
            servers = servers,
            hosts = hosts,
            tag = TunnelConstants.TAG_DNS,
        )

        if (domainDirectList.isNotEmpty()) {
            coreConfig.routing.rules.add(
                0,
                CoreConfig.RoutingBean.RulesBean(
                    inboundTag = listOf(TunnelConstants.TAG_DOMESTIC_DNS),
                    outboundTag = TunnelConstants.TAG_DIRECT,
                ),
            )
        }
        coreConfig.routing.rules.add(
            0,
            CoreConfig.RoutingBean.RulesBean(
                inboundTag = listOf(TunnelConstants.TAG_DNS),
                outboundTag = TunnelConstants.TAG_PROXY,
            ),
        )
    }

    fun configureLocalDns(
        settings: AppSettings,
        coreConfig: CoreConfig,
    ) {
        if (!settings.localDnsEnabled) {
            return
        }
        if (coreConfig.outbounds.none { it.protocol == "dns" && it.tag == "dns-out" }) {
            coreConfig.outbounds.add(
                CoreConfig.OutboundBean(
                    protocol = "dns",
                    tag = "dns-out",
                    settings = null,
                    streamSettings = null,
                    mux = null,
                ),
            )
        }
        coreConfig.routing.rules.add(
            0,
            CoreConfig.RoutingBean.RulesBean(
                inboundTag = listOf("socks"),
                outboundTag = "dns-out",
                port = "53",
            ),
        )
    }

    fun configureRootModeDns(coreConfig: CoreConfig) {
        if (coreConfig.outbounds.none { it.protocol == "dns" && it.tag == "dns-out" }) {
            coreConfig.outbounds.add(
                CoreConfig.OutboundBean(
                    protocol = "dns",
                    tag = "dns-out",
                    settings = null,
                    streamSettings = null,
                    mux = null,
                ),
            )
        }
        coreConfig.routing.rules.add(
            0,
            CoreConfig.RoutingBean.RulesBean(
                inboundTag = listOf("tun"),
                outboundTag = "dns-out",
                port = "53",
            ),
        )
    }

}
