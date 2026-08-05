package nl.potat04.kookboek.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Remembers the appearance choices.
 *
 * SharedPreferences rather than DataStore, and read straight through on the calling
 * thread: it is four values in one tiny file, and it has to be available *before* the
 * first frame. Reading it asynchronously would paint one frame in the wrong palette
 * on every cold start, which is a worse trade than a sub-millisecond read.
 *
 * The language is **not** kept here — see `ui/Language.kt`. Android 13 stores the
 * per-app locale itself, and duplicating it would leave two answers to one question.
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private fun read() = Settings(
        palette = PaletteId.of(prefs.getString(KEY_PALETTE, null)),
        mode = prefs.getString(KEY_MODE, null).toEnum(ThemeMode.SYSTEM),
        textSize = prefs.getString(KEY_TEXT_SIZE, null).toEnum(TextSize.NORMAL),
    )

    fun setPalette(palette: PaletteId) = write { it.copy(palette = palette) }

    fun setMode(mode: ThemeMode) = write { it.copy(mode = mode) }

    fun setTextSize(size: TextSize) = write { it.copy(textSize = size) }

    private fun write(transform: (Settings) -> Settings) {
        val next = transform(_settings.value)
        if (next == _settings.value) return
        prefs.edit()
            .putString(KEY_PALETTE, next.palette.stored)
            .putString(KEY_MODE, next.mode.name)
            .putString(KEY_TEXT_SIZE, next.textSize.name)
            .apply()
        _settings.value = next
    }

    private companion object {
        const val FILE = "appearance"
        const val KEY_PALETTE = "palette"
        const val KEY_MODE = "mode"
        const val KEY_TEXT_SIZE = "text_size"
    }
}

/** An unknown stored name means the app changed under a saved preference — fall back. */
private inline fun <reified T : Enum<T>> String?.toEnum(fallback: T): T =
    this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: fallback
