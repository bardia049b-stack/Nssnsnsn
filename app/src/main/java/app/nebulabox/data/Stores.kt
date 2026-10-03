package app.nebulabox.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json as KJson

private val Context.profileStore by preferencesDataStore(name = "profiles")
private val Context.settingsStore by preferencesDataStore(name = "settings")

private val PROFILES_KEY = stringPreferencesKey("profiles")
private val SETTINGS_KEY = stringPreferencesKey("settings")

private val kjson = KJson { ignoreUnknownKeys = true; encodeDefaults = true }

private val profileListSerializer = ListSerializer(Profile.serializer())
private val settingsSerializer = AppSettings.serializer()

/**
 * Profiles live as one serialised array. The working set for a personal VPN
 * client is tens of entries, so a single read-modify-write keeps the code
 * small and avoids a database dependency.
 */
class ProfileStore(private val context: Context) {

    val profiles: Flow<List<Profile>> = context.profileStore.data.map { prefs ->
        val raw = prefs[PROFILES_KEY] ?: return@map emptyList()
        runCatching { kjson.decodeFromString(profileListSerializer, raw) }.getOrDefault(emptyList())
    }

    suspend fun all(): List<Profile> = profiles.first()

    suspend fun upsert(profile: Profile) = write { list ->
        val index = list.indexOfFirst { it.id == profile.id }
        if (index >= 0) list.toMutableList().also { it[index] = profile } else list + profile
    }

    suspend fun addAll(newProfiles: List<Profile>) = write { list ->
        val baseOrder = list.maxOfOrNull { it.order } ?: 0
        list + newProfiles.mapIndexed { index, profile ->
            profile.copy(order = baseOrder + index + 1)
        }
    }

    suspend fun delete(id: String) = write { list -> list.filterNot { it.id == id } }

    suspend fun clear() = write { emptyList() }

    suspend fun update(id: String, transform: (Profile) -> Profile) = write { list ->
        list.map { if (it.id == id) transform(it) else it }
    }

    suspend fun move(from: Int, to: Int) = write { list ->
        if (from !in list.indices || to !in list.indices) return@write list
        val copy = list.toMutableList()
        copy.add(to, copy.removeAt(from))
        copy.mapIndexed { index, profile -> profile.copy(order = index) }
    }

    suspend fun exportJson(): String = kjson.encodeToString(profileListSerializer, all())

    suspend fun importJson(text: String): Int {
        val parsed = runCatching { kjson.decodeFromString(profileListSerializer, text) }.getOrNull()
            ?: return 0
        addAll(parsed)
        return parsed.size
    }

    private suspend fun write(transform: (List<Profile>) -> List<Profile>) {
        context.profileStore.edit { prefs ->
            val raw = prefs[PROFILES_KEY] ?: "[]"
            val current = runCatching { kjson.decodeFromString(profileListSerializer, raw) }
                .getOrDefault(emptyList())
            prefs[PROFILES_KEY] = kjson.encodeToString(profileListSerializer, transform(current))
        }
    }
}

class SettingsStore(private val context: Context) {

    val settings: Flow<AppSettings> = context.settingsStore.data.map { prefs ->
        val raw = prefs[SETTINGS_KEY]
        val decoded = if (raw == null) {
            AppSettings()
        } else {
            runCatching { kjson.decodeFromString(settingsSerializer, raw) }.getOrDefault(AppSettings())
        }
        decoded.normalized()
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsStore.edit { prefs ->
            val raw = prefs[SETTINGS_KEY]
            val current = if (raw == null) {
                AppSettings()
            } else {
                runCatching { kjson.decodeFromString(settingsSerializer, raw) }.getOrDefault(AppSettings())
            }.normalized()
            prefs[SETTINGS_KEY] = kjson.encodeToString(settingsSerializer, transform(current))
        }
    }

    suspend fun current(): AppSettings = settings.first()
}
