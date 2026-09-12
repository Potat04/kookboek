package nl.potat04.kookboek.ui

import android.app.Activity
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Keeps the screen awake for as long as this composable is on screen. Your hands are
 * in the dough on the recipe screen and at the hob alike, so both ask for it.
 *
 * Counted rather than set and cleared per screen. During a navigation transition both
 * screens are composed at once and the one walking away disposes last, so a plain
 * `clearFlags` there would let the screen you just walked into time out — in cook mode
 * on the way in, and on the recipe screen on the way back.
 */
@Composable
internal fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        window?.let(ScreenAwake::hold)
        onDispose { window?.let(ScreenAwake::release) }
    }
}

/**
 * How many composables want a window kept awake.
 *
 * Only ever touched from composition, which is the main thread, so a plain map does.
 * The entry goes at zero, so no window outlives the activity it belongs to.
 */
private object ScreenAwake {
    private val holders = mutableMapOf<Window, Int>()

    fun hold(window: Window) {
        val count = (holders[window] ?: 0) + 1
        holders[window] = count
        if (count == 1) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun release(window: Window) {
        val count = (holders[window] ?: 0) - 1
        if (count > 0) {
            holders[window] = count
        } else {
            holders.remove(window)
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
