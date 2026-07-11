package com.khataagent.onboarding

import android.app.LocaleManager as AndroidLocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Persists the shopkeeper's chosen language (SharedPreferences) and applies it app-wide using
 * standard, framework-level Android localization APIs.
 *
 * NOTE on implementation: the spec for this feature called for
 * `androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(LocaleListCompat...)` backed by
 * `androidx.appcompat:appcompat:1.7.0`. This build environment is fully offline with a pre-warmed
 * Gradle cache that does not contain the `androidx.appcompat` artifact (verified: no
 * `androidx.appcompat` directory anywhere under the Gradle module cache, and no local/offline
 * Maven mirror on the machine), so adding that dependency makes `--offline` dependency resolution
 * fail immediately. To keep the build green this class instead does exactly what AppCompatDelegate
 * would do internally, using only framework APIs that ship with the platform:
 *  - API 33+ (Tiramisu): the framework's own per-app [android.app.LocaleManager].
 *  - API 29-32 (this app's minSdk is 29): a manual `Configuration` locale override, applied both
 *    immediately (in-process) and via [wrapContext], which [MainActivity] calls from
 *    `attachBaseContext` on every (re)creation so all screens/resources see the saved locale.
 *
 * If `androidx.appcompat` becomes available in this environment later, this class's public API
 * (`isLanguageChosen`, `setLanguage`, `currentTag`) is a drop-in match for a future
 * AppCompatDelegate-backed implementation — call sites would not need to change.
 */
class LocaleManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** True once the shopkeeper has picked a language on the onboarding screen. */
    fun isLanguageChosen(): Boolean = prefs.contains(KEY_LANGUAGE_TAG)

    /** BCP-47 tag of the currently saved language, or null if none has been chosen yet. */
    fun currentTag(): String? = prefs.getString(KEY_LANGUAGE_TAG, null)

    /** Persists [tag] and applies it immediately to the current process. */
    fun setLanguage(tag: String) {
        prefs.edit().putString(KEY_LANGUAGE_TAG, tag).apply()
        applyLocale(appContext, tag)
    }

    companion object {
        private const val PREFS_NAME = "khata_locale_prefs"
        private const val KEY_LANGUAGE_TAG = "language_tag"

        /**
         * Wraps [base] with a Configuration carrying the previously saved locale. Call this from
         * `Activity.attachBaseContext` (before `onCreate`/`setContent`) so every screen — including
         * the very first frame after a language change + `recreate()` — renders in the right
         * language.
         */
        fun wrapContext(base: Context): Context {
            val tag = base.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE_TAG, null) ?: return base
            val locale = Locale.forLanguageTag(tag)
            Locale.setDefault(locale)
            val config = Configuration(base.resources.configuration)
            config.setLocale(locale)
            config.setLayoutDirection(locale)
            return base.createConfigurationContext(config)
        }

        private fun applyLocale(context: Context, tag: String) {
            val locale = Locale.forLanguageTag(tag)
            Locale.setDefault(locale)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.getSystemService(AndroidLocaleManager::class.java)
                    ?.applicationLocales = LocaleList(locale)
            }

            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            config.setLayoutDirection(locale)
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
        }
    }
}
