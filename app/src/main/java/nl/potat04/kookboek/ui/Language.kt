package nl.potat04.kookboek.ui

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.annotation.StringRes
import nl.potat04.kookboek.R

/**
 * The two languages the app is written in, plus "whatever the phone says".
 *
 * Dutch is the untagged default in `res/values/`, English lives in `res/values-en/`.
 */
enum class AppLanguage(val tag: String, @param:StringRes val labelRes: Int) {
    SYSTEM("", R.string.settings_follow_system),
    DUTCH("nl", R.string.settings_language_dutch),
    ENGLISH("en", R.string.settings_language_english),
}

/**
 * Android 13 keeps a per-app locale of its own, and shows it in the phone's own
 * settings next to every other app. Storing a second copy in our preferences would
 * mean two answers to one question the moment the reader changes it over there, so
 * the platform is the only record — we just read and write it.
 *
 * Writing it restarts the activity, which is what makes the new strings appear.
 */
fun Context.appLanguage(): AppLanguage {
    val locales = localeManager?.applicationLocales ?: return AppLanguage.SYSTEM
    if (locales.isEmpty) return AppLanguage.SYSTEM
    val language = locales[0].language
    return AppLanguage.entries
        .firstOrNull { it.tag.isNotEmpty() && it.tag == language }
        ?: AppLanguage.SYSTEM
}

fun Context.setAppLanguage(language: AppLanguage) {
    localeManager?.applicationLocales = when (language) {
        AppLanguage.SYSTEM -> LocaleList.getEmptyLocaleList()
        else -> LocaleList.forLanguageTags(language.tag)
    }
}

private val Context.localeManager: LocaleManager?
    get() = getSystemService(LocaleManager::class.java)
