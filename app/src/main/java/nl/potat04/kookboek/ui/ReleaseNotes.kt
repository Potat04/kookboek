package nl.potat04.kookboek.ui

import android.graphics.Typeface
import android.text.util.Linkify
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TableAwareMovementMethod
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.linkify.LinkifyPlugin

/** GitHub release Markdown rendered as native text inside the dialog's scroll area. */
@Composable
internal fun ReleaseNotes(markdown: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.bodyMedium
    val size = with(LocalDensity.current) { style.fontSize.toPx() }
    val typeface = LocalFontFamilyResolver.current.resolve(
        style.fontFamily,
        style.fontWeight ?: FontWeight.Normal,
        style.fontStyle ?: FontStyle.Normal,
    ).value as Typeface
    val renderer = remember(context, colors) {
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(
                colors.primary.toArgb(), colors.outline.toArgb(), colors.onPrimary.toArgb(),
            ))
            .usePlugin(LinkifyPlugin.create(Linkify.WEB_URLS))
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder.linkColor(colors.primary.toArgb())
                        .codeTextColor(colors.onSurface.toArgb())
                        .codeBackgroundColor(colors.surfaceContainerHighest.toArgb())
                        .blockQuoteColor(colors.outline.toArgb())
                }
            })
            .build()
    }
    // Download progress recomposes the dialog; parse the notes only when they change.
    val rendered = remember(renderer, markdown) { renderer.toMarkdown(markdown) }
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { TextView(it).apply { setTextIsSelectable(true) } },
        update = { view ->
            view.setTextColor(colors.onSurfaceVariant.toArgb())
            view.setLinkTextColor(colors.primary.toArgb())
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
            view.typeface = typeface
            renderer.setParsedMarkdown(view, rendered)
            view.movementMethod = TableAwareMovementMethod.create()
        },
    )
}
