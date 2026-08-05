package nl.potat04.kookboek.ui

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * A piece of text that is not yet in a language.
 *
 * The ViewModel and the repository decide *what* to say; only the screen knows which
 * language to say it in. Handing a finished `String` upward would freeze the snackbar
 * into whatever locale was current when the work started — and would put Dutch
 * sentences back into layers that have no business holding them.
 */
sealed interface UiText {
    data class Res(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Quantity(@param:PluralsRes val id: Int, val count: Int) : UiText
    data class Joined(val parts: List<UiText>, val separator: String = ", ") : UiText
    /** Text that comes from the recipe itself — a title, a site name — and is never translated. */
    data class Raw(val text: String) : UiText

    companion object {
        fun res(@StringRes id: Int, vararg args: Any): Res = Res(id, args.toList())
    }
}

fun UiText.resolve(context: Context): String = when (this) {
    is UiText.Res -> context.getString(id, *args.resolveArgs(context))
    is UiText.Quantity -> context.resources.getQuantityString(id, count, count)
    is UiText.Joined -> parts.joinToString(separator) { it.resolve(context) }
    is UiText.Raw -> text
}

@Composable
fun UiText.resolve(): String = resolve(LocalContext.current)

/** Nested [UiText] arguments are resolved first, so "'%s' verwijderd" can take a title. */
private fun List<Any>.resolveArgs(context: Context): Array<Any> =
    map { if (it is UiText) it.resolve(context) else it }.toTypedArray()
