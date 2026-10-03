package app.nebulabox.core.enums

enum class ProtocolType(val value: Int, val protocolScheme: String) {
    VMESS(1, "vmess://"),
    CUSTOM(2, ""),
    SHADOWSOCKS(3, "ss://"),
    SOCKS(4, "socks://"),
    VLESS(5, "vless://"),
    TROJAN(6, "trojan://"),
    WIREGUARD(7, "wireguard://"),
    HYSTERIA2(9, "hysteria2://"),
    HTTP(10, "http://"),
    POLICYGROUP(101, ""),
    PROXYCHAIN(102, "");

    companion object {
        fun fromInt(value: Int) = entries.firstOrNull { it.value == value }
    }
}
