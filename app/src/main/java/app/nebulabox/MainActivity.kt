package app.nebulabox

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.nebulabox.data.ProfileStore
import app.nebulabox.data.SettingsStore
import app.nebulabox.locale.LocaleManager
import app.nebulabox.ui.NebulaTheme
import app.nebulabox.ui.NebulaViewModel
import app.nebulabox.ui.NebulaViewModelFactory
import app.nebulabox.ui.RootScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: NebulaViewModel by viewModels {
        NebulaViewModelFactory(
            application,
            ProfileStore(applicationContext),
            SettingsStore(applicationContext),
        )
    }

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                viewModel.onVpnPermissionGranted()
            } else {
                viewModel.onVpnPermissionDenied()
            }
        }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        observeVpnRequests()
        handleIncomingShare(intent)

        setContent {
            val settings by viewModel.settings.collectAsState()
            NebulaTheme(
                theme = settings.theme,
                dynamicColor = settings.dynamicColor,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    RootScreen(
                        viewModel = viewModel,
                        onRequestVpnPermission = { vpnPermissionLauncher.launch(it) },
                    )
                }
            }
        }

        lifecycleScope.launch { viewModel.consumePendingImport() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingShare(intent)
        lifecycleScope.launch { viewModel.consumePendingImport() }
    }

    /** The permission dialog must be launched from an activity, so route it here. */
    private fun observeVpnRequests() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.vpnPermissionRequests.collect { intent ->
                    vpnPermissionLauncher.launch(intent)
                }
            }
        }
    }

    /** Accepts vless:// style links and plain text shared from other apps. */
    private fun handleIncomingShare(intent: Intent?) {
        intent ?: return
        val text = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }
        if (!text.isNullOrBlank()) {
            viewModel.submitImportText(text)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }
}
