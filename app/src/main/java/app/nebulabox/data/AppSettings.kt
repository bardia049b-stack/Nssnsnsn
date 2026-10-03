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
    val blockAds: Boolean = false,
    val ipv6: Boolean = false,

    // dns
    val remoteDns: String = "1.1.1.1",
    val directDns: String = "8.8.8.8",
    val dnsStrategy: String = "prefer_ipv4",

    // tunnel tuning
    val mtu: Int = 9000,
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
    val meteredNetwork: Boolean = true,
    val showSpeedInNotification: Boolean = true,
    val logLevel: String = "warning",      // trace | debug | info | warning | error | fatal | panic
    val selectedProfileId: String? = null,
)
