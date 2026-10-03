package app.nebulabox.util

import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import app.nebulabox.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {

    enum class Level(val code: Int, val label: String) {
        DEBUG(2, "D"),
        INFO(3, "I"),
        WARN(4, "W"),
        ERROR(5, "E"),
    }

    data class LogEntry(
        val id: Long,
        val timestamp: Long,
        val level: Level,
        val tag: String,
        val message: String,
        val stacktrace: String? = null,
    )

    data class CrashReport(
        val id: String,
        val timestamp: Long,
        val source: String,
        val summary: String,
        val details: String,
    )

    private const val MAX_MEMORY_ENTRIES = 600
    private const val MAX_CRASH_FILES = 25

    private lateinit var appContext: Context
    private lateinit var crashDir: File
    private lateinit var logFile: File
    private lateinit var lastConfigFile: File

    private val nextId = java.util.concurrent.atomic.AtomicLong(1L)
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _crashes = MutableStateFlow<List<CrashReport>>(emptyList())
    val crashes: StateFlow<List<CrashReport>> = _crashes.asStateFlow()

    private val _lastGeneratedConfig = MutableStateFlow("")
    val lastGeneratedConfig: StateFlow<String> = _lastGeneratedConfig.asStateFlow()

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            appContext = context.applicationContext
            crashDir = File(appContext.filesDir, "crash_logs").apply { mkdirs() }
            logFile = File(appContext.filesDir, "app_debug.log")
            lastConfigFile = File(appContext.filesDir, "last_config.json")

            runCatching {
                if (lastConfigFile.exists()) {
                    _lastGeneratedConfig.value = lastConfigFile.readText()
                }
            }

            archiveNativeGoCrashReports()

            installUncaughtExceptionHandler()

            refreshNativeCrashes()

            initialized = true
            i("AppLogger", "Initialized on ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})")
        }
    }

    fun d(tag: String, message: String) = log(Level.DEBUG, tag, message, null)
    fun i(tag: String, message: String) = log(Level.INFO, tag, message, null)
    fun w(tag: String, message: String, tr: Throwable? = null) = log(Level.WARN, tag, message, tr)
    fun e(tag: String, message: String, tr: Throwable? = null) = log(Level.ERROR, tag, message, tr)

    fun log(level: Level, tag: String, message: String, tr: Throwable? = null) {
        val traceStr = tr?.let { stackTraceString(it) }
        val fullMsg = if (traceStr != null) "$message\n$traceStr" else message

        when (level) {
            Level.DEBUG -> Log.d(tag, fullMsg)
            Level.INFO -> Log.i(tag, fullMsg)
            Level.WARN -> Log.w(tag, fullMsg)
            Level.ERROR -> Log.e(tag, fullMsg)
        }

        val now = System.currentTimeMillis()
        val entry = LogEntry(
            id = nextId.getAndIncrement(),
            timestamp = now,
            level = level,
            tag = tag,
            message = message,
            stacktrace = traceStr,
        )

        synchronized(_logs) {
            val current = _logs.value
            val updated = if (current.size >= MAX_MEMORY_ENTRIES) {
                current.drop(current.size - MAX_MEMORY_ENTRIES + 1) + entry
            } else {
                current + entry
            }
            _logs.value = updated
        }

        if (::logFile.isInitialized) {
            runCatching {
                if (logFile.exists() && logFile.length() > 512 * 1024) {
                    val tail = logFile.readText().takeLast(200 * 1024)
                    logFile.writeText(tail)
                }
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(now))
                logFile.appendText("$ts [${level.label}/$tag] $fullMsg\n")
            }
        }
    }

    fun recordGeneratedConfig(config: String) {
        _lastGeneratedConfig.value = config
        if (::lastConfigFile.isInitialized) {
            runCatching { lastConfigFile.writeText(config) }
        }
    }

    fun clearLogs() {
        synchronized(_logs) {
            _logs.value = emptyList()
        }
        if (::logFile.isInitialized) {
            runCatching { logFile.writeText("") }
        }
    }

    fun clearCrashes() {
        if (!::crashDir.isInitialized) return
        runCatching {
            crashDir.listFiles()?.forEach { it.delete() }
            getCandidateNativeDirs().forEach { dir ->
                dir.listFiles()?.filter { it.name.startsWith("CrashReport") && it.name.endsWith(".log") }
                    ?.forEach { runCatching { it.writeText("") } }
            }
        }
        refreshNativeCrashes()
    }

    fun archiveNativeGoCrashReports() {
        if (!::crashDir.isInitialized) return
        runCatching {
            for (dir in getCandidateNativeDirs()) {
                val files = dir.listFiles() ?: continue
                for (file in files) {
                    if (file.isFile && file.name.startsWith("CrashReport") && file.name.endsWith(".log")) {
                        val content = runCatching { file.readText().trim() }.getOrDefault("")
                        if (content.isNotEmpty()) {
                            val ts = file.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()
                            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(ts))
                            val target = File(crashDir, "native_go_crash_${stamp}_${file.name}")
                            if (!target.exists()) {
                                val header = buildString {
                                    appendLine("=== NATIVE GO / LIBBOX CRASH REPORT ===")
                                    appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(ts))}")
                                    appendLine("Source File: ${file.absolutePath}")
                                    appendLine("Device: ${Build.FINGERPRINT}")
                                    appendLine("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                                    appendLine("=======================================")
                                    appendLine()
                                    appendLine(content)
                                }
                                target.writeText(header)
                            }
                            runCatching { file.writeText("") }
                        }
                    }
                }
            }
            pruneOldCrashFiles()
        }
    }

    fun refreshNativeCrashes() {
        if (!::crashDir.isInitialized) return
        runCatching {
            val list = mutableListOf<CrashReport>()

            for (dir in getCandidateNativeDirs()) {
                val files = dir.listFiles() ?: continue
                for (file in files) {
                    if (file.isFile && file.name.startsWith("CrashReport") && file.name.endsWith(".log")) {
                        val content = runCatching { file.readText().trim() }.getOrDefault("")
                        if (content.isNotEmpty()) {
                            list += CrashReport(
                                id = "live_${file.name}",
                                timestamp = file.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis(),
                                source = "NATIVE (libbox.so)",
                                summary = content.lines().firstOrNull { it.isNotBlank() }?.take(120) ?: "Native libbox stderr (${file.name})",
                                details = content,
                            )
                        }
                    }
                }
            }

            val archived = crashDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".log") }
                ?.sortedByDescending { it.lastModified() }
                .orEmpty()

            for (file in archived) {
                val content = runCatching { file.readText() }.getOrDefault("")
                if (content.isBlank()) continue
                val isNative = file.name.startsWith("native_go_")
                val firstLine = content.lines().firstOrNull {
                    it.contains("Exception") || it.contains("Error") || it.contains("panic:") || it.contains("SIG")
                } ?: if (isNative) "Native libbox.so Crash" else "Application Crash"
                list += CrashReport(
                    id = file.name,
                    timestamp = file.lastModified(),
                    source = if (isNative) "NATIVE (libbox.so)" else "JVM",
                    summary = firstLine.take(120),
                    details = content,
                )
            }

            _crashes.value = list
        }
    }

    fun exportLogsText(): String = buildString {
        append(deviceInfoHeader())
        appendLine()
        val tf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        for (e in _logs.value) {
            appendLine("${tf.format(Date(e.timestamp))} ${e.level.label}/${e.tag}: ${e.message}")
            if (!e.stacktrace.isNullOrBlank()) {
                appendLine(e.stacktrace)
            }
        }
    }

    fun exportCrashesText(): String = buildString {
        append(deviceInfoHeader())
        appendLine()
        val list = _crashes.value
        if (list.isEmpty()) {
            appendLine("(No crash reports recorded)")
        } else {
            for (c in list) {
                appendLine("----- [${c.source}] ${c.summary} -----")
                appendLine(c.details)
                appendLine()
            }
        }
    }

    fun deviceInfoHeader(): String = buildString {
        appendLine("App: JavidTun v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Engine Compiled: ${BuildConfig.HAS_ENGINE}")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.PRODUCT})")
        appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("Fingerprint: ${Build.FINGERPRINT}")
        appendLine("Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
        appendLine("Process PID: ${Process.myPid()}")
        appendLine("Recorded Crashes: ${_crashes.value.size}")
    }

    fun readSystemLogcat(maxLines: Int = 350): String {
        return runCatching {
            val pid = Process.myPid().toString()
            val process = ProcessBuilder("logcat", "-d", "-v", "time", "--pid=$pid", "-t", maxLines.toString())
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            if (output.isNotBlank()) {
                output
            } else {
                val p2 = ProcessBuilder("logcat", "-d", "-v", "time", "-t", maxLines.toString())
                    .redirectErrorStream(true)
                    .start()
                val out2 = p2.inputStream.bufferedReader().readText()
                p2.waitFor()
                out2
            }
        }.getOrElse { "Failed to read logcat: ${it.message}" }
    }

    private fun getCandidateNativeDirs(): List<File> {
        if (!::appContext.isInitialized) return emptyList()
        return listOfNotNull(
            appContext.filesDir,
            runCatching { appContext.getExternalFilesDir(null) }.getOrNull(),
        ).distinctBy { it.absolutePath }
    }

    private fun installUncaughtExceptionHandler() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val now = System.currentTimeMillis()
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(now))
                val file = File(crashDir, "jvm_crash_${stamp}.log")
                val report = buildString {
                    appendLine("=== APPLICATION CRASH REPORT ===")
                    appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(now))}")
                    appendLine("Thread: ${thread.name} (id=${thread.id})")
                    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("Fingerprint: ${Build.FINGERPRINT}")
                    appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                    appendLine("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    appendLine("================================")
                    appendLine()
                    appendLine("Stack Trace:")
                    appendLine(stackTraceString(throwable))
                    appendLine()
                    appendLine("Recent App Logs:")
                    _logs.value.takeLast(50).forEach { entry ->
                        val t = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(entry.timestamp))
                        appendLine("$t [${entry.level.label}/${entry.tag}] ${entry.message}")
                    }
                }
                file.writeText(report)
                pruneOldCrashFiles()
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun pruneOldCrashFiles() {
        runCatching {
            val files = crashDir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: return
            if (files.size > MAX_CRASH_FILES) {
                files.drop(MAX_CRASH_FILES).forEach { it.delete() }
            }
        }
    }

    private fun stackTraceString(tr: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        tr.printStackTrace(pw)
        pw.flush()
        return sw.toString()
    }
}
