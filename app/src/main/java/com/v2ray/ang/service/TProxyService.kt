package com.v2ray.ang.service

import android.content.Context
import android.os.ParcelFileDescriptor
import app.nebulabox.data.AppSettings
import app.nebulabox.util.AppLogger
import java.io.File

/**
 * JNI bridge for `libhev-socks5-tunnel.so` (from `2dust/v2rayNG` 2.3.10).
 *
 * Package and class name MUST remain `com.v2ray.ang.service.TProxyService` because
 * `libhev-socks5-tunnel.so` registers JNI methods on `com/v2ray/ang/service/TProxyService`
 * inside `JNI_OnLoad`.
 */
class TProxyService {

    companion object {
        private const val TAG = "HevTun"

        @Volatile
        var isLoaded: Boolean = false
            private set

        @Volatile
        var isRunning: Boolean = false
            private set

        @JvmStatic
        @Suppress("FunctionName")
        private external fun TProxyStartService(configPath: String, fd: Int)

        @JvmStatic
        @Suppress("FunctionName")
        private external fun TProxyStopService()

        @JvmStatic
        @Suppress("FunctionName")
        private external fun TProxyGetStats(): LongArray?

        init {
            isLoaded = try {
                System.loadLibrary("hev-socks5-tunnel")
                AppLogger.i(TAG, "libhev-socks5-tunnel.so loaded successfully")
                true
            } catch (t: Throwable) {
                AppLogger.w(TAG, "libhev-socks5-tunnel.so not available, will use Xray native TUN: ${t.message}")
                false
            }
        }

        fun start(context: Context, vpnInterface: ParcelFileDescriptor, settings: AppSettings): Boolean {
            if (!isLoaded) return false
            return try {
                if (isRunning) {
                    runCatching { TProxyStopService() }
                    isRunning = false
                }
                val configContent = buildHevConfig(settings)
                val configFile = File(context.filesDir, "hev-socks5-tunnel.yaml").apply {
                    writeText(configContent)
                }
                AppLogger.i(TAG, "Starting hev-socks5-tunnel (fd=${vpnInterface.fd}, port=${settings.socksPort})")
                TProxyStartService(configFile.absolutePath, vpnInterface.fd)
                isRunning = true
                true
            } catch (t: Throwable) {
                AppLogger.e(TAG, "Failed to start hev-socks5-tunnel: ${t.message}", t)
                isRunning = false
                false
            }
        }

        fun stop() {
            if (!isLoaded || !isRunning) return
            try {
                AppLogger.i(TAG, "Stopping hev-socks5-tunnel")
                TProxyStopService()
            } catch (t: Throwable) {
                AppLogger.e(TAG, "Failed to stop hev-socks5-tunnel: ${t.message}", t)
            } finally {
                isRunning = false
            }
        }

        fun getStats(): LongArray? {
            if (!isLoaded || !isRunning) return null
            return runCatching { TProxyGetStats() }.getOrNull()
        }

        private fun buildHevConfig(settings: AppSettings): String = buildString {
            val safeMtu = if (settings.mtu in 1280..1500) settings.mtu else 1500
            appendLine("tunnel:")
            appendLine("  mtu: $safeMtu")
            appendLine("  ipv4: 10.10.14.1")
            if (settings.ipv6) {
                appendLine("  ipv6: 'fc00::10:10:14:1'")
            }
            appendLine("socks5:")
            appendLine("  port: ${settings.socksPort}")
            appendLine("  address: 127.0.0.1")
            appendLine("  udp: 'udp'")
            appendLine("misc:")
            appendLine("  task-stack-size: 81920")
            appendLine("  tcp-buffer-size: 65536")
            appendLine("  read-write-timeout: 300000")
            appendLine("  connect-timeout: 10000")
            appendLine("  log-file: stderr")
            appendLine("  log-level: warn")
        }
    }
}
