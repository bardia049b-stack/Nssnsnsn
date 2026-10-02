package app.nebulabox.data

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val language: String = "system",       // system | en | fa
    val theme: String = "system",          // system | light | dark
    val dynamicColor: Boolean = false,

    // routing
    val routeMode: String = "global",      // global | rule | direct
    val bypassLan: Boolean = true,
    val bypassChina: Boolean = false,
    val blockAds: Boolean = true,
    val ipv6: Boolean = false,

    // dns
    val remoteDns: String = "https://1.1.1.1/dns-query",
    val directDns: String = "https://8.8.8.8/dns-query",
    val dnsStrategy: String = "prefer_ipv4",

    // tunnel tuning
    val mtu: Int = 9000,
    val tcpFastOpen: Boolean = false,
    val sniffing: Boolean = true,
    val tcpMux: Boolean = true,
    val tcpMuxPadding: Boolean = false,

    // per-app proxy
    val perAppEnabled: Boolean = false,
    val perAppMode: String = "exclude",    // include | exclude
    val perAppPackages: Set<String> = emptySet(),

    // behaviour
    val autoConnect: Boolean = false,
    val alwaysOn: Boolean = false,
    val meteredNetwork: Boolean = true,
    val showSpeedInNotification: Boolean = true,
    val logLevel: String = "warning",      // trace | debug | info | warning | error | fatal | panic
    val selectedProfileId: String? = null,
)
