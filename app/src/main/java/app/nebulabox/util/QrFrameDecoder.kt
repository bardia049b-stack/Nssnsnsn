package app.nebulabox.util

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.atomic.AtomicInteger

object QrFrameDecoder {

    private val reader = MultiFormatReader()

    private val hints = mapOf(
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.CHARACTER_SET to "UTF-8",
    )

    private val frames = AtomicInteger(0)

    fun decode(image: ImageProxy): String? {
        if (frames.incrementAndGet() % 2 != 0) return null
        val bitmap = runCatching { image.toBitmap() }.getOrNull() ?: return null
        val rotation = image.imageInfo.rotationDegrees
        val oriented = if (rotation == 0) {
            bitmap
        } else {
            runCatching {
                Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    bitmap.width,
                    bitmap.height,
                    Matrix().apply { postRotate(rotation.toFloat()) },
                    true,
                )
            }.getOrDefault(bitmap)
        }
        if (oriented !== bitmap) bitmap.recycle()
        return decodeBitmap(oriented)
    }

    private fun decodeBitmap(bitmap: Bitmap): String? {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)

        runCatching {
            reader.decode(BinaryBitmap(HybridBinarizer(source)), hints).text
        }.onSuccess { text ->
            if (!text.isNullOrBlank()) return text
        }

        return runCatching {
            reader.decode(BinaryBitmap(GlobalHistogramBinarizer(source)), hints).text
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
