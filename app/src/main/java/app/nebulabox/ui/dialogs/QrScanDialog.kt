package app.nebulabox.ui.dialogs

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.nebulabox.R
import app.nebulabox.util.QrFrameDecoder
import app.nebulabox.util.QrImageDecoder
import kotlinx.coroutines.launch
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun QrScanDialog(
    onDismiss: () -> Unit,
    onScanned: (String) -> Unit,
) {
    val context = LocalContext.current
    var cameraPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var scanMessage by remember { mutableStateOf<String?>(null) }
    val delivered = remember { AtomicBoolean(false) }
    val scanningActive = remember { AtomicBoolean(true) }
    val scope = rememberCoroutineScope()
    val currentOnScanned by rememberUpdatedState(onScanned)

    DisposableEffect(Unit) {
        onDispose { scanningActive.set(false) }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        cameraPermissionGranted = granted
        if (!granted) scanMessage = context.getString(R.string.qr_camera_denied)
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            scanMessage = context.getString(R.string.qr_reading)
            scope.launch {
                val value = QrImageDecoder.decode(context, uri)
                if (value != null && scanningActive.get() && delivered.compareAndSet(false, true)) {
                    currentOnScanned(value)
                } else if (value == null) {
                    scanMessage = context.getString(R.string.qr_not_found)
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        title = { Text(stringResource(R.string.scan_qr_code)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (cameraPermissionGranted) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CameraQrPreview(
                            onScanned = { value ->
                                if (scanningActive.get() && delivered.compareAndSet(false, true)) {
                                    currentOnScanned(value)
                                }
                            },
                            onError = { scanMessage = it },
                        )
                    }
                    Text(
                        text = "Point the camera at a JavidTun share QR code.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = scanMessage ?: stringResource(R.string.qr_camera_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Allow camera")
                    }
                }
                if (cameraPermissionGranted && scanMessage != null) {
                    Text(
                        text = scanMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { galleryLauncher.launch("image/*") }) {
                Text("Choose from gallery")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun CameraQrPreview(
    onScanned: (String) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnScanned by rememberUpdatedState(onScanned)
    val currentOnError by rememberUpdatedState(onError)
    val delivered = remember { AtomicBoolean(false) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var analysisUseCase by remember { mutableStateOf<ImageAnalysis?>(null) }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                val provider = runCatching { providerFuture.get() }.getOrNull()
                if (provider == null) {
                    currentOnError(context.getString(R.string.qr_camera_unavailable))
                    return@addListener
                }
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { imageProxy ->
                    val value = QrFrameDecoder.decode(imageProxy)
                    imageProxy.close()
                    if (value != null && delivered.compareAndSet(false, true)) {
                        currentOnScanned(value)
                    }
                }
                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                    cameraProvider = provider
                    analysisUseCase = analysis
                }.onFailure {
                    currentOnError(context.getString(R.string.qr_camera_preview_failed))
                }
            },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            analysisUseCase?.clearAnalyzer()
            cameraProvider?.unbindAll()
            executor.shutdown()
            cameraProvider = null
            analysisUseCase = null
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxWidth().height(250.dp),
    )
}
