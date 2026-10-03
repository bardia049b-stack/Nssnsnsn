package app.nebulabox.core.serializer

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer as GsonJsonSerializer
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

object JsonSerializer {
    private const val TAG = "JsonSerializer"
    private val gson = Gson()

    fun toJson(src: Any?): String {
        return gson.toJson(src)
    }

    fun <T> fromJson(src: String, cls: Class<T>): T? {
        if (src.isBlank()) return null
        return try {
            gson.fromJson(src, cls)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON to ${cls.simpleName}", e)
            null
        }
    }

    fun <T> fromJsonSafe(src: String?, cls: Class<T>): T? {
        if (src.isNullOrBlank()) return null
        return fromJson(src, cls)
    }

    fun <T> fromJson(src: String, typeOfT: Type): T? {
        if (src.isBlank()) return null
        return try {
            gson.fromJson(src, typeOfT)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON to $typeOfT", e)
            null
        }
    }

    fun toJsonPretty(src: Any?): String? {
        if (src == null) return null
        val gsonPre = GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .registerTypeAdapter(
                object : TypeToken<Double>() {}.type,
                GsonJsonSerializer<Double> { srcDouble: Double?, _: Type?, _: JsonSerializationContext? ->
                    JsonPrimitive(srcDouble?.toInt())
                }
            )
            .create()
        return gsonPre.toJson(src)
    }

    fun parseString(src: String?): JsonObject? {
        if (src == null) return null
        return try {
            JsonParser.parseString(src).asJsonObject
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON string to JsonObject", e)
            null
        }
    }
}
