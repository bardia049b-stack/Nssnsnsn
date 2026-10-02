package app.nebulabox.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The protocol families the tunnel engine can speak.
 * Every one of these maps onto a real sing-box outbound type.
 */
enum class Protocol(val wire: String) {
    @SerialName("vless")
    VLESS("vless"),

    @SerialName("vmess")
    VMESS("vmess"),

    @SerialName("trojan")
    TROJAN("trojan"),

    @SerialName("shadowsocks")
    SHADOWSOCKS("shadowsocks"),

    @SerialName("socks")
    SOCKS("socks"),

    @SerialName("http")
    HTTP("http"),

    @SerialName("hysteria2")
    HYSTERIA2("hysteria2"),

    @SerialName("tuic")
    TUIC("tuic"),

    @SerialName("wireguard")
    WIREGUARD("wireguard"),

    @SerialName("ssh")
    SSH("ssh"),

    @SerialName("naive")
    NAIVE("naive"),

    @SerialName("direct")
    DIRECT("direct");

    companion object {
        fun fromWire(value: String): Protocol? = entries.firstOrNull {
            it.wire.equals(value, ignoreCase = true)
        }
    }
}

/** TLS transport wrapped around a stream protocol. */
@Serializable
data class Transport(
    val type: String = "tcp",          // tcp | ws | http | httpupgrade | quic | grpc
    val host: String = "",             // Host header / SNI override
    val path: String = "",             // ws / http path
    val headers: Map<String, String> = emptyMap(),
    val serviceName: String = "",      // grpc service name
    val maxEarlyData: Int = 0,
    val earlyDataHeader: String = "",
)

@Serializable
data class TlsSettings(
    val enabled: Boolean = false,
    val serverName: String = "",
    val insecure: Boolean = false,
    val alpn: List<String> = emptyList(),
    val minVersion: String = "",
    val maxVersion: String = "",
    val reality: Boolean = false,
    val realityPublicKey: String = "",
    val realityShortId: String = "",
    val utls: Boolean = false,
    val utlsFingerprint: String = "chrome",
)

/** A single server the user can connect to. */
@Serializable
data class Profile(
    val id: String,
    var name: String,
    val protocol: Protocol,
    val server: String,
    val serverPort: Int,

    // authentication, meaning depends on protocol
    val username: String = "",         // socks / http user, ssh user, tuic uuid
    val password: String = "",         // ss / trojan / socks / ssh password, tuic token
    val uuid: String = "",             // vless / vmess id

    // vless / hysteria2 flow control
    val flow: String = "",             // xtls-rprx-vision
    val upMbps: Int = 0,
    val downMbps: Int = 0,

    // vmess specifics
    val alterId: Int = 0,
    val security: String = "",         // auto | aes-128-gcm | chacha20-poly1305 | none

    // shadowsocks specifics
    val method: String = "",
    val plugin: String = "",
    val pluginOptions: String = "",

    // wireguard specifics
    val privateKey: String = "",
    val peerPublicKey: String = "",
    val preSharedKey: String = "",
    val localAddresses: List<String> = emptyList(),
    val reserved: List<Int> = emptyList(),
    val mtu: Int = 1420,

    // ssh specifics
    val clientVersion: String = "SSH-2.0-OpenSSH_9.8",
    val hostKeyAlgorithms: List<String> = emptyList(),
    val knownHosts: String = "",

    val transport: Transport = Transport(),
    val tls: TlsSettings = TlsSettings(),

    val subscriptionUrl: String = "",
    val remark: String = "",
    var order: Int = 0,
    var lastTestedAt: Long = 0,
    var lastDelayMs: Int = -1,
) {
    val displayName: String
        get() = name.ifBlank { "$server:$serverPort" }
}
