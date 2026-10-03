package app.nebulabox.data

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val settingsVersion: Int = 2,
    val language: String = "system",       // system | en | fa
    val theme: String = "system",          // system | light | dark
    val dynamicColor: Boolean = false,

    // routing
    val routeMode: String = "global",      // global | rule | direct
    val bypassLan: Boolean = true,
    val bypassChina: Boolean = false,
    val blockAds: Boolean = false,
    val ipv6: Boolean = false,

    // dns (aligned with v2rayNG & NekoBox defaults: DoH over proxy + UDP direct)
    val remoteDns: String = "https://1.1.1.1/dns-query",
    val directDns: String = "8.8.8.8",
    val dnsStrategy: String = "ipv4_only",

    // tunnel tuning (aligned with v2rayNG VPN_MTU = 1500, mux & TFO disabled)
    val mtu: Int = 1500,
    val tcpFastOpen: Boolean = false,
    val sniffing: Boolean = true,
    val tcpMux: Boolean = false,
    val tcpMuxPadding: Boolean = false,

    // per-app proxy
    val perAppEnabled: Boolean = false,
    val perAppMode: String = "exclude",    // include | exclude
    val perAppPackages: Set<String> = emptySet(),

    // behaviour
    val autoConnect: Boolean = false,
    val alwaysOn: Boolean = false,
    val meteredNetwork: Boolean = false,
    val showSpeedInNotification: Boolean = true,
    val logLevel: String = "info",         // trace | debug | info | warning | error | fatal | panic
    val selectedProfileId: String? = null,
) {
    fun normalized(): AppSettings {
        if (settingsVersion >= 2 && mtu in 1280..1500) return this
        return copy(
            settingsVersion = 2,
            mtu = if (mtu in 1280..1500) mtu else 1500,
            tcpFastOpen = false,
            tcpMux = false,
            meteredNetwork = false,
            remoteDns = if (remoteDns == "tls://8.8.8.8" || remoteDns == "1.1.1.1" || remoteDns.isBlank()) {
                "https://1.1.1.1/dns-query"
            } else {
                remoteDns
            },
            directDns = if (directDns == "1.1.1.1" || directDns.isBlank()) "8.8.8.8" else directDns,
            dnsStrategy = if (!ipv6) "ipv4_only" else dnsStrategy,
        )
    }
}
