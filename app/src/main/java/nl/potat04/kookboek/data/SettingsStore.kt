package nl.potat04.kookboek.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Remembers the appearance choices and the handful of other things the reader set.
 *
 * SharedPreferences rather than DataStore, and read straight through on the calling
 * thread: it is a few values in one tiny file, and it has to be available *before* the
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
        hapticFeedback = prefs.getBoolean(KEY_HAPTIC, true),
        librarySort = prefs.getString(KEY_LIBRARY_SORT, null),
        favouritesOnly = prefs.getBoolean(KEY_FAVOURITES_ONLY, false),
        holdHintSeen = prefs.getBoolean(KEY_HOLD_HINT_SEEN, false),
        backupFolder = prefs.getString(KEY_BACKUP_FOLDER, null),
        lastBackupAt = prefs.getLong(KEY_LAST_BACKUP_AT, 0),
        autoBackup = prefs.getBoolean(KEY_AUTO_BACKUP, false),
    )

    fun setPalette(palette: PaletteId) = write { it.copy(palette = palette) }

    fun setMode(mode: ThemeMode) = write { it.copy(mode = mode) }

    fun setTextSize(size: TextSize) = write { it.copy(textSize = size) }

    fun setHapticFeedback(enabled: Boolean) = write { it.copy(hapticFeedback = enabled) }

    fun setLibrarySort(name: String?) = write { it.copy(librarySort = name) }

    fun setFavouritesOnly(only: Boolean) = write { it.copy(favouritesOnly = only) }

    fun setHoldHintSeen(seen: Boolean) = write { it.copy(holdHintSeen = seen) }

    fun setBackupFolder(uri: String?) = write { it.copy(backupFolder = uri) }

    fun setLastBackupAt(millis: Long) = write { it.copy(lastBackupAt = millis) }

    fun setAutoBackup(enabled: Boolean) = write { it.copy(autoBackup = enabled) }

    private fun write(transform: (Settings) -> Settings) {
        val next = transform(_settings.value)
        if (next == _settings.value) return
        prefs.edit()
            .putString(KEY_PALETTE, next.palette.stored)
            .putString(KEY_MODE, next.mode.name)
            .putString(KEY_TEXT_SIZE, next.textSize.name)
            .putBoolean(KEY_HAPTIC, next.hapticFeedback)
            .putString(KEY_LIBRARY_SORT, next.librarySort)
            .putBoolean(KEY_FAVOURITES_ONLY, next.favouritesOnly)
            .putBoolean(KEY_HOLD_HINT_SEEN, next.holdHintSeen)
            .putString(KEY_BACKUP_FOLDER, next.backupFolder)
            .putLong(KEY_LAST_BACKUP_AT, next.lastBackupAt)
            .putBoolean(KEY_AUTO_BACKUP, next.autoBackup)
            .apply()
        _settings.value = next
    }

    private companion object {
        // The file keeps its old name: renaming it would reset everyone's palette.
        const val FILE = "appearance"
        const val KEY_PALETTE = "palette"
        const val KEY_MODE = "mode"
        const val KEY_TEXT_SIZE = "text_size"
        const val KEY_HAPTIC = "haptic_feedback"
        const val KEY_LIBRARY_SORT = "library_sort"
        const val KEY_FAVOURITES_ONLY = "favourites_only"
        const val KEY_HOLD_HINT_SEEN = "hold_hint_seen"
        const val KEY_BACKUP_FOLDER = "backup_folder"
        const val KEY_LAST_BACKUP_AT = "last_backup_at"
        const val KEY_AUTO_BACKUP = "auto_backup"
    }
}

/** An unknown stored name means the app changed under a saved preference — fall back. */
private inline fun <reified T : Enum<T>> String?.toEnum(fallback: T): T =
    this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: fallback
