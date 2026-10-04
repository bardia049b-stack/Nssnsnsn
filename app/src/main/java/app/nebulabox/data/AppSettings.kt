package app.nebulabox.data

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val settingsVersion: Int = 4,
    val language: String = "system",
    val theme: String = "system",
    val dynamicColor: Boolean = false,

    val useHevTun: Boolean = true,
    val socksPort: Int = 10808,
    val allowLan: Boolean = false,
    val mtu: Int = 1500,
    val ipv6: Boolean = false,
    val preferIpv6: Boolean = false,

    val routeMode: String = "global",
    val domainStrategy: String = "AsIs",
    val outboundDomainResolve: String = "happy_eyeballs",
    val bypassLan: Boolean = true,
    val bypassChina: Boolean = false,
    val blockAds: Boolean = false,

    val localDnsEnabled: Boolean = false,
    val remoteDns: String = "https://cloudflare-dns.com/dns-query",
    val directDns: String = "223.5.5.5",
    val vpnDns: String = "1.1.1.1",
    val dnsStrategy: String = "ipv4_only",
    val fakeDns: Boolean = false,

    val sniffing: Boolean = true,
    val routeOnly: Boolean = false,

    val fragmentEnabled: Boolean = false,
    val fragmentPackets: String = "tlshello",
    val fragmentLength: String = "50-100",
    val fragmentInterval: String = "10-20",
    val fragmentMaxSplit: String = "10",

    val tcpMux: Boolean = false,
    val muxConcurrency: Int = 8,
    val muxXudpConcurrency: Int = 8,
    val muxXudpQuic: String = "reject",
    val tcpMuxPadding: Boolean = false,
    val tcpFastOpen: Boolean = false,

    val delayTestUrl: String = "https://cp.cloudflare.com/generate_204",

    val perAppEnabled: Boolean = false,
    val perAppMode: String = "exclude",
    val perAppPackages: Set<String> = emptySet(),

    val autoConnect: Boolean = false,
    val autoReconnect: Boolean = true,
    val autoPingMinutes: Int = 15,
    val alwaysOn: Boolean = false,
    val meteredNetwork: Boolean = false,
    val showSpeedInNotification: Boolean = true,
    val autoUpdateSubscriptions: Boolean = false,
    val subscriptionUpdateIntervalHours: Int = 12,
    val notifySubscriptionUpdates: Boolean = true,
    val autoCheckAppUpdates: Boolean = false,
    val notifyAppUpdates: Boolean = true,
    val clipboardAutoImport: Boolean = false,
    val reconnectOnNetworkChange: Boolean = false,
    val notifySlowServers: Boolean = false,
    val slowServerThresholdMs: Int = 500,
    val logLevel: String = "warning",
    val selectedProfileId: String? = null,
    val selectedSubscriptionId: String = "",
) {
    fun normalized(): AppSettings {
        val validMtu = if (mtu in 1280..1500) mtu else 1500
        val validPort = if (socksPort in 1024..65535) socksPort else 10808
        val validLogLevel = when (logLevel.lowercase()) {
            "trace", "debug" -> "debug"
            "info" -> "info"
            "warn", "warning" -> "warning"
            "error", "fatal", "panic" -> "error"
            "none" -> "none"
            else -> "warning"
        }
        val validRemoteDns = if (remoteDns == "tls://8.8.8.8" || remoteDns == "1.1.1.1" || remoteDns.isBlank()) {
            "https://cloudflare-dns.com/dns-query"
        } else {
            remoteDns
        }
        val validDirectDns = if (directDns == "1.1.1.1" || directDns.isBlank()) "223.5.5.5" else directDns
        val validDelayUrl = if (delayTestUrl.isBlank() || delayTestUrl.contains("gstatic.com")) {
            "https://cp.cloudflare.com/generate_204"
        } else {
            delayTestUrl
        }
        val validPingMinutes = if (autoPingMinutes in listOf(0, 5, 15, 30)) autoPingMinutes else 15
        if (settingsVersion >= 5 &&
            mtu == validMtu &&
            socksPort == validPort &&
            logLevel == validLogLevel &&
            remoteDns == validRemoteDns &&
            directDns == validDirectDns &&
            delayTestUrl == validDelayUrl &&
            autoPingMinutes == validPingMinutes
        ) {
            return this
        }
        return copy(
            settingsVersion = 5,
            mtu = validMtu,
            socksPort = validPort,
            logLevel = validLogLevel,
            remoteDns = validRemoteDns,
            directDns = validDirectDns,
            delayTestUrl = validDelayUrl,
            autoPingMinutes = validPingMinutes,
            localDnsEnabled = false,
            tcpFastOpen = false,
            tcpMux = false,
            meteredNetwork = false,
        )
    }
}
