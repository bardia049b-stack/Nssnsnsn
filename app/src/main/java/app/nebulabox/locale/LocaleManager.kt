package app.nebulabox.locale

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import app.nebulabox.Application
import java.util.Locale

object LocaleManager {

    private const val KEY_LANGUAGE = "language"

    const val SYSTEM = "system"
    const val ENGLISH = "en"
    const val PERSIAN = "fa"

    fun isSupported(code: String): Boolean = code == SYSTEM || code == ENGLISH || code == PERSIAN

    fun storedLanguage(): String {
        val raw = Application.instance.prefs.getString(KEY_LANGUAGE, null)
        return if (raw != null && isSupported(raw)) raw else SYSTEM
    }

    fun storeLanguage(context: Context, code: String) {
        val clean = if (isSupported(code)) code else SYSTEM
        Application.instance.prefs.edit().putString(KEY_LANGUAGE, clean).apply()
        applyLocale(clean)
    }

    fun applyStoredLocale(context: Context) {
        applyLocale(storedLanguage())
    }

    fun applyLocale(code: String) {
        val locales = if (code == SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(code)
        }
        AppCompatDelegate.setApplicationLocales(locales)
        if (code == SYSTEM) {
            Locale.setDefault(Locale.getDefault())
        } else {
            Locale.setDefault(Locale(code))
        }
    }

    fun resolvedCode(context: Context): String {
        val stored = storedLanguage()
        if (stored != SYSTEM) return stored
        val fromConfig = context.resources.configuration.locales?.get(0)?.language
        return fromConfig ?: Locale.getDefault().language
    }

    fun isPersian(context: Context): Boolean {
        val code = resolvedCode(context)
        return code == "fa" || code == "prs" || code == "pes"
    }

    @Suppress("DEPRECATION")
    fun wrap(context: Context): Context {
        val code = storedLanguage()
        if (code == SYSTEM) return context

        val locale = Locale(code)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }

    fun restart(context: Context) {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) {
                current.recreate()
                return
            }
            current = current.baseContext
        }
    }
}
