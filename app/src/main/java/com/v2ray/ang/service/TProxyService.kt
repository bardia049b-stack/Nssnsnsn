package com.v2ray.ang.service

import android.content.Context
import android.os.ParcelFileDescriptor
import app.nebulabox.data.AppSettings
import app.nebulabox.util.AppLogger
import java.io.File

class TProxyService {
    companion object {
        private const val TAG = "HevTun"

        @Volatile
        var isLoaded: Boolean = false
            private set

        @Volatile
        var isRunning: Boolean = false
            private set

        init {
            try {
                System.loadLibrary("hev-socks5-tunnel")
                isLoaded = true
                AppLogger.i(TAG, "libhev-socks5-tunnel.so loaded successfully via JNI_OnLoad")
            } catch (t: Throwable) {
                isLoaded = false
                AppLogger.w(TAG, "libhev-socks5-tunnel.so failed to load, falling back to Xray native TUN: ${t.message}")
            }
        }

        @JvmStatic
        @Suppress("FunctionName")
        external fun TProxyStartService(configPath: String, fd: Int): Boolean

        @JvmStatic
        @Suppress("FunctionName")
        external fun TProxyStopService(): Boolean

        @JvmStatic
        @Suppress("FunctionName")
        external fun TProxyIsRunning(): Boolean

        @JvmStatic
        @Suppress("FunctionName")
        external fun TProxyGetStats(): LongArray?

        @Synchronized
        fun start(
            context: Context,
            vpnInterface: ParcelFileDescriptor,
            settings: AppSettings,
        ): Boolean {
            if (!isLoaded) return false
            if (isRunning) {
                stop()
            }
            val configContent = buildConfig(settings)
            val configFile = File(context.filesDir, "hev-socks5-tunnel.yaml")
            configFile.writeText(configContent)
            AppLogger.d(TAG, "Starting hev-socks5-tunnel (fd=${vpnInterface.fd}, port=${settings.socksPort})")
            return try {
                TProxyStartService(configFile.absolutePath, vpnInterface.fd)
                isRunning = true
                AppLogger.i(TAG, "hev-socks5-tunnel started successfully")
                true
            } catch (e: Throwable) {
                AppLogger.e(TAG, "TProxyStartService failed: ${e.message}", e)
                isRunning = false
                false
            }
        }

        @Synchronized
        fun stop() {
            if (!isLoaded || !isRunning) return
            try {
                AppLogger.i(TAG, "Stopping hev-socks5-tunnel...")
                TProxyStopService()
            } catch (e: Throwable) {
                AppLogger.w(TAG, "TProxyStopService failed: ${e.message}")
            } finally {
                isRunning = false
            }
        }

        fun getStats(): LongArray? {
            if (!isLoaded || !isRunning) return null
            return try {
                TProxyGetStats()
            } catch (_: Throwable) {
                null
            }
        }

        private fun buildConfig(settings: AppSettings): String {
            val safeMtu = if (settings.mtu in 1280..1500) settings.mtu else 1500
            return buildString {
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
                appendLine("  tcp-read-write-timeout: 300000")
                appendLine("  udp-read-write-timeout: 60000")
                appendLine("  log-level: warn")
            }
        }
    }
}
