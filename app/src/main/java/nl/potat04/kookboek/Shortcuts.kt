package nl.potat04.kookboek

import android.content.Intent

/** What a long press on the app icon can ask for. */
enum class Shortcut { FAVOURITES, ADD_LINK }

/**
 * The two entries behind a long press on the launcher icon, declared in
 * `res/xml/shortcuts.xml`.
 *
 * They arrive as extras on a plain start of [MainActivity] rather than as activities of
 * their own: there is one window, the library is where both of them land, and a second
 * entry point would be a second thing to keep working.
 *
 * The extra is read once and taken off the intent, the way `EXTRA_OPEN_RECIPE` is. A
 * rotation rebuilds the activity from the same intent, and reading it twice would drag
 * the reader back to favourites they had just switched off.
 *
 * The declaration sits on the launcher aliases and not on [LauncherRouter]: the system
 * reads `android.app.shortcuts` off the component that answers MAIN/LAUNCHER, and that
 * is whichever alias is enabled. See AndroidManifest.xml.
 */
object Shortcuts {
    const val EXTRA_FAVOURITES = "shortcut_favourites"
    const val EXTRA_ADD_LINK = "shortcut_add_link"

    fun consume(intent: Intent?): Shortcut? {
        if (intent == null) return null
        val asked = when {
            intent.getBooleanExtra(EXTRA_FAVOURITES, false) -> Shortcut.FAVOURITES
            intent.getBooleanExtra(EXTRA_ADD_LINK, false) -> Shortcut.ADD_LINK
            else -> null
        }
        intent.removeExtra(EXTRA_FAVOURITES)
        intent.removeExtra(EXTRA_ADD_LINK)
        return asked
    }
}
