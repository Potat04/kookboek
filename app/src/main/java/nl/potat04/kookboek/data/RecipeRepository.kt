package nl.potat04.kookboek.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import nl.potat04.kookboek.parse.ParsedRecipe
import nl.potat04.kookboek.parse.RecipeParser
import java.net.URI
import java.util.Locale

/**
 * Why an import came back empty-handed. A reason and not a sentence: this layer has
 * no business knowing which language the reader picked, and a recipe imported today
 * may well be looked at in the other one tomorrow.
 */
enum class FailureReason {
    /** What was shared is not a link at all. */
    NO_VALID_LINK,
    /** The phone is not on a network. */
    OFFLINE,
    /** The site answered, but not with the page: a 404, a dead host, a socket that gave up. */
    FETCH_FAILED,
    /** A bot check stood in the way and would not step aside. */
    BLOCKED,
    /** The check was still running when the clock ran out. */
    TIMED_OUT,
    /** The page loaded and holds no recipe: no markup, no lists, nothing to read. */
    NO_RECIPE_ON_PAGE,
    /** Asked to re-read a recipe that was never fetched from anywhere. */
    NO_SOURCE_URL,
    /** Opened without a link, so there is nothing to do. */
    NOTHING_SHARED,
}

sealed interface ImportResult {
    /**
     * [note] is set when the recipe was saved but something is worth saying about it —
     * a page with no recipe on it still becomes a bookmark, and the reader should hear
     * why that is all there is.
     */
    data class Saved(val recipe: Recipe, val note: FailureReason? = null) : ImportResult
    data class AlreadySaved(val recipe: Recipe) : ImportResult
    data class Failed(val reason: FailureReason, val url: String?) : ImportResult
}

