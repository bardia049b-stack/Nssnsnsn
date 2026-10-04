package app.nebulabox.util

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object QrImageDecoder {

    suspend fun decode(context: Context, uri: Uri): String? = suspendCancellableCoroutine { continuation ->
        val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrNull()
        if (image == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()

        BarcodeScanning.getClient(options).process(image)
            .addOnSuccessListener { barcodes ->
                val value = barcodes.firstNotNullOfOrNull { barcode ->
                    barcode.rawValue?.trim()?.takeIf { it.isNotEmpty() }
                }
                continuation.resume(value)
            }
            .addOnFailureListener {
                continuation.resume(null)
            }
    }
}
