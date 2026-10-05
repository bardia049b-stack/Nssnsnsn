package app.nebulabox.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.nebulabox.util.AppLogger
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
private val PROFILES_RECOVERY_KEY = stringPreferencesKey("profiles_unreadable")

private const val TAG = "ProfileStore"

private val kjson = KJson { ignoreUnknownKeys = true; encodeDefaults = true }
private val kjsonRelaxed = KJson {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
    coerceInputValues = true
}

private val profileListSerializer = ListSerializer(Profile.serializer())
private val subscriptionListSerializer = ListSerializer(SubscriptionItem.serializer())
private val settingsSerializer = AppSettings.serializer()

class ProfileStore(private val context: Context) {

    val profiles: Flow<List<Profile>> = context.profileStore.data.map { prefs ->
        val raw = prefs[PROFILES_KEY] ?: return@map emptyList()
        decodeProfiles(raw).orEmpty().map { p ->
            if (p.lastTestedAt == 0L && p.lastDelayMs < 0) p.copy(lastDelayMs = 0) else p
        }
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

    /**
     * A subscription refresh hands back freshly parsed entries with new ids. The servers that are
     * still in the list keep the id they had, so the profile the tunnel is running, the one the user
     * picked and the delay that was measured for it all survive the refresh.
     */
    suspend fun replaceSubscriptionProfiles(subId: String, subUrl: String, newProfiles: List<Profile>) = write { list ->
        val previous = list.filter { it.subscriptionId == subId }
        val reused = HashSet<String>()
        val kept = list.filterNot { it.subscriptionId == subId }
        val baseOrder = kept.maxOfOrNull { it.order } ?: 0
        val refreshed = newProfiles.mapIndexed { index, profile ->
            val match = previous.firstOrNull { it.id !in reused && it.duplicateKey() == profile.duplicateKey() }
            if (match != null) reused.add(match.id)
            profile.copy(
                id = match?.id ?: profile.id,
                subscriptionId = subId,
                subscriptionUrl = subUrl,
                order = baseOrder + index + 1,
                lastDelayMs = match?.lastDelayMs ?: 0,
                lastTestedAt = match?.lastTestedAt ?: 0L,
            )
        }
        val droppedCount = previous.size - reused.size
        if (refreshed.isNotEmpty()) {
            AppLogger.i(TAG, "Subscription refreshed: ${refreshed.size} servers, ${reused.size} kept their identity, $droppedCount gone")
        }
        kept + refreshed
    }

    suspend fun upsert(profile: Profile) = write { list ->
        val normalized = if (profile.lastTestedAt == 0L && profile.lastDelayMs < 0) {
            profile.copy(lastDelayMs = 0)
        } else {
            profile
        }
        val index = list.indexOfFirst { it.id == normalized.id }
        if (index >= 0) {
            list.toMutableList().also { it[index] = normalized }
        } else {
            (listOf(normalized) + list).mapIndexed { idx, p -> p.copy(order = idx) }
        }
    }

    suspend fun addAll(newProfiles: List<Profile>) = write { list ->
        val prepended = newProfiles.map { profile ->
            profile.copy(
                lastDelayMs = 0,
                lastTestedAt = 0L,
            )
        }
        (prepended + list).mapIndexed { index, profile ->
            profile.copy(order = index)
        }
    }

    suspend fun delete(id: String) = write { list -> list.filterNot { it.id == id } }

    suspend fun deleteAll(ids: Set<String>) = write { list -> list.filterNot { it.id in ids } }

    suspend fun clear() = write { emptyList() }

    suspend fun clearGroup(subId: String) = write { list ->
        list.filterNot { p ->
            if (subId.isBlank()) p.subscriptionId.isBlank() else p.subscriptionId == subId
        }
    }

    suspend fun update(id: String, transform: (Profile) -> Profile) = write { list ->
        list.map { if (it.id == id) transform(it) else it }
    }

    suspend fun clearTestDelays(ids: Set<String>) = write { list ->
        list.map { p ->
            if (p.id in ids) p.copy(lastDelayMs = 0, lastTestedAt = 0L) else p
        }
    }

    suspend fun updateDelays(results: Map<String, Int>, testedAt: Long = System.currentTimeMillis()) = write { list ->
        list.map { p ->
            val delay = results[p.id] ?: return@map p
            p.copy(
                lastDelayMs = delay,
                lastTestedAt = testedAt,
                failureStreak = if (delay < 0) p.failureStreak + 1 else 0,
            )
        }
    }

    suspend fun sortByTestResults() = write { list ->
        list.sortedWith(
            compareBy<Profile> { if (it.lastDelayMs > 0) 0 else 1 }
                .thenBy { if (it.lastDelayMs > 0) it.lastDelayMs else Int.MAX_VALUE }
                .thenBy { it.order },
        ).mapIndexed { idx, p -> p.copy(order = idx) }
    }

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
     * A server is only dropped once it failed twice in a row, so a single bad round on a shaky
     * network cannot throw the list away. When everything fails together it is this phone's problem
     * and nothing is touched.
     */
    suspend fun removeInvalid(group: String? = null): Int {
        var removedCount = 0
        var refused = false
        write { list ->
            val scope = if (group == null) list else list.filter { it.subscriptionId == group }
            val failed = scope.filter { it.lastTestedAt > 0L && it.lastDelayMs < 0 }
            if (failed.isEmpty()) return@write list
            if (failed.size == scope.size) {
                refused = true
                return@write list
            }
            val doomed = failed.filter { it.failureStreak >= 2 }.mapTo(HashSet()) { it.id }
            if (doomed.isEmpty()) {
                AppLogger.i(TAG, "Nothing removed yet, every failing server has only one bad round behind it")
                return@write list
            }
            removedCount = doomed.size
            list.filterNot { it.id in doomed }.mapIndexed { idx, p -> p.copy(order = idx) }
        }
        if (refused) {
            AppLogger.w(TAG, "Every tested server failed, keeping them all so a bad connection cannot delete the list")
            return -1
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

    private fun decodeProfiles(raw: String): List<Profile>? =
        runCatching { kjson.decodeFromString(profileListSerializer, raw) }.getOrNull()
            ?: runCatching { kjsonRelaxed.decodeFromString(profileListSerializer, raw) }.getOrNull()

    private fun keepUnreadableCopy(prefs: MutablePreferences, raw: String) {
        prefs[PROFILES_RECOVERY_KEY] = raw
        AppLogger.w(TAG, "The stored profile list could not be read, a copy of it is kept for recovery")
    }

    private suspend fun write(transform: (List<Profile>) -> List<Profile>) {
        context.profileStore.edit { prefs ->
            val raw = prefs[PROFILES_KEY] ?: "[]"
            val decoded = decodeProfiles(raw)
            if (decoded == null && raw != "[]") keepUnreadableCopy(prefs, raw)
            val current = decoded.orEmpty()
                .map { p -> if (p.lastTestedAt == 0L && p.lastDelayMs < 0) p.copy(lastDelayMs = 0) else p }
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
