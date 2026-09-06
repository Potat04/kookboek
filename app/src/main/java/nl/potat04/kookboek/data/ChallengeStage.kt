package nl.potat04.kookboek.data

import android.webkit.WebView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Where the bot check gets to happen in the open.
 *
 * Most checks pass on their own in a second or two, with the WebView off screen and
 * nobody watching, so [visible] starts false. Some ask for a tap instead, and one that
 * nobody can reach is one nobody can answer. The screen in front is the only thing that
 * can show it, and it cannot know when one is needed. It watches this instead.
 *
 * [PageFetcher] owns the WebView and puts it here; the screen only draws it.
 */
object ChallengeStage {

    data class Challenge(val web: WebView, val visible: Boolean)

    private val _current = MutableStateFlow<Challenge?>(null)
    val current: StateFlow<Challenge?> = _current.asStateFlow()

    fun show(web: WebView) {
        _current.value = Challenge(web, visible = false)
    }

    /** Safe to call from anywhere, including the JavaScript bridge thread. */
    fun reveal(web: WebView) {
        _current.update { if (it?.web === web) it.copy(visible = true) else it }
    }

    /** Takes [web] off the stage, if it is still the one on it. */
    fun clear(web: WebView) {
        if (_current.value?.web === web) _current.value = null
    }
}
