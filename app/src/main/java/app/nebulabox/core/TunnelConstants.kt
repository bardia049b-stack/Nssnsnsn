package app.nebulabox.core

object TunnelConstants {
    const val TAG_PROXY = "proxy"
    const val TAG_DIRECT = "direct"
    const val TAG_BLOCKED = "block"
    const val TAG_FRAGMENT = "fragment"
    const val TAG_DNS = "dns-module"
    const val TAG_DOMESTIC_DNS = "domestic-dns"

    const val PROTOCOL_FREEDOM = "freedom"
    const val LOOPBACK = "127.0.0.1"
    const val DEFAULT_PORT = 443
    const val DEFAULT_SECURITY = "auto"
    const val DEFAULT_LEVEL = 8
    const val DEFAULT_NETWORK = "tcp"
    const val TLS = "tls"
    const val REALITY = "reality"
    const val HEADER_TYPE_HTTP = "http"

    const val DNS_PROXY = "https://cloudflare-dns.com/dns-query"
    const val DNS_DIRECT = "223.5.5.5"
    const val DNS_VPN = "1.1.1.1"

    const val GEOSITE_PRIVATE = "geosite:private"
    const val GEOSITE_CN = "geosite:cn"
    const val GEOIP_PRIVATE = "geoip:private"
    const val GEOIP_CN = "geoip:cn"

    const val PORT_SOCKS = "10808"
    const val VPN_MTU = 1500

    const val DELAY_TEST_URL = "https://www.gstatic.com/generate_204"
    const val IP_API_URL = "https://api.ip.sb/geoip"

    const val WIREGUARD_LOCAL_ADDRESS_V4 = "172.16.0.2/32"
    const val WIREGUARD_LOCAL_ADDRESS_V6 = "2606:4700:110:8f81:d551:a0:532e:a2b3/128"
    const val WIREGUARD_LOCAL_MTU = "1420"

    const val HYSTERIA2 = "hysteria2://"
    const val HY2 = "hy2://"
}
