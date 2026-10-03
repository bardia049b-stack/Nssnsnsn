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
private val SUBSCRIPTIONS_KEY = stringPreferencesKey("subscriptions")
private val SETTINGS_KEY = stringPreferencesKey("settings")

private val kjson = KJson { ignoreUnknownKeys = true; encodeDefaults = true }

private val profileListSerializer = ListSerializer(Profile.serializer())
private val subscriptionListSerializer = ListSerializer(SubscriptionItem.serializer())
private val settingsSerializer = AppSettings.serializer()

class ProfileStore(private val context: Context) {

    val profiles: Flow<List<Profile>> = context.profileStore.data.map { prefs ->
        val raw = prefs[PROFILES_KEY] ?: return@map emptyList()
        runCatching { kjson.decodeFromString(profileListSerializer, raw) }.getOrDefault(emptyList())
    }

    val subscriptions: Flow<List<SubscriptionItem>> = context.profileStore.data.map { prefs ->
        val raw = prefs[SUBSCRIPTIONS_KEY] ?: return@map emptyList()
        runCatching { kjson.decodeFromString(subscriptionListSerializer, raw) }.getOrDefault(emptyList())
    }

    suspend fun all(): List<Profile> = profiles.first()

    suspend fun allSubscriptions(): List<SubscriptionItem> = subscriptions.first()

    suspend fun upsertSubscription(item: SubscriptionItem) {
        context.profileStore.edit { prefs ->
            val raw = prefs[SUBSCRIPTIONS_KEY] ?: "[]"
            val list = runCatching { kjson.decodeFromString(subscriptionListSerializer, raw) }
                .getOrDefault(emptyList())
            val idx = list.indexOfFirst { it.id == item.id }
            val updated = if (idx >= 0) list.toMutableList().also { it[idx] = item } else list + item
            prefs[SUBSCRIPTIONS_KEY] = kjson.encodeToString(subscriptionListSerializer, updated)
        }
    }

    suspend fun deleteSubscription(subId: String, removeProfiles: Boolean = true) {
        context.profileStore.edit { prefs ->
            val rawSubs = prefs[SUBSCRIPTIONS_KEY] ?: "[]"
            val subs = runCatching { kjson.decodeFromString(subscriptionListSerializer, rawSubs) }
                .getOrDefault(emptyList())
            prefs[SUBSCRIPTIONS_KEY] = kjson.encodeToString(
                subscriptionListSerializer,
                subs.filterNot { it.id == subId },
            )
            if (removeProfiles) {
                val rawProf = prefs[PROFILES_KEY] ?: "[]"
                val profs = runCatching { kjson.decodeFromString(profileListSerializer, rawProf) }
                    .getOrDefault(emptyList())
                prefs[PROFILES_KEY] = kjson.encodeToString(
                    profileListSerializer,
                    profs.filterNot { it.subscriptionId == subId },
                )
            }
        }
    }

    suspend fun replaceSubscriptionProfiles(subId: String, subUrl: String, newProfiles: List<Profile>) = write { list ->
        val kept = list.filterNot { it.subscriptionId == subId }
        val baseOrder = kept.maxOfOrNull { it.order } ?: 0
        kept + newProfiles.mapIndexed { index, profile ->
            profile.copy(
                subscriptionId = subId,
                subscriptionUrl = subUrl,
                order = baseOrder + index + 1,
            )
        }
    }

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

    suspend fun updateDelays(results: Map<String, Int>, testedAt: Long = System.currentTimeMillis()) = write { list ->
        list.map { p ->
            val delay = results[p.id]
            if (delay != null) p.copy(lastDelayMs = delay, lastTestedAt = testedAt) else p
        }
    }

    /**
     * Sorts profiles by ping test results (fastest positive delay first, untested/failed at bottom),
     * matching v2rayNG's SortByTestResults.
     */
    suspend fun sortByTestResults() = write { list ->
        list.sortedWith(
            compareBy<Profile> { if (it.lastDelayMs > 0) 0 else 1 }
                .thenBy { if (it.lastDelayMs > 0) it.lastDelayMs else Int.MAX_VALUE }
                .thenBy { it.order },
        ).mapIndexed { idx, p -> p.copy(order = idx) }
    }

    /**
     * Removes duplicate profiles based on [Profile.duplicateKey], matching v2rayNG's DeleteDuplicate.
     * @return Number of duplicates removed.
     */
    suspend fun removeDuplicates(): Int {
        var removedCount = 0
        write { list ->
            val seen = HashSet<String>()
            val unique = ArrayList<Profile>(list.size)
            for (p in list) {
                if (seen.add(p.duplicateKey())) {
                    unique.add(p)
                } else {
                    removedCount++
                }
            }
            unique.mapIndexed { idx, p -> p.copy(order = idx) }
        }
        return removedCount
    }

    /**
     * Removes invalid/timed-out profiles (where lastDelayMs <= 0 after being tested),
     * matching v2rayNG's DeleteInvalid.
     * @return Number of invalid profiles removed.
     */
    suspend fun removeInvalid(): Int {
        var removedCount = 0
        write { list ->
            val kept = list.filter { p ->
                val isFailed = p.lastTestedAt > 0L && p.lastDelayMs <= 0
                if (isFailed) removedCount++
                !isFailed
            }
            kept.mapIndexed { idx, p -> p.copy(order = idx) }
        }
        return removedCount
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
