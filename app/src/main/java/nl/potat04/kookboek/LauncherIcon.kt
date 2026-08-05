package nl.potat04.kookboek

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import nl.potat04.kookboek.data.PaletteId

/**
 * Keeps the icon on the home screen the colour of the chosen palette.
 *
 * Android decides an app's icon from the manifest, long before any of our code runs, so
 * it cannot be tinted at runtime the way the logo inside the app is. The way around it
 * is one `activity-alias` per palette, each with its own icon, with exactly one enabled
 * — see AndroidManifest.xml.
 *
 * "Exactly one" is the whole risk: with none enabled the app vanishes from the launcher
 * and the only way back is the app list in Settings. So the new alias is switched on
 * *before* the others are switched off, and never the other way round.
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

fun Context.applyLauncherIcon(palette: PaletteId) {
    val manager = packageManager
    val wanted = ComponentName(packageName, palette.launcherAlias)

    runCatching {
        // On first, off after.
        manager.enable(wanted, true)
        PaletteId.entries
            .filter { it != palette }
            .forEach { manager.enable(ComponentName(packageName, it.launcherAlias), false) }
    }.onFailure {
        // A launcher that refuses the change is a cosmetic loss, not a reason to take
        // the app down with it.
        Log.w(TAG, "could not switch the launcher icon to $palette", it)
    }
}

/**
 * Only writes when the state actually differs. Every call makes the launcher redraw, and
 * on some of them the icon blinks — so doing it on every app start would be noticeable.
 */
private fun PackageManager.enable(component: ComponentName, enabled: Boolean) {
    val wanted = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    if (getComponentEnabledSetting(component) == wanted) return
    setComponentEnabledSetting(component, wanted, PackageManager.DONT_KILL_APP)
}

private const val TAG = "LauncherIcon"
