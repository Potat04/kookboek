package nl.potat04.kookboek.data

import android.content.Context
import java.io.File

/**
 * The scratch space for files that travel between apps.
 *
 * Everything here lives in the cache, on purpose. None of it is the cookbook: the
 * outbox holds a copy that another app is reading right now, the inbox holds pictures
 * out of a file that may never be added, and a kept page can always be fetched again.
 * The system may empty any of it whenever it needs the room.
 *
 * The names live in one place because two sides have to agree on them: the code that
 * writes the file and `res/xml/file_paths.xml`, which decides what the FileProvider is
 * allowed to hand out.
 */
object ShareFiles {

    /** What the FileProvider serves. Anything written here is on its way to another app. */
    fun outbox(context: Context): File = dir(context, "share")

    /** Pictures unpacked from a `.kookboek` file, before the reader has said yes to it. */
    fun inbox(context: Context): File = dir(context, "incoming")

    /**
     * The raw HTML of a page the parser could not make sense of, one file per recipe.
     * A failed import is the best possible test fixture, so it is kept rather than
     * thrown away — see .claude/knowledge/testing.md.
     */
    fun pages(context: Context): File = dir(context, "pages")

    fun pageFor(context: Context, recipeId: String): File = File(pages(context), "$recipeId.html")

    /** The authority in the manifest, spelled the same way from code. */
    fun authority(context: Context): String = "${context.packageName}.files"

    private fun dir(context: Context, name: String): File =
        File(context.cacheDir, name).apply { mkdirs() }
}
