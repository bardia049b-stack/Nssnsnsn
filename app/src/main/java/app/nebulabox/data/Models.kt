package app.nebulabox.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The protocol families the Xray-core tunnel engine can speak.
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

    @SerialName("custom")
    CUSTOM("custom"),

    @SerialName("direct")
    DIRECT("direct");

    companion object {
        fun fromWire(value: String): Protocol? = entries.firstOrNull {
            it.wire.equals(value, ignoreCase = true) ||
                (value.equals("hysteria", ignoreCase = true) && it == HYSTERIA2)
        }
    }
}

/** Stream transport wrapped around a proxy protocol. */
@Serializable
data class Transport(
    val type: String = "tcp",          // tcp | ws | httpupgrade | xhttp | h2 | http | kcp | grpc | quic
    val host: String = "",             // Host header / SNI override
    val path: String = "",             // ws / httpupgrade / xhttp path (preserves ?ed=2048 for Xray)
    val headers: Map<String, String> = emptyMap(),
    val headerType: String = "none",   // none | http | srtp | utp | wechat-video | dtls | wireguard
    val serviceName: String = "",      // grpc service name
    val authority: String = "",        // grpc authority
    val grpcMode: String = "gun",      // gun | multi
    val xhttpMode: String = "auto",    // auto | packet-up | stream-up | stream-one
    val xhttpExtra: String = "",
    val seed: String = "",             // mKCP seed
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
    val realitySpiderX: String = "",
    val utls: Boolean = false,
    val utlsFingerprint: String = "",
    val echConfigList: String = "",
    val echForceQuery: String = "",
    val pinnedCA256: String = "",
    val verifyPeerCertByName: String = "",
    val mldsa65Verify: String = "",
)

/** Subscription group item (aligned with v2rayNG SubscriptionItem). */
@Serializable
data class SubscriptionItem(
    val id: String,
    val remarks: String,
    val url: String,
    val enabled: Boolean = true,
    val updatedAt: Long = 0L,
    val userAgent: String = "",
)

/** A single server or custom JSON configuration the user can connect to. */
@Serializable
data class Profile(
    val id: String,
    var name: String,
    val protocol: Protocol,
    val server: String = "",
    val serverPort: Int = 0,

    // authentication, meaning depends on protocol
    val username: String = "",         // socks / http user, ssh user, tuic uuid
    val password: String = "",         // ss / trojan / socks / ssh password, tuic token, hysteria2 auth
    val uuid: String = "",             // vless / vmess id
    val encryption: String = "none",   // vless encryption (default "none")

    // vless / hysteria2 flow control & port hopping
    val flow: String = "",             // xtls-rprx-vision | xtls-rprx-vision-udp443
    val upMbps: Int = 0,
    val downMbps: Int = 0,
    val obfsPassword: String = "",     // hysteria2 salamander obfs password
    val portHopping: String = "",      // hysteria2 mport e.g. "20000-50000"
    val portHoppingInterval: String = "30",
    val finalMask: String = "",        // Xray finalmask JSON from &fm=

    // vmess specifics
    val alterId: Int = 0,
    val security: String = "auto",     // auto | aes-128-gcm | chacha20-poly1305 | none | zero

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

    // Raw custom Xray JSON configuration (matching v2rayNG EConfigType.CUSTOM)
    val customConfig: String = "",

    val subscriptionId: String = "",
    val subscriptionUrl: String = "",
    val remark: String = "",
    var order: Int = 0,
    var lastTestedAt: Long = 0,
    // 0 = untested (renders empty string ""), > 0 = ms in green, < 0 (-1) = failed in red (matches v2rayNG testDelayMillis)
    var lastDelayMs: Int = 0,
) {
    val displayName: String
        get() = name.ifBlank {
            when {
                server.isNotBlank() && serverPort > 0 -> "$server:$serverPort"
                server.isNotBlank() -> server
                protocol == Protocol.CUSTOM -> "Custom JSON"
                else -> protocol.wire
            }
        }

    /**
     * Type description matching v2rayNG's `MainServerRowModels.buildTypeDescription`:
     * e.g. `VLESS / ws / tls` or `CUSTOM`.
     */
    val typeDescription: String
        get() {
            if (protocol == Protocol.CUSTOM) return "CUSTOM"
            return buildList {
                add(protocol.name)
                val net = transport.type.trim()
                if (net.isNotEmpty() && protocol != Protocol.WIREGUARD) add(net)
                when {
                    tls.reality -> add("reality")
                    tls.enabled -> add("tls")
                }
            }.joinToString(" / ")
        }

    /**
     * Formatted address line matching v2rayNG's `MainServerRowModels`:
     * `example.com : 443`.
     */
    val formattedAddress: String
        get() {
            val s = server.trim()
            if (s.isEmpty()) return if (protocol == Protocol.CUSTOM) "Custom Xray Configuration" else ""
            val masked = if (s.contains(":") && !s.startsWith("[")) "[$s]" else s
            return if (serverPort > 0) "$masked : $serverPort" else masked
        }

    /**
     * Delay string matching v2rayNG's `ServerAffiliationInfo.getTestDelayString()`:
     * `0` -> `""` (nothing displayed before testing), `> 0` -> `"123 ms"`, `< 0` -> `"-1 ms"`.
     */
    val testDelayString: String
        get() = if (lastDelayMs == 0) "" else "$lastDelayMs ms"

    /**
     * Identity key for deduplication (matches v2rayNG ProfileItem.duplicateIdentity).
     * Ignores id, name, order, subscriptionId, and lastDelayMs.
     */
    fun duplicateKey(): String {
        if (protocol == Protocol.CUSTOM) {
            return "custom:${customConfig.trim().hashCode()}"
        }
        return listOf(
            protocol.wire,
            server.trim().lowercase(),
            serverPort.toString(),
            uuid.trim(),
            password.trim(),
            username.trim(),
            method.trim().lowercase(),
            flow.trim(),
            transport.type.lowercase(),
            transport.host.trim().lowercase(),
            transport.path.trim(),
            transport.serviceName.trim(),
            tls.enabled.toString(),
            tls.reality.toString(),
            tls.serverName.trim().lowercase(),
            tls.realityPublicKey.trim(),
            tls.realityShortId.trim(),
        ).joinToString("|")
    }
}
