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
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

internal object HostResolver {
    fun resolveOutboundDomainsToHosts(
        settings: AppSettings,
        coreConfig: CoreConfig,
    ) {
        val proxyOutboundList = coreConfig.getAllProxyOutbound()
        val dns = coreConfig.dns ?: return
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

    fun isPureIpAddress(value: String): Boolean {
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

}
