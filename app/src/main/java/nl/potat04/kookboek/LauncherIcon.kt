package nl.potat04.kookboek

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import nl.potat04.kookboek.data.PaletteId

/**
 * Keeps the icon on the home screen the colour of the chosen palette.
 *
 * Android decides an app's icon from the manifest, long before any of our code runs, so it
 * cannot be tinted at runtime the way the logo inside the app is. The way around it is one
 * `activity-alias` per palette, each with its own icon, with exactly one enabled — see
 * AndroidManifest.xml.
 *
 * Two things make this sharper than it looks.
 *
 * **Exactly one enabled.** With none enabled the app vanishes from the launcher and the only
 * way back is the app list in Settings. So the new alias is switched on *before* the others
 * are switched off, and never the other way round.
 *
 * **Disabling an alias destroys the tasks rooted at it**, and `DONT_KILL_APP` only covers the
 * process. That is why the aliases point at [LauncherRouter] rather than at [MainActivity]:
 * it keeps the reader's own task out of reach. Read that class before changing any of this —
 * without it, choosing a palette throws the current session away.
 */
private val PaletteId.launcherAlias: String
    get() = "nl.potat04.kookboek.Launcher" + when (this) {
        PaletteId.SINAASAPPEL -> "Sinaasappel"
        PaletteId.OLIJF -> "Olijf"
        PaletteId.BOSBES -> "Bosbes"
        PaletteId.RABARBER -> "Rabarber"
        PaletteId.ESPRESSO -> "Espresso"
        PaletteId.INKT -> "Inkt"
    }

/** The one alias the manifest ships enabled. `LauncherIconTest` keeps this true. */
private val PaletteId.enabledInManifest: Boolean get() = this == PaletteId.entries.first()

fun Context.applyLauncherIcon(palette: PaletteId) {
    val manager = packageManager
    runCatching {
        // On first, off after.
        manager.set(component(palette), enabled = true, manifestDefault = palette.enabledInManifest)
        PaletteId.entries
            .filter { it != palette }
            .forEach { manager.set(component(it), enabled = false, manifestDefault = it.enabledInManifest) }
    }.onFailure {
        // A launcher that refuses the change is a cosmetic loss, not a reason to take the
        // app down with it.
        Log.w(TAG, "could not switch the launcher icon to $palette", it)
    }
}

private fun Context.component(palette: PaletteId) =
    ComponentName(packageName, palette.launcherAlias)

/**
 * Writes only when the state actually differs. Every write pokes the launcher into redrawing
 * the icon, so a pointless one is a visible flicker rather than merely wasted work.
 *
 * The subtlety is that a component nobody has touched yet reports
 * `COMPONENT_ENABLED_STATE_DEFAULT`, not enabled or disabled, and "default" means whatever
 * the manifest declared. Comparing against enabled/disabled alone therefore rewrites all six
 * aliases on the first launch after an install, when not one of them needed changing —
 * measured, not guessed.
 */
private fun PackageManager.set(component: ComponentName, enabled: Boolean, manifestDefault: Boolean) {
    val current = when (getComponentEnabledSetting(component)) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
        else -> manifestDefault
    }
    if (current == enabled) return
    setComponentEnabledSetting(
        component,
        if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.DONT_KILL_APP,
    )
}

private const val TAG = "LauncherIcon"
