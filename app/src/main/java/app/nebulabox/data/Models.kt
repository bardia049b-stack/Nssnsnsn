package app.nebulabox.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

    val displayName: String
        get() = when (this) {
            VLESS -> "VLESS"
            VMESS -> "VMess"
            TROJAN -> "Trojan"
            SHADOWSOCKS -> "Shadowsocks"
            SOCKS -> "SOCKS"
            HTTP -> "HTTP"
            HYSTERIA2 -> "Hysteria2"
            TUIC -> "TUIC"
            WIREGUARD -> "WireGuard"
            SSH -> "SSH"
            NAIVE -> "Naive"
            CUSTOM -> "Custom JSON"
            DIRECT -> "Direct"
        }

    companion object {
        val STREAM_PROTOCOLS = setOf(VLESS, VMESS, TROJAN)

        fun fromWire(value: String): Protocol? = entries.firstOrNull {
            it.wire.equals(value, ignoreCase = true) ||
                (value.equals("hysteria", ignoreCase = true) && it == HYSTERIA2)
        }
    }
}

@Serializable
data class Transport(
    val type: String = "tcp",
    val host: String = "",
    val path: String = "",
    val headers: Map<String, String> = emptyMap(),
    val headerType: String = "none",
    val serviceName: String = "",
    val authority: String = "",
    val grpcMode: String = "gun",
    val xhttpMode: String = "auto",
    val xhttpExtra: String = "",
    val seed: String = "",
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

@Serializable
data class SubscriptionItem(
    val id: String,
    val remarks: String,
    val url: String,
    val enabled: Boolean = true,
    val updatedAt: Long = 0L,
    val userAgent: String = "",
)

@Serializable
data class Profile(
    val id: String,
    var name: String,
    val protocol: Protocol,
    val server: String = "",
    val serverPort: Int = 0,

    val username: String = "",
    val password: String = "",
    val uuid: String = "",
    val encryption: String = "none",

    val flow: String = "",
    val upMbps: Int = 0,
    val downMbps: Int = 0,
    val obfsPassword: String = "",
    val portHopping: String = "",
    val portHoppingInterval: String = "30",
    val finalMask: String = "",

    val alterId: Int = 0,
    val security: String = "auto",

    val method: String = "",
    val plugin: String = "",
    val pluginOptions: String = "",

    val privateKey: String = "",
    val peerPublicKey: String = "",
    val preSharedKey: String = "",
    val localAddresses: List<String> = emptyList(),
    val reserved: List<Int> = emptyList(),
    val mtu: Int = 1420,

    val clientVersion: String = "SSH-2.0-OpenSSH_9.8",
    val hostKeyAlgorithms: List<String> = emptyList(),
    val knownHosts: String = "",

    val transport: Transport = Transport(),
    val tls: TlsSettings = TlsSettings(),

    val customConfig: String = "",

    val subscriptionId: String = "",
    val subscriptionUrl: String = "",
    val remark: String = "",
    var order: Int = 0,
    var lastTestedAt: Long = 0,

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

    val typeDescription: String
        get() {
            if (protocol == Protocol.CUSTOM) return "Custom JSON"
            return buildList {
                add(protocol.displayName)
                if (protocol in Protocol.STREAM_PROTOCOLS) {
                    val net = transport.type.trim().lowercase()
                    if (net.isNotEmpty()) add(if (net == "httpupgrade") "httpupgrade" else net)
                    when {
                        tls.reality -> add("reality")
                        tls.enabled -> add("tls")
                    }
                }
            }.joinToString(" / ")
        }

    val formattedAddress: String
        get() {
            val s = server.trim()
            if (s.isEmpty()) return if (protocol == Protocol.CUSTOM) "Custom Xray Configuration" else ""
            val masked = if (s.contains(":") && !s.startsWith("[")) "[$s]" else s
            return if (serverPort > 0) "$masked : $serverPort" else masked
        }

    val testDelayString: String
        get() = when {
            lastDelayMs > 0 -> "$lastDelayMs ms"
            lastDelayMs < 0 || lastTestedAt > 0L -> "timeout"
            else -> ""
        }

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
