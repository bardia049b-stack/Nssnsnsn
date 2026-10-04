package app.nebulabox.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object QrImageDecoder {

    private const val MAX_SIDE = 1600

    private val reader = MultiFormatReader()

    private val hints = mapOf(
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.CHARACTER_SET to "UTF-8",
    )

    suspend fun decode(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        decodeBitmap(loadBitmap(context, uri))
    }

    private fun loadBitmap(context: Context, uri: Uri): Bitmap? = runCatching {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        var sample = 1
        while (options.outWidth / sample > MAX_SIDE || options.outHeight / sample > MAX_SIDE) {
            sample *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        }
    }.getOrNull()

    private fun decodeBitmap(bitmap: Bitmap?): String? {
        bitmap ?: return null
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)

        runCatching {
            reader.decode(BinaryBitmap(HybridBinarizer(source)), hints).text
        }.onSuccess { text ->
            if (!text.isNullOrBlank()) return text
        }

        runCatching {
            reader.decode(BinaryBitmap(GlobalHistogramBinarizer(source)), hints).text
        }.onSuccess { text ->
            if (!text.isNullOrBlank()) return text
        }

        return runCatching {
            reader.decode(BinaryBitmap(GlobalHistogramBinarizer(source.invert())), hints).text
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
