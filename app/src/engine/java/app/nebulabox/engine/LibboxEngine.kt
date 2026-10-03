package app.nebulabox.engine

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import app.nebulabox.Application
import app.nebulabox.util.AppLogger
import go.Seq
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import libv2ray.ProcessFinder
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Real [TunnelEngine] backed by `2dust/AndroidLibXrayLite` (`libv2ray.aar` v26.9.30 / Xray-core v1.260327.1),
 * matching `v2rayNG 2.3.10`'s `CoreNativeManager` & `CoreServiceManager`.
 */
class LibboxEngine : TunnelEngine {

    override val status = MutableStateFlow(TunnelStatus())
    override val groups = MutableStateFlow<List<OutboundGroup>>(emptyList())

    private val logFlow = MutableSharedFlow<TunnelEngine.LogLine>(
        replay = 200,
        extraBufferCapacity = 500,
    )
    override val logs: Flow<TunnelEngine.LogLine> = logFlow.asSharedFlow()

    override val implementationName: String
        get() = runCatching {
            ensureInit()
            "Xray-core ${Libv2ray.checkVersionX()}"
        }.getOrDefault("Xray-core (libv2ray)")

    override val functional: Boolean = true

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var statsJob: Job? = null
    private val initialized = AtomicBoolean(false)

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
                emitLog(3, "Xray status [$code]: $statusMsg")
            }
            return 0L
        }
    }

    private val coreController: CoreController by lazy {
        ensureInit()
        Libv2ray.newCoreController(callbackHandler)
    }

    private fun ensureInit() {
        if (!initialized.compareAndSet(false, true)) return
        val app = Application.instance
        val assetDir = File(app.filesDir, "assets").apply { mkdirs() }
        copyGeoAssetsIfNeeded(app, assetDir)
        Seq.setContext(app.applicationContext)
        Libv2ray.initCoreEnv(assetDir.absolutePath, "")
        AppLogger.i(TAG, "Libv2ray initialized: version=${runCatching { Libv2ray.checkVersionX() }.getOrNull()}, assets=${assetDir.absolutePath}")
    }

    private fun copyGeoAssetsIfNeeded(context: Context, targetDir: File) {
        val geoFiles = arrayOf("geosite.dat", "geoip.dat", "geoip-only-cn-private.dat")
        for (name in geoFiles) {
            val outFile = File(targetDir, name)
            if (outFile.exists() && outFile.length() > 1024L) continue
            runCatching {
                context.assets.open(name).use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
                AppLogger.i(TAG, "Copied bundled asset $name (${outFile.length()} bytes)")
            }.onFailure { e ->
                AppLogger.w(TAG, "Asset $name not copied: ${e.message}")
            }
        }
    }

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
            // 1. Open the Android VPN TUN interface first
            if (!openTun()) {
                throw IllegalStateException("VPN permission denied or TUN creation failed")
            }

            // 2. Register ProcessFinder on Android Q+ (just like v2rayNG CoreServiceManager)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching {
                    val cm = Application.instance.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    if (cm != null) {
                        coreController.registerProcessFinder(AndroidProcessFinder(cm))
                    }
                }
            }

            // 3. Determine whether Xray-core should own the TUN fd directly (`"protocol":"tun"`)
            //    or `hev-socks5-tunnel` owns the TUN fd (`tunFd = 0` passed to startLoop)
            val usesNativeXrayTun = config.contains("\"protocol\":\"tun\"") || config.contains("\"protocol\": \"tun\"")
            val tunFd = if (usesNativeXrayTun) {
                Engines.tunProvider?.tunFileDescriptor()?.takeIf { it > 0 } ?: 0
            } else {
                0
            }

            emitLog(3, "Calling coreController.startLoop (nativeTun=$usesNativeXrayTun, tunFd=$tunFd)")
            coreController.startLoop(config, tunFd)

            if (!coreController.isRunning) {
                throw IllegalStateException("Xray-core failed to enter running state")
            }

            val startedAt = System.currentTimeMillis()
            status.value = TunnelStatus(
                state = TunnelState.STARTED,
                profileName = profileName,
                startedAt = startedAt,
            )
            emitLog(3, "Xray-core running (${Libv2ray.checkVersionX()})")

            startTrafficStatsPolling(profileName, startedAt)
        } catch (t: Throwable) {
            AppLogger.e(TAG, "XrayEngine.start failed: ${t.message}", t)
            emitLog(5, "Failed to start Xray-core: ${t.message}")
            stopInternal()
            status.value = TunnelStatus(
                state = TunnelState.STOPPED,
                profileName = profileName,
                message = t.message ?: "Failed to start Xray-core",
            )
            throw t
        }
    }

    private fun startTrafficStatsPolling(profileName: String, startedAt: Long) {
        statsJob?.cancel()
        statsJob = scope.launch {
            var totalUp = 0L
            var totalDown = 0L
            var lastTick = SystemClock.elapsedRealtime()

            while (isActive && runCatching { coreController.isRunning }.getOrDefault(false)) {
                delay(1000L)
                val now = SystemClock.elapsedRealtime()
                val elapsedSec = ((now - lastTick) / 1000.0).coerceAtLeast(0.5)
                lastTick = now

                var deltaUp = 0L
                var deltaDown = 0L

                val rawStats = runCatching { coreController.queryAllOutboundTrafficStats() }.getOrDefault("")
                if (rawStats.isNotBlank()) {
                    // Format from AndroidLibXrayLite: tag,direction,value;tag,direction,value;
                    rawStats.split(';').forEach { entry ->
                        if (entry.isBlank()) return@forEach
                        val parts = entry.split(',', limit = 3)
                        if (parts.size != 3) return@forEach
                        val tag = parts[0]
                        val direction = parts[1]
                        val value = parts[2].toLongOrNull() ?: return@forEach
                        if (tag == "proxy" || tag == "direct") {
                            if (direction == "uplink") deltaUp += value
                            else if (direction == "downlink") deltaDown += value
                        }
                    }
                }

                totalUp += deltaUp
                totalDown += deltaDown
                val rateUp = (deltaUp / elapsedSec).toLong()
                val rateDown = (deltaDown / elapsedSec).toLong()

                val runtime = Runtime.getRuntime()
                val usedMem = runtime.totalMemory() - runtime.freeMemory()

                val current = status.value
                if (current.state == TunnelState.STARTED) {
                    status.value = current.copy(
                        profileName = profileName,
                        uplink = rateUp,
                        downlink = rateDown,
                        uplinkTotal = totalUp,
                        downlinkTotal = totalDown,
                        memory = usedMem,
                        startedAt = startedAt,
                    )
                }
            }
        }
    }

    @Synchronized
    override fun stop() {
        val lastProfile = status.value.profileName
        status.value = status.value.copy(state = TunnelState.STOPPING)
        stopInternal()
        status.value = TunnelStatus(
            state = TunnelState.STOPPED,
            profileName = lastProfile,
        )
    }

    private fun stopInternal() {
        statsJob?.cancel()
        statsJob = null
        runCatching {
            if (initialized.get() && coreController.isRunning) {
                coreController.stopLoop()
            }
        }.onFailure { e ->
            AppLogger.w(TAG, "coreController.stopLoop warning: ${e.message}")
        }
    }

    override fun measureOutboundDelay(config: String, testUrl: String): Long {
        return try {
            ensureInit()
            Libv2ray.measureOutboundDelay(config, testUrl)
        } catch (t: Throwable) {
            AppLogger.w(TAG, "measureOutboundDelay failed: ${t.message}")
            -1L
        }
    }

    override fun measureActiveDelay(testUrl: String): Long {
        return try {
            if (!initialized.get() || !coreController.isRunning) return -1L
            coreController.measureDelay(testUrl)
        } catch (t: Throwable) {
            AppLogger.w(TAG, "measureActiveDelay failed: ${t.message}")
            -1L
        }
    }

    override fun selectOutbound(groupTag: String, itemTag: String) {
        // Not applicable to single-outbound Xray configs
    }

    override fun urlTest(groupTag: String) {
        scope.launch {
            val d = measureActiveDelay("https://www.gstatic.com/generate_204")
            emitLog(3, "Active tunnel delay test: ${d}ms")
        }
    }

    override fun clearLogs() {
        logFlow.resetReplayCache()
    }

    private fun emitLog(level: Int, message: String) {
        val lvl = when (level) {
            5, 6 -> AppLogger.Level.ERROR
            4 -> AppLogger.Level.WARN
            2 -> AppLogger.Level.DEBUG
            else -> AppLogger.Level.INFO
        }
        AppLogger.log(lvl, "Xray-core", message)
        logFlow.tryEmit(TunnelEngine.LogLine(level, System.currentTimeMillis(), message))
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private class AndroidProcessFinder(
        private val connectivityManager: ConnectivityManager,
    ) : ProcessFinder {
        override fun findProcessByConnection(
            network: String?,
            srcIP: String?,
            srcPort: Long,
            destIP: String?,
            destPort: Long,
        ): Long {
            return try {
                val proto = when (network?.lowercase()) {
                    "tcp", "tcp4", "tcp6" -> android.system.OsConstants.IPPROTO_TCP
                    "udp", "udp4", "udp6" -> android.system.OsConstants.IPPROTO_UDP
                    else -> return -1L
                }
                val local = InetSocketAddress(srcIP ?: return -1L, srcPort.toInt())
                val remote = InetSocketAddress(destIP ?: return -1L, destPort.toInt())
                connectivityManager.getConnectionOwnerUid(proto, local, remote).toLong()
            } catch (_: Throwable) {
                -1L
            }
        }
    }

    companion object {
        private const val TAG = "XrayEngine"
    }
}
