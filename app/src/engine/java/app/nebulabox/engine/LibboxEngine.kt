package app.nebulabox.engine

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.system.OsConstants
import app.nebulabox.Application
import app.nebulabox.util.AppLogger
import com.v2ray.ang.service.TProxyService
import go.Seq
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import libv2ray.ProcessFinder
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress

private const val TAG = "XrayEngine"

/**
 * Production [TunnelEngine] backed by `2dust/AndroidLibXrayLite` (`libv2ray.Libv2ray` v26.9.30 /
 * Xray-core v1.260327.1), matching the exact architecture of `2dust/v2rayNG` 2.3.10.
 */
class LibboxEngine : TunnelEngine {

    override val status = MutableStateFlow(TunnelStatus())
    override val groups = MutableStateFlow<List<OutboundGroup>>(emptyList())
    override val logs = MutableSharedFlow<TunnelEngine.LogLine>(
        replay = 200,
        extraBufferCapacity = 256,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var statsJob: Job? = null
    @Volatile
    private var isInitialized = false

    private val callbackHandler = object : CoreCallbackHandler {
        override fun startup(): Long {
            emitLog(3, "Xray-core callback: startup")
            return 0L
        }

        override fun shutdown(): Long {
            emitLog(3, "Xray-core callback: shutdown")
            return 0L
        }

        override fun onEmitStatus(code: Long, statusMsg: String?): Long {
            if (!statusMsg.isNullOrBlank()) {
                emitLog(3, "Core status [$code]: $statusMsg")
            }
            return 0L
        }
    }

    private class XrayProcessFinder(context: Context) : ProcessFinder {
        private val cm: ConnectivityManager? = context.getSystemService(ConnectivityManager::class.java)

        override fun findProcessByConnection(
            network: String,
            srcIP: String,
            srcPort: Long,
            destIP: String,
            destPort: Long,
        ): Long {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return -1L
            if (cm == null) return -1L
            val proto = when (network) {
                "tcp" -> OsConstants.IPPROTO_TCP
                "udp" -> OsConstants.IPPROTO_UDP
                else -> return -1L
            }
            if (destIP.isBlank() || destPort == 0L) return -1L
            return try {
                cm.getConnectionOwnerUid(
                    proto,
                    InetSocketAddress(srcIP, srcPort.toInt()),
                    InetSocketAddress(destIP, destPort.toInt()),
                ).toLong()
            } catch (_: Exception) {
                -1L
            }
        }
    }

    private val coreController: CoreController by lazy {
        ensureInit()
        Libv2ray.newCoreController(callbackHandler)
    }

    @Synchronized
    private fun ensureInit() {
        if (isInitialized) return
        val app = Application.instance
        val assetDir = File(app.filesDir, "assets").apply { mkdirs() }
        copyGeoAssetsIfNeeded(app, assetDir, force = false)
        Seq.setContext(app.applicationContext)
        Libv2ray.initCoreEnv(assetDir.absolutePath, "")
        isInitialized = true
        AppLogger.i(TAG, "Libv2ray initialized: version=${runCatching { Libv2ray.checkVersionX() }.getOrNull()}, assets=${assetDir.absolutePath}")
    }

    @Synchronized
    fun refreshGeoAssets() {
        val app = Application.instance
        val assetDir = File(app.filesDir, "assets").apply { mkdirs() }
        copyGeoAssetsIfNeeded(app, assetDir, force = true)
    }

    private fun copyGeoAssetsIfNeeded(context: Context, targetDir: File, force: Boolean) {
        val geoFiles = listOf(
            "geosite.dat" to 5_000_000L,
            "geoip.dat" to 5_000_000L,
            "geoip-only-cn-private.dat" to 50_000L,
        )
        for ((name, minBytes) in geoFiles) {
            val outFile = File(targetDir, name)
            if (!force && outFile.exists() && outFile.length() >= minBytes) continue
            val tmpFile = File(targetDir, "$name.tmp")
            runCatching {
                context.assets.open(name).use { input ->
                    FileOutputStream(tmpFile).use { output ->
                        input.copyTo(output)
                        output.fd.sync()
                    }
                }
                if (outFile.exists()) outFile.delete()
                tmpFile.renameTo(outFile)
                AppLogger.i(TAG, "Copied bundled asset $name (${outFile.length()} bytes)")
            }.onFailure { e ->
                tmpFile.delete()
                AppLogger.w(TAG, "Asset $name not copied: ${e.message}")
            }
        }
    }

    override val implementationName: String
        get() = runCatching {
            ensureInit()
            Libv2ray.checkVersionX()
        }.getOrDefault("Xray-core")

    override val functional: Boolean = true

    @Synchronized
    override fun start(
        profileName: String,
        config: String,
        mtu: Int,
        openTun: () -> Boolean,
    ) {
        stopInternal()
        ensureInit()

        status.value = TunnelStatus(
            state = TunnelState.STARTING,
            profileName = profileName,
        )
        emitLog(3, "Starting Xray-core for profile: $profileName")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching {
                    coreController.registerProcessFinder(XrayProcessFinder(Application.instance))
                }
            }

            val usesNativeTun = config.contains("\"protocol\":\"tun\"") || config.contains("\"protocol\": \"tun\"")
            if (usesNativeTun) {
                if (!openTun()) {
                    throw IllegalStateException("VPN permission denied or failed to establish TUN interface")
                }
                val fd = Engines.tunProvider?.tunFileDescriptor() ?: -1
                if (fd <= 0) {
                    throw IllegalStateException("Invalid TUN file descriptor: $fd")
                }
                emitLog(3, "Calling coreController.startLoop (nativeTun=true, tunFd=$fd)")
                coreController.startLoop(config, fd)
            } else {
                // Exact v2rayNG 2.3.10 order for hev-socks5-tunnel:
                // 1. Start Xray-core loop first so 127.0.0.1:10808 is already listening
                // 2. Then establish the Android VPN TUN interface
                coreController.startLoop(config, 0)
                if (!coreController.isRunning) {
                    throw IllegalStateException("Xray-core failed to enter running state")
                }
                if (!openTun()) {
                    throw IllegalStateException("VPN permission denied or failed to establish TUN interface")
                }
            }

            if (!coreController.isRunning) {
                throw IllegalStateException("Xray-core failed to enter running state")
            }

            val startedAt = System.currentTimeMillis()
            status.value = TunnelStatus(
                state = TunnelState.STARTED,
                profileName = profileName,
                startedAt = startedAt,
            )
            emitLog(3, "Xray-core started successfully")

            startStatsPolling(profileName, startedAt)
        } catch (t: Throwable) {
            AppLogger.e(TAG, "XrayEngine.start failed: ${t.message}", t)
            emitLog(1, "Failed to start Xray-core: ${t.message}")
            stopInternal()
            status.value = TunnelStatus(
                state = TunnelState.STOPPED,
                profileName = profileName,
                message = t.message ?: t.javaClass.simpleName,
            )
            throw t
        }
    }

    @Synchronized
    override fun stop() {
        val currentName = status.value.profileName
        status.value = status.value.copy(state = TunnelState.STOPPING)
        stopInternal()
        status.value = TunnelStatus(state = TunnelState.STOPPED, profileName = currentName)
        groups.value = emptyList()
    }

    private fun stopInternal() {
        statsJob?.cancel()
        statsJob = null

        runCatching {
            if (coreController.isRunning) {
                coreController.stopLoop()
                emitLog(3, "Xray-core stopped")
            }
        }.onFailure {
            AppLogger.w(TAG, "coreController.stopLoop warning: ${it.message}")
        }
    }

    private fun startStatsPolling(profileName: String, startedAt: Long) {
        statsJob?.cancel()
        statsJob = scope.launch {
            var totalUp = 0L
            var totalDown = 0L
            var lastHevUp = 0L
            var lastHevDown = 0L

            while (isActive && coreController.isRunning) {
                delay(1000L)
                if (!coreController.isRunning) break

                var deltaUp = 0L
                var deltaDown = 0L
                val rawStats = runCatching { coreController.queryAllOutboundTrafficStats() }.getOrDefault("")
                if (rawStats.isNotBlank()) {
                    for (entry in rawStats.split(';')) {
                        if (entry.isBlank()) continue
                        val parts = entry.split(',')
                        if (parts.size != 3) continue
                        val dir = parts[1]
                        val bytes = parts[2].toLongOrNull() ?: 0L
                        if (bytes <= 0L) continue
                        if (dir == "uplink") deltaUp += bytes
                        else if (dir == "downlink") deltaDown += bytes
                    }
                }

                if (deltaUp == 0L && deltaDown == 0L) {
                    val hevStats = TProxyService.getStats()
                    if (hevStats != null && hevStats.size >= 4) {
                        val curUp = hevStats[1].coerceAtLeast(0L)
                        val curDown = hevStats[3].coerceAtLeast(0L)
                        if (lastHevUp > 0L || lastHevDown > 0L) {
                            deltaUp = (curUp - lastHevUp).coerceAtLeast(0L)
                            deltaDown = (curDown - lastHevDown).coerceAtLeast(0L)
                        }
                        lastHevUp = curUp
                        lastHevDown = curDown
                    }
                }

                totalUp += deltaUp
                totalDown += deltaDown

                val current = status.value
                if (current.state == TunnelState.STARTED) {
                    status.value = current.copy(
                        profileName = profileName,
                        startedAt = startedAt,
                        uplink = deltaUp,
                        downlink = deltaDown,
                        uplinkTotal = totalUp,
                        downlinkTotal = totalDown,
                    )
                }
            }
        }
    }

    override fun measureOutboundDelay(config: String, testUrl: String): Long {
        return try {
            ensureInit()
            val url = testUrl.ifBlank { "https://www.gstatic.com/generate_204" }
            val delay = Libv2ray.measureOutboundDelay(config, url)
            AppLogger.i("Xray-core", "Real ping (MeasureOutboundDelay): ${delay}ms")
            delay
        } catch (t: Throwable) {
            AppLogger.e("Xray-core", "Real ping (MeasureOutboundDelay) error: ${t.message}")
            -1L
        }
    }

    override fun measureActiveDelay(testUrl: String): Long {
        return try {
            if (!coreController.isRunning) return -1L
            val url = testUrl.ifBlank { "https://www.gstatic.com/generate_204" }
            val delay = coreController.measureDelay(url)
            AppLogger.i("Xray-core", "Active connection delay (MeasureDelay): ${delay}ms")
            delay
        } catch (t: Throwable) {
            AppLogger.e("Xray-core", "Active connection delay (MeasureDelay) error: ${t.message}")
            -1L
        }
    }

    override fun selectOutbound(groupTag: String, itemTag: String) = Unit

    override fun urlTest(groupTag: String) = Unit

    override fun clearLogs() {
        logs.resetReplayCache()
    }

    private fun emitLog(level: Int, message: String) {
        when (level) {
            1 -> AppLogger.e("Xray-core", message)
            2 -> AppLogger.w("Xray-core", message)
            4 -> AppLogger.d("Xray-core", message)
            else -> AppLogger.i("Xray-core", message)
        }
        logs.tryEmit(
            TunnelEngine.LogLine(
                level = level,
                time = System.currentTimeMillis(),
                message = message,
            ),
        )
    }
}
