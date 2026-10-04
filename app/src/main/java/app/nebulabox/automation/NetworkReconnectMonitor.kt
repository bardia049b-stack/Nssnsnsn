package app.nebulabox.automation

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import app.nebulabox.data.SettingsStore
import app.nebulabox.service.Actions
import app.nebulabox.service.TunnelVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NetworkReconnectMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val knownNetworks = linkedSetOf<Network>()
    private var waitingForReplacement = false
    private var lastReconnectAt = 0L
    private var callback: ConnectivityManager.NetworkCallback? = null

    fun start() {
        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val reconnect = synchronized(knownNetworks) {
                    val wasEmpty = knownNetworks.isEmpty()
                    knownNetworks.add(network)
                    if (wasEmpty && waitingForReplacement) {
                        waitingForReplacement = false
                        true
                    } else {
                        false
                    }
                }
                if (reconnect) requestReconnect()
            }

            override fun onLost(network: Network) {
                val reconnect = synchronized(knownNetworks) {
                    val removed = knownNetworks.remove(network)
                    if (removed && knownNetworks.isEmpty()) {
                        waitingForReplacement = true
                        false
                    } else {
                        removed
                    }
                }
                if (reconnect) requestReconnect()
            }
        }
        runCatching {
            connectivityManager.registerNetworkCallback(request, networkCallback)
            callback = networkCallback
        }
    }

    private fun requestReconnect() {
        scope.launch {
            delay(1500L)
            val settings = runCatching { SettingsStore(appContext).current() }.getOrNull() ?: return@launch
            if (!settings.reconnectOnNetworkChange || !TunnelVpnService.isServiceAlive) return@launch
            val now = System.currentTimeMillis()
            val isOutsideCooldown = synchronized(knownNetworks) {
                if (now - lastReconnectAt < RECONNECT_COOLDOWN_MS) {
                    false
                } else {
                    lastReconnectAt = now
                    true
                }
            }
            if (!isOutsideCooldown) return@launch
            val profileId = settings.selectedProfileId ?: return@launch
            Actions.connect(appContext, profileId)
        }
    }

    fun stop() {
        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val networkCallback = callback
        callback = null
        if (connectivityManager != null && networkCallback != null) {
            runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        }
        scope.cancel()
    }

    companion object {
        private const val RECONNECT_COOLDOWN_MS = 15_000L
    }
}
