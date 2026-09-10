package nl.potat04.kookboek.ui

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Base64
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import nl.potat04.kookboek.R
import nl.potat04.kookboek.data.ImageStore
import nl.potat04.kookboek.data.Recipe
import nl.potat04.kookboek.parse.RecipeParser

/**
 * Printing, which on Android is also "save as PDF": the system print dialog offers
 * both, so one path covers a sheet for the kitchen wall and a file to keep.
 *
 * The page is built as HTML and printed through an off-screen WebView, because that is
 * the only printer Android hands an app without a layout engine of its own. It is ink
 * on white paper and not the reader's palette — a printed page has no dark mode, and a
 * cream background would use a cartridge to say nothing.
 */

/**
 * The WebView that is printing right now.
 *
 * Nothing else in the app holds it: it is never in a view tree. Dropping it the moment
 * [printRecipe] returns would collect it halfway through the job and print a blank
 * page, so it stays here until the print dialog has taken over.
 */
private var printing: WebView? = null

fun Context.printRecipe(recipe: Recipe, images: ImageStore?) {
    val printer = getSystemService(Context.PRINT_SERVICE) as? PrintManager
    if (printer == null) {
        Toast.makeText(this, R.string.share_print_unavailable, Toast.LENGTH_SHORT).show()
        return
    }
    val jobName = recipe.title.ifBlank { getString(R.string.recipe_untitled) }
    val html = recipe.toPrintableHtml(this, images)

    val web = runCatching { WebView(this) }.getOrElse {
        // A phone whose WebView is disabled or mid-update.
        Log.w(TAG, "no WebView to print with", it)
        Toast.makeText(this, R.string.share_print_unavailable, Toast.LENGTH_SHORT).show()
        return
    }
    web.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String) {
            printer.print(
                jobName,
                view.createPrintDocumentAdapter(jobName),
                PrintAttributes.Builder().build(),
            )
            printing = null
        }
    }
    printing = web
    web.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
}

/** One page of paper: serif throughout, a rule under the title, ingredients then method. */
private fun Recipe.toPrintableHtml(context: Context, images: ImageStore?): String {
    val picture = imageFile
        ?.let { images?.file(it) }
        ?.takeIf { it.exists() }
        ?.let {
            runCatching {
                "data:image/jpeg;base64," + Base64.encodeToString(it.readBytes(), Base64.NO_WRAP)
            }.getOrNull()
        }

    val heading = listOfNotNull(
        RecipeParser.descriptiveYield(servingsLabel)
            ?: servings?.takeIf { it > 0 }?.let {
                context.resources.getQuantityString(R.plurals.recipe_servings_count, it, it)
            },
        totalMinutes?.takeIf { it > 0 }?.let { minutes ->
            // The same three cases ui/Labels.kt words on screen, in a plain function:
            // paper is built outside a composition.
            when {
                minutes < 60 -> context.getString(R.string.recipe_time_minutes, minutes)
                minutes % 60 == 0 -> context.getString(R.string.recipe_time_hours, minutes / 60)
                else -> context.getString(
                    R.string.recipe_time_hours_minutes, minutes / 60, minutes % 60
                )
            }
        },
        listOfNotNull(author, siteName).distinctBy { it.lowercase() }.joinToString(" · ")
            .takeIf { it.isNotBlank() },
    ).joinToString(" · ")

    return buildString {
        append("<!doctype html><html><head><meta charset=\"utf-8\">")
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        append("<style>")
        append(CSS)
        append("</style></head><body>")
        append("<h1>${esc(title.ifBlank { context.getString(R.string.recipe_untitled) })}</h1>")
        if (heading.isNotBlank()) append("<p class=\"meta\">${esc(heading)}</p>")
        picture?.let { append("<img class=\"photo\" src=\"$it\">") }
        if (!description.isNullOrBlank()) append("<p class=\"blurb\">${esc(description)}</p>")

        append("<div class=\"columns\">")
        if (ingredients.isNotEmpty()) {
            append("<section class=\"ingredients\">")
            append("<h2>${esc(context.getString(R.string.recipe_ingredients))}</h2><ul>")
            var section: String? = null
            ingredients.forEach { line ->
                if (line.section != null && line.section != section) {
                    append("</ul><h3>${esc(line.section)}</h3><ul>")
                }
                section = line.section
                append("<li>${esc(line.text)}</li>")
            }
            append("</ul></section>")
        }
        if (steps.isNotEmpty()) {
            append("<section class=\"method\">")
            append("<h2>${esc(context.getString(R.string.recipe_method))}</h2><ol>")
            var section: String? = null
            steps.forEach { step ->
                if (step.section != null && step.section != section) {
                    append("</ol><h3>${esc(step.section)}</h3><ol>")
                }
                section = step.section
                append("<li>${esc(step.text)}</li>")
            }
            append("</ol></section>")
        }
        append("</div>")

        sourceUrl?.let { append("<p class=\"source\">${esc(it)}</p>") }
        append("</body></html>")
    }
}

/**
 * The recipe is somebody else's text and goes straight into a document, so every last
 * bit of it is escaped rather than trusted to be tame.
 */
private fun esc(text: String): String = text
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")

private const val TAG = "PrintRecipe"

private val CSS = """
    body { font-family: Georgia, 'Times New Roman', serif; color: #000; background: #fff;
           margin: 0; padding: 24px; line-height: 1.45; font-size: 12pt; }
    h1 { font-size: 24pt; margin: 0 0 4px; border-bottom: 1px solid #000; padding-bottom: 8px; }
    h2 { font-size: 14pt; margin: 20px 0 6px; }
    h3 { font-size: 12pt; margin: 12px 0 4px; font-style: italic; font-weight: normal; }
    p.meta { margin: 6px 0 0; font-size: 10pt; }
    p.blurb { font-style: italic; margin: 12px 0 0; }
    p.source { margin-top: 24px; font-size: 9pt; word-break: break-all; }
    img.photo { display: block; margin: 16px 0 0; max-width: 60%; }
    ul, ol { margin: 0; padding-left: 20px; }
    li { margin-bottom: 4px; }
    /* Wide paper gets the shopping list beside the method, the way a cookbook sets it. */
    @media (min-width: 700px) {
      .columns { display: flex; gap: 32px; align-items: flex-start; }
      .ingredients { flex: 0 0 34%; }
      .method { flex: 1; }
    }
""".trimIndent()
