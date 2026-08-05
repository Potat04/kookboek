package nl.potat04.kookboek.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import nl.potat04.kookboek.parse.ParsedRecipe
import nl.potat04.kookboek.parse.RecipeParser
import org.jsoup.Jsoup
import java.net.URI

/**
 * Why an import came back empty-handed. A reason and not a sentence: this layer has
 * no business knowing which language the reader picked, and a recipe imported today
 * may well be looked at in the other one tomorrow.
 */
enum class FailureReason { NO_VALID_LINK, FETCH_FAILED, NO_SOURCE_URL, NOTHING_SHARED }

sealed interface ImportResult {
    data class Saved(val recipe: Recipe) : ImportResult
    data class AlreadySaved(val recipe: Recipe) : ImportResult
    data class Failed(val reason: FailureReason, val url: String?) : ImportResult
}

class RecipeRepository(
    private val store: RecipeStore,
    val images: ImageStore,
) {
    val recipes: StateFlow<List<Recipe>> = store.recipes
    val loaded: StateFlow<Boolean> = store.loaded

    suspend fun load() {
        store.load()
        // Deleting a recipe leaves its picture behind so undo can restore it whole.
        // Anything still unreferenced by the next launch was really meant to go.
        images.pruneOrphans(store.recipes.value.mapNotNull { it.imageFile }.toSet())
    }

    fun byId(id: String?): Recipe? = store.byId(id)

    suspend fun save(recipe: Recipe) = store.upsert(recipe)

    suspend fun update(id: String, transform: (Recipe) -> Recipe) = store.update(id, transform)

    suspend fun delete(recipe: Recipe) = store.delete(recipe.id)

    /** Restores a recipe after an undo, picture and all. */
    suspend fun restore(recipe: Recipe) = store.upsert(recipe)

    /**
     * Fetches [rawUrl], reads the recipe off it and stores it.
     * Returns quickly with whatever it could get — a bare link beats losing the page.
     */
    suspend fun import(rawUrl: String): ImportResult {
        val url = normalizeUrl(rawUrl)
            ?: return ImportResult.Failed(FailureReason.NO_VALID_LINK, null)

        existingFor(url)?.let { return ImportResult.AlreadySaved(it) }

        val parsed = fetchAndParse(url)
            ?: return ImportResult.Failed(FailureReason.FETCH_FAILED, url)

        val recipe = parsed.toRecipe(url)
        store.upsert(recipe)

        // The picture arrives a moment later; the recipe is already usable without it.
        parsed.imageUrl?.let { imageUrl ->
            images.download(imageUrl, recipe.id)?.let { fileName ->
                store.update(recipe.id) { it.copy(imageFile = fileName) }
            }
        }
        return ImportResult.Saved(store.byId(recipe.id) ?: recipe)
    }

    /** Re-reads the source page for an existing recipe, keeping notes, favourite and checks. */
    suspend fun refresh(recipe: Recipe): ImportResult {
        val url = recipe.sourceUrl
            ?: return ImportResult.Failed(FailureReason.NO_SOURCE_URL, null)
        val parsed = fetchAndParse(url)
            ?: return ImportResult.Failed(FailureReason.FETCH_FAILED, url)

        val fresh = parsed.toRecipe(url).copy(
            id = recipe.id,
            notes = recipe.notes,
            favorite = recipe.favorite,
            addedAt = recipe.addedAt,
            imageFile = recipe.imageFile,
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

    private suspend fun fetchAndParse(url: String): ParsedRecipe? = withContext(Dispatchers.IO) {
        runCatching {
            val doc = Jsoup.connect(url)
                .userAgent(ImageStore.USER_AGENT)
                .header("Accept-Language", "nl,en;q=0.8")
                .followRedirects(true)
                .ignoreHttpErrors(true)
                .timeout(25_000)
                .maxBodySize(6 * 1024 * 1024)
                .get()
            RecipeParser.parse(doc, url)
        }.onFailure { Log.w(TAG, "fetch failed for $url", it) }.getOrNull()
    }

    private fun existingFor(url: String): Recipe? {
        val key = comparableUrl(url)
        return recipes.value.firstOrNull { it.sourceUrl?.let(::comparableUrl) == key }
    }

    private companion object {
        const val TAG = "RecipeRepository"

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
    totalMinutes = totalMinutes,
    servings = servings,
    servingsLabel = servingsLabel,
    tags = tags,
    quality = quality,
)
