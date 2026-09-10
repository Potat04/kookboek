package nl.potat04.kookboek.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import nl.potat04.kookboek.parse.Durations

/**
 * A step's text with every spoken duration underlined in the primary colour and
 * tappable. Tapping one hands the length in seconds to [onTimer]; wording the timer's
 * label and starting it is the caller's business, see [startTimer].
 */
@Composable
fun DurationText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    onTimer: (seconds: Int) -> Unit,
) {
    val durations = remember(text) { Durations.find(text) }
    if (durations.isEmpty()) {
        Text(text, style = style, modifier = modifier)
        return
    }
    val linkStyle = TextLinkStyles(
        style = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
        ),
    )
    val annotated = buildAnnotatedString {
        var cursor = 0
        durations.forEach { d ->
            append(text.substring(cursor, d.range.first))
            withLink(
                LinkAnnotation.Clickable(
                    tag = "timer:${d.seconds}",
                    styles = linkStyle,
                    linkInteractionListener = { onTimer(d.seconds) },
                ),
            ) {
                append(text.substring(d.range.first, d.range.last + 1))
            }
            cursor = d.range.last + 1
        }
        append(text.substring(cursor))
    }
    Text(annotated, style = style, modifier = modifier)
}

/**
 * Asks the clock app for a timer, without showing its screen. Returns false when no
 * app on the phone takes timers, so the caller can say so instead of doing nothing.
 */
fun Context.startTimer(seconds: Int, label: String): Boolean {
    val intent = Intent(AlarmClock.ACTION_SET_TIMER)
        .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
        .putExtra(AlarmClock.EXTRA_MESSAGE, label)
        .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
    return try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
