package app.nebulabox.data

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val settingsVersion: Int = 3,
    val language: String = "system",       // system | en | fa
    val theme: String = "system",          // system | light | dark
    val dynamicColor: Boolean = false,

    // VPN & TUN engine (aligned with v2rayNG 2.3.10)
    val useHevTun: Boolean = true,         // true = hev-socks5-tunnel (v2rayNG default), false = Xray Native TUN (gVisor)
    val socksPort: Int = 10808,
    val allowLan: Boolean = false,
    val mtu: Int = 1500,
    val ipv6: Boolean = false,
    val preferIpv6: Boolean = false,

    // Routing (aligned with v2rayNG routing presets)
    val routeMode: String = "global",      // global | white_iran | rule | direct
    val domainStrategy: String = "AsIs",   // AsIs | IPIfNonMatch | IPOnDemand
    val outboundDomainResolve: String = "happy_eyeballs", // happy_eyeballs | ipv4_only | prefer_ipv6 | asis
    val bypassLan: Boolean = true,
    val bypassChina: Boolean = false,
    val blockAds: Boolean = false,

    // DNS (aligned with v2rayNG defaults)
    val remoteDns: String = "https://cloudflare-dns.com/dns-query",
    val directDns: String = "8.8.8.8",
    val vpnDns: String = "1.1.1.1",
    val dnsStrategy: String = "ipv4_only",
    val fakeDns: Boolean = false,

    // Sniffing
    val sniffing: Boolean = true,
    val routeOnly: Boolean = false,

    // Xray Anti-DPI TLS Fragment & Noise (finalmask)
    val fragmentEnabled: Boolean = false,
    val fragmentPackets: String = "tlshello", // tlshello | 1-3 | 1-5
    val fragmentLength: String = "50-100",
    val fragmentInterval: String = "10-20",
    val fragmentMaxSplit: String = "10",

    // Xray Mux
    val tcpMux: Boolean = false,
    val muxConcurrency: Int = 8,
    val muxXudpConcurrency: Int = 8,
    val muxXudpQuic: String = "reject",    // reject | allow | skip
    val tcpMuxPadding: Boolean = false,
    val tcpFastOpen: Boolean = false,

    // Connection test
    val delayTestUrl: String = "https://www.gstatic.com/generate_204",

    // Per-app proxy
    val perAppEnabled: Boolean = false,
    val perAppMode: String = "exclude",    // include | exclude
    val perAppPackages: Set<String> = emptySet(),

    // Behaviour
    val autoConnect: Boolean = false,
    val alwaysOn: Boolean = false,
    val meteredNetwork: Boolean = false,
    val showSpeedInNotification: Boolean = true,
    val logLevel: String = "warning",      // debug | info | warning | error | none
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
        val validDirectDns = if (directDns == "1.1.1.1" || directDns.isBlank()) "8.8.8.8" else directDns
        if (settingsVersion >= 3 &&
            mtu == validMtu &&
            socksPort == validPort &&
            logLevel == validLogLevel &&
            remoteDns == validRemoteDns &&
            directDns == validDirectDns
        ) {
            return this
        }
        return copy(
            settingsVersion = 3,
            mtu = validMtu,
            socksPort = validPort,
            logLevel = validLogLevel,
            remoteDns = validRemoteDns,
            directDns = validDirectDns,
            tcpFastOpen = false,
            tcpMux = false,
            meteredNetwork = false,
        )
    }
}