class RecipeRepository(
    private val store: RecipeStore,
    val images: ImageStore,
    private val pages: PageFetcher,
) {
    val recipes: StateFlow<List<Recipe>> = store.recipes
    val deleted: StateFlow<List<Recipe>> = store.deleted
    val labels: StateFlow<List<Label>> = store.labels
    val loaded: StateFlow<Boolean> = store.loaded

    suspend fun load() {
        store.load()
        // Deleting a recipe leaves its picture behind so undo can restore it whole, and
        // a soft-deleted recipe still owns its files. Anything unreferenced by either
        // list at the next launch was really meant to go.
        val inUse = (store.recipes.value + store.deleted.value)
            .flatMap { listOfNotNull(it.imageFile, it.attachmentFile) }
            .toSet()
        images.pruneOrphans(inUse)
    }

    fun byId(id: String?): Recipe? = store.byId(id)

    fun deletedById(id: String?): Recipe? = store.deletedById(id)

    suspend fun save(recipe: Recipe) = store.upsert(recipe)

    suspend fun update(id: String, transform: (Recipe) -> Recipe) = store.update(id, transform)

    /** Soft: the recipe leaves the library but keeps its rows and picture until purged. */
    suspend fun delete(recipe: Recipe) = store.softDelete(recipe.id)

    suspend fun deleteForever(id: String) = store.deleteForever(id)

    /**
     * Restores a recipe after an undo, picture and all. The copy handed back was taken
     * before the delete, so writing it puts the row back exactly as it was, deletedAt
     * included.
     */
    suspend fun restore(recipe: Recipe) = store.upsert(recipe)

    suspend fun restoreDeleted(id: String) = store.restoreDeleted(id)

    suspend fun purgeDeleted(olderThanMillis: Long) = store.purgeDeleted(olderThanMillis)

    suspend fun markOpened(id: String) = store.markOpened(id)

    suspend fun createLabel(name: String): Label = store.createLabel(name)

    suspend fun renameLabel(id: String, name: String) = store.renameLabel(id, name)

    suspend fun deleteLabel(id: String) = store.deleteLabel(id)

    suspend fun setLabels(recipeId: String, labelIds: Set<String>) = store.setLabels(recipeId, labelIds)

    suspend fun moveLabel(id: String, newPosition: Int) = store.moveLabel(id, newPosition)

    /**
     * Fetches [rawUrl], reads the recipe off it and stores it.
     * Returns quickly with whatever it could get — a bare link beats losing the page.
     */
    suspend fun import(rawUrl: String): ImportResult {
        val url = normalizeUrl(rawUrl)
            ?: return ImportResult.Failed(FailureReason.NO_VALID_LINK, null)

        existingFor(url)?.let { return ImportResult.AlreadySaved(it) }

        val parsed = when (val fetched = fetchAndParse(url)) {
            is Fetched.Parsed -> fetched.recipe
            is Fetched.Failed -> return ImportResult.Failed(fetched.reason, url)
        }

        val recipe = parsed.toRecipe(url)
        store.upsert(recipe)

        // The picture arrives a moment later; the recipe is already usable without it.
        parsed.imageUrl?.let { imageUrl ->
            images.download(imageUrl, recipe.id)?.let { fileName ->
                store.update(recipe.id) { it.copy(imageFile = fileName) }
            }
        }
        // The link is kept either way — losing the page is worse than saving a bookmark —
        // but a page we read nothing off is not a recipe, and saying so beats a card
        // that looks half broken for no stated reason.
        val note = if (parsed.quality == ParseQuality.LINK_ONLY) FailureReason.NO_RECIPE_ON_PAGE else null
        return ImportResult.Saved(store.byId(recipe.id) ?: recipe, note)
    }

    /**
     * Re-reads the source page for an existing recipe. Everything the reader owns
     * (notes, favourite, labels, servings cooked for, dates, attached photo) stays;
     * everything the page owns (lines, times, video, yield) is taken fresh.
     */
    suspend fun refresh(recipe: Recipe): ImportResult {
        val url = recipe.sourceUrl
            ?: return ImportResult.Failed(FailureReason.NO_SOURCE_URL, null)
        val parsed = when (val fetched = fetchAndParse(url)) {
            is Fetched.Parsed -> fetched.recipe
            is Fetched.Failed -> return ImportResult.Failed(fetched.reason, url)
        }

        // A re-read that comes back empty would wipe lines the reader may have typed in
        // by hand. Say what happened and change nothing.
        if (parsed.quality == ParseQuality.LINK_ONLY && recipe.hasContent) {
            return ImportResult.Failed(FailureReason.NO_RECIPE_ON_PAGE, url)
        }

        val fresh = parsed.toRecipe(url).copy(
            id = recipe.id,
            notes = recipe.notes,
            favorite = recipe.favorite,
            addedAt = recipe.addedAt,
            imageFile = recipe.imageFile,
            labels = recipe.labels,
            cookedServings = recipe.cookedServings,
            lastCookedAt = recipe.lastCookedAt,
            editedAt = recipe.editedAt,
            attachmentFile = recipe.attachmentFile,
            openedAt = recipe.openedAt,
            deletedAt = recipe.deletedAt,
        )
        store.upsert(fresh)
        if (recipe.imageFile == null) {
            parsed.imageUrl?.let { imageUrl ->
                images.download(imageUrl, recipe.id)?.let { name ->
                    store.update(recipe.id) { it.copy(imageFile = name) }
                }
            }
        }
        return ImportResult.Saved(store.byId(recipe.id) ?: fresh)
    }

    /** A page that was read, or the reason it was not. */
    private sealed interface Fetched {
        data class Parsed(val recipe: ParsedRecipe) : Fetched
        data class Failed(val reason: FailureReason) : Fetched
    }

    private suspend fun fetchAndParse(url: String): Fetched =
        when (val page = pages.fetch(url, acceptLanguage())) {
            is FetchResult.Page -> withContext(Dispatchers.Default) {
                Fetched.Parsed(RecipeParser.parse(page.html, url))
            }
            FetchResult.Blocked -> {
                Log.w(TAG, "$url is behind a bot check we could not get past")
                Fetched.Failed(FailureReason.BLOCKED)
            }
            FetchResult.TimedOut -> {
                Log.w(TAG, "the check on $url was still running when time ran out")
                Fetched.Failed(FailureReason.TIMED_OUT)
            }
            FetchResult.Offline -> Fetched.Failed(FailureReason.OFFLINE)
            FetchResult.Unreachable -> Fetched.Failed(FailureReason.FETCH_FAILED)
        }

    private fun existingFor(url: String): Recipe? {
        val key = comparableUrl(url)
        return recipes.value.firstOrNull { it.sourceUrl?.let(::comparableUrl) == key }
    }

    private companion object {
        const val TAG = "RecipeRepository"

        /**
         * Sites that publish in more than one language should answer in the one the
         * reader picked. This is the only place the language choice reaches the
         * *content* of a recipe rather than the app around it.
         *
         * The app locale set through LocaleManager is what `Locale.getDefault()`
         * reports, so no plumbing is needed to find out what it is.
         */
        fun acceptLanguage(): String {
            val chosen = Locale.getDefault().language.takeIf { it.isNotBlank() } ?: "nl"
            val fallback = if (chosen == "nl") "en" else "nl"
            return "$chosen,$fallback;q=0.8"
        }

        /** Ignores tracking noise so re-sharing the same page is recognised as the same page. */
        fun comparableUrl(url: String): String = runCatching {
            val u = URI(url)
            val host = u.host?.removePrefix("www.")?.lowercase().orEmpty()
            host + u.path.orEmpty().trimEnd('/').lowercase()
        }.getOrDefault(url)
    }
}

/** Shared text is rarely just a URL — it is usually "Look at this! https://…". */
fun extractUrl(text: String?): String? {
    if (text.isNullOrBlank()) return null
    val match = Regex("https?://\\S+").find(text) ?: return null
    return match.value.trimEnd('.', ',', ')', ']', '"', '\'', '>')
}

fun normalizeUrl(raw: String?): String? {
    val candidate = extractUrl(raw) ?: raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val withScheme = if (candidate.startsWith("http")) candidate else "https://$candidate"
    return runCatching {
        val u = URI(withScheme)
        if (u.host.isNullOrBlank()) null else withScheme
    }.getOrNull()
}

fun ParsedRecipe.toRecipe(url: String): Recipe = Recipe(
    // A blank title stays blank: the screen fills in "Naamloos recept" in whichever
    // language is set, rather than freezing a Dutch word into the database.
    title = title.trim(),
    sourceUrl = url,
    siteName = siteName ?: RecipeParser.host(url),
    author = author,
    description = description,
    imageUrl = imageUrl,
    ingredients = ingredients,
    steps = steps,
    prepMinutes = prepMinutes,
    cookMinutes = cookMinutes,
    totalMinutes = totalMinutes,
    videoUrl = videoUrl,
    servings = servings,
    servingsLabel = servingsLabel,
    tags = tags,
    quality = quality,
)
