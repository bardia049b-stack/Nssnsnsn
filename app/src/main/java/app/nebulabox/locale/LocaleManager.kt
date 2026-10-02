package app.nebulabox.locale

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import app.nebulabox.Application
import java.util.Locale

/**
 * Language switching.
 *
 * On API 33+ the per-app language API is used so the system settings screen
 * reflects the choice. Below that the configuration is overridden on the
 * activity, which is why [wrap] is called from the base activity.
 */
object LocaleManager {

    private const val KEY_LANGUAGE = "language"

    const val SYSTEM = "system"
    const val ENGLISH = "en"
    const val PERSIAN = "fa"

    fun storedLanguage(): String =
        Application.instance.prefs.getString(KEY_LANGUAGE, SYSTEM) ?: SYSTEM

    fun storeLanguage(context: Context, code: String) {
        Application.instance.prefs.edit().putString(KEY_LANGUAGE, code).apply()
        applyLocale(context, code)
    }

    fun applyStoredLocale(context: Context) {
        applyLocale(context, storedLanguage())
    }

    private fun applyLocale(context: Context, code: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = if (code == SYSTEM) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(code)
            }
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    /** Wraps a context so resources resolve to the chosen language pre API 33. */
    @Suppress("DEPRECATION")
    fun wrap(context: Context): Context {
        val code = storedLanguage()
        if (code == SYSTEM) return context
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return context

        val locale = Locale(code)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }

    /** True when the effective language is right to left, used by the theme. */
    fun isRtl(context: Context): Boolean {
        val code = storedLanguage()
        return if (code == SYSTEM) {
            val default = Locale.getDefault()
            default.language == "fa" || default.language == "ar" || default.language == "he"
        } else {
            code == "fa"
        }
    }

    fun currentTag(activity: Activity): String {
        val code = storedLanguage()
        if (code != SYSTEM) return code
        return activity.resources.configuration.locales?.get(0)?.language
            ?: Locale.getDefault().language
    }
}
