package nl.potat04.kookboek.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.potat04.kookboek.R
import nl.potat04.kookboek.data.ImageStore
import nl.potat04.kookboek.data.Recipe
import nl.potat04.kookboek.data.RecipeFile
import nl.potat04.kookboek.data.ShareFiles
import nl.potat04.kookboek.parse.RecipeParser
import java.io.File

/**
 * Sending a recipe out of the app, three ways.
 *
 * As text it goes to whatever the reader already talks to their family in, and it has
 * to read as a recipe there and not as a data dump — so no ticks, no notes, no
 * scaling, and the numbers exactly as the page gave them. As a `.kookboek` file it
 * goes to another Kookboek and keeps everything. The third is the page itself, which
 * only turns up when the parser failed on it.
 *
 * The section headings come out of the resources, so a recipe sent from a phone set to
 * English says "Ingredients" and one sent from a Dutch phone says "Ingrediënten". The
 * recipe underneath is untouched either way; it stays in the language of the site.
 */

/** The recipe as a message: title, source, yield, ingredients, numbered steps. */
fun Recipe.toShareText(context: Context): String = buildString {
    appendLine(title.ifBlank { context.getString(R.string.recipe_untitled) })
    sourceUrl?.let { appendLine(it) }

    yieldText(context)?.let {
        appendLine()
        appendLine(it)
    }

    if (ingredients.isNotEmpty()) {
        appendLine()
        appendLine(context.getString(R.string.recipe_ingredients))
        var section: String? = null
        ingredients.forEach { line ->
            if (line.section != null && line.section != section) {
                appendLine()
                appendLine(line.section)
            }
            section = line.section
            appendLine("- ${line.text}")
        }
    }

    if (steps.isNotEmpty()) {
        appendLine()
        appendLine(context.getString(R.string.recipe_method))
        var section: String? = null
        steps.forEachIndexed { index, step ->
            if (step.section != null && step.section != section) {
                appendLine()
                appendLine(step.section)
            }
            section = step.section
            appendLine("${index + 1}. ${step.text}")
        }
    }
}.trim()

/**
 * The yield the way the recipe screen says it, minus the stepper: the site's own words
 * when it gave any, otherwise the count phrased in the reader's language.
 */
private fun Recipe.yieldText(context: Context): String? =
    RecipeParser.descriptiveYield(servingsLabel)
        ?: servings?.takeIf { it > 0 }?.let {
            context.resources.getQuantityString(R.plurals.recipe_servings_count, it, it)
        }

/** Hands the recipes to any app that takes text. */
fun Context.shareRecipeText(recipes: List<Recipe>) {
    if (recipes.isEmpty()) return
    // A printed rule between recipes, for the same reason a cookbook has one.
    val text = recipes.joinToString("\n\n———\n\n") { it.toShareText(this) }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, recipes.first().title)
    }
    chooseAnApp(send, chooserTitle(recipes.size))
}

/**
 * Writes the recipes to a `.kookboek` file and hands that over.
 *
 * Suspending because it is a zip with pictures in it: the caller decides on which scope
 * that runs, and the chooser only comes up once the file is really there.
 */
suspend fun Context.shareRecipeFile(recipes: List<Recipe>, images: ImageStore) {
    if (recipes.isEmpty()) return
    val file = withContext(Dispatchers.IO) {
        runCatching {
            val outbox = ShareFiles.outbox(this@shareRecipeFile)
            sweep(outbox)
            val target = File(outbox, "${fileName(recipes)}.${RecipeFile.EXTENSION}")
            target.outputStream().use { out ->
                RecipeFile.write(out, recipes) { name ->
                    images.file(name).takeIf { it.exists() }?.readBytes()
                }
            }
            target
        }.onFailure { Log.w(TAG, "could not write the kookboek file", it) }.getOrNull()
    }
    if (file == null) {
        Toast.makeText(this, R.string.share_file_failed, Toast.LENGTH_SHORT).show()
        return
    }
    shareFile(file, RecipeFile.MIME_TYPE, chooserTitle(recipes.size))
}

/**
 * Sends the page that would not parse.
 *
 * This is the one bug report the app can make useful on its own: a saved page is where
 * support for a new site starts, and the reader is the only one who has it.
 */
fun Context.sharePage(recipe: Recipe) {
    val page = ShareFiles.pageFor(this, recipe.id)
    if (!page.exists()) return
    val copy = runCatching {
        val outbox = ShareFiles.outbox(this)
        sweep(outbox)
        page.copyTo(File(outbox, "${safeName(recipe.title)}.html"), overwrite = true)
    }.getOrNull() ?: return
    shareFile(copy, "text/html", getString(R.string.share_help_site))
}

/** True when there is a kept page for this recipe, so the button can stay away otherwise. */
fun Context.hasKeptPage(recipe: Recipe): Boolean = ShareFiles.pageFor(this, recipe.id).exists()

private fun Context.shareFile(file: File, mime: String, title: String) {
    val uri = runCatching {
        FileProvider.getUriForFile(this, ShareFiles.authority(this), file)
    }.getOrNull() ?: run {
        Toast.makeText(this, R.string.share_file_failed, Toast.LENGTH_SHORT).show()
        return
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
        // Without this the app on the other side gets a Uri it is not allowed to open.
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    chooseAnApp(send, title)
}

private fun Context.chooseAnApp(send: Intent, title: String) {
    val chooser = Intent.createChooser(send, title)
    // A chooser started from anything but an activity has no task to live in.
    if (this !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(chooser) }
        .onFailure { Log.w(TAG, "nothing on this phone takes that", it) }
}

private fun Context.chooserTitle(count: Int): String =
    resources.getQuantityString(R.plurals.share_chooser_title, count, count)

private fun Context.fileName(recipes: List<Recipe>): String =
    if (recipes.size == 1) safeName(recipes.single().title)
    else "${getString(R.string.app_name)}-${recipes.size}"

/**
 * A file name other apps and file managers will accept. Letters and digits survive,
 * everything else becomes a space, because a name is what the receiver sees first.
 */
private fun Context.safeName(title: String): String =
    title.map { if (it.isLetterOrDigit() || it == ' ' || it == '-') it else ' ' }
        .joinToString("")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(60)
        .ifBlank { getString(R.string.app_name) }

/**
 * Throws away what the last share left behind. The receiving app has long since copied
 * what it wanted, and the cache is not a place to hoard.
 */
private fun sweep(outbox: File) {
    val cutoff = System.currentTimeMillis() - KEEP_MS
    outbox.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
}

private const val TAG = "Sharing"
private const val KEEP_MS = 60 * 60 * 1000L
