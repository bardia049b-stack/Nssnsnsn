package com.v2ray.ang

object AppConfig {
    const val TAG = "v2rayNG"
    const val DEFAULT_NETWORK = "tcp"
    const val HEADER_TYPE_HTTP = "http"
    const val PROTOCOL_FREEDOM = "freedom"
    const val TLS = "tls"
    const val REALITY = "reality"
    const val DEFAULT_SECURITY = "auto"
    const val DEFAULT_LEVEL = 8
    const val PORT_SOCKS = "10808"

    const val TAG_PROXY = "proxy"
    const val TAG_DIRECT = "direct"
    const val TAG_BLOCKED = "block"
    const val TAG_FRAGMENT = "fragment"
    const val TAG_DNS = "dns-module"
    const val TAG_DOMESTIC_DNS = "domestic-dns"

    const val WIREGUARD_LOCAL_ADDRESS_V4 = "172.16.0.2/32"
    const val WIREGUARD_LOCAL_MTU = "1420"
    const val LOOPBACK = "127.0.0.1"
    const val DELAY_TEST_URL = "https://www.gstatic.com/generate_204"
    const val IP_API_URL = "https://api.ip.sb/geoip"

    const val VMESS = "vmess://"
    const val CUSTOM = ""
    const val SHADOWSOCKS = "ss://"
    const val SOCKS = "socks://"
    const val HTTP = "http://"
    const val VLESS = "vless://"
    const val TROJAN = "trojan://"
    const val WIREGUARD = "wireguard://"
    const val TUIC = "tuic://"
    const val HYSTERIA2 = "hysteria2://"
    const val HYSTERIA = "hysteria://"
}
