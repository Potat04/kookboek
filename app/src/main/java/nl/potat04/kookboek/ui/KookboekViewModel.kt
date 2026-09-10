package nl.potat04.kookboek.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.potat04.kookboek.KookboekApp
import nl.potat04.kookboek.R
import nl.potat04.kookboek.data.Backup
import nl.potat04.kookboek.data.BackupError
import nl.potat04.kookboek.data.ImportResult
import nl.potat04.kookboek.data.Recipe
import nl.potat04.kookboek.data.RecipeRepository

enum class SortOrder(@param:StringRes val labelRes: Int) {
    NEWEST(R.string.sort_newest),
    TITLE(R.string.sort_title),
    QUICKEST(R.string.sort_quickest),
}

/**
 * A one-shot message plus an optional undo, shown in the snackbar.
 *
 * The text is a [UiText] and not a `String`: the screen resolves it, so a snackbar
 * always speaks the language that is set at the moment it appears.
 */
data class Toast(
    val message: UiText,
    val undo: (() -> Unit)? = null,
    val actionLabel: UiText? = null,
)

class KookboekViewModel(private val repo: RecipeRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _favouritesOnly = MutableStateFlow(false)
    val favouritesOnly: StateFlow<Boolean> = _favouritesOnly.asStateFlow()

    private val _sort = MutableStateFlow(SortOrder.NEWEST)
    val sort: StateFlow<SortOrder> = _sort.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /**
     * The recipes picked out for a job on several at once, by id.
     *
     * Ids rather than recipes, because the list underneath keeps moving: a refresh
     * replaces a recipe wholesale, and holding the old copy would act on something that
     * is no longer there. Empty means the library is in its normal state; anything in it
     * means the screen is in selection mode.
     */
    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    private val toasts = Channel<Toast>(Channel.BUFFERED)
    val messages = toasts.receiveAsFlow()

    val all: StateFlow<List<Recipe>> = repo.recipes

    /** Deleted and still recoverable, newest first. Emptied thirty days after the delete. */
    val deleted: StateFlow<List<Recipe>> = repo.deleted

    /** True once the cookbook has been read off disk. */
    val loaded: StateFlow<Boolean> = repo.loaded

    /** Stateless, so it is built here rather than threaded through the constructor. */
    private val backup = Backup(repo)

    val visible: StateFlow<List<Recipe>> =
        combine(repo.recipes, _query, _favouritesOnly, _sort) { recipes, query, favourites, sort ->
            val words = query.trim().lowercase().split(" ").filter { it.isNotBlank() }
            recipes
                .filter { !favourites || it.favorite }
                .filter { recipe ->
                    if (words.isEmpty()) true
                    else recipe.searchBlob().let { blob -> words.all(blob::contains) }
                }
                .let { list ->
                    when (sort) {
                        SortOrder.NEWEST -> list.sortedByDescending { it.addedAt }
                        SortOrder.TITLE -> list.sortedBy { it.title.lowercase() }
                        SortOrder.QUICKEST -> list.sortedBy { it.totalMinutes ?: Int.MAX_VALUE }
                    }
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val images get() = repo.images

    fun byId(id: String?): Recipe? = repo.byId(id)

    fun setQuery(value: String) { _query.value = value }
    fun toggleFavouritesFilter() { _favouritesOnly.value = !_favouritesOnly.value }
    fun setSort(order: SortOrder) { _sort.value = order }

    fun toggleFavourite(recipe: Recipe) = viewModelScope.launch {
        repo.update(recipe.id) { it.copy(favorite = !it.favorite) }
    }

    fun toggleIngredient(recipe: Recipe, index: Int) = viewModelScope.launch {
        repo.update(recipe.id) {
            it.copy(checkedIngredients = it.checkedIngredients.toggled(index))
        }
    }

    fun toggleStep(recipe: Recipe, index: Int) = viewModelScope.launch {
        repo.update(recipe.id) { it.copy(checkedSteps = it.checkedSteps.toggled(index)) }
    }

    fun clearChecks(recipe: Recipe) = viewModelScope.launch {
        repo.update(recipe.id) { it.copy(checkedIngredients = emptySet(), checkedSteps = emptySet()) }
    }

    fun setNotes(recipe: Recipe, notes: String) = viewModelScope.launch {
        repo.update(recipe.id) { it.copy(notes = notes) }
    }

    fun save(recipe: Recipe) = viewModelScope.launch { repo.save(recipe) }

    fun toggleSelected(id: String) {
        _selection.update { if (id in it) it - id else it + id }
    }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    /** The picked recipes as they stand now, in the order the library shows them. */
    private fun selected(): List<Recipe> =
        visible.value.filter { it.id in _selection.value }

    /** Deletes everything picked, with one undo that brings all of it back. */
    fun deleteSelected() = viewModelScope.launch {
        val chosen = selected()
        if (chosen.isEmpty()) return@launch
        clearSelection()
        chosen.forEach { repo.delete(it) }
        toasts.send(
            Toast(
                message = if (chosen.size == 1) {
                    UiText.res(R.string.toast_deleted, chosen.first().titleText())
                } else {
                    UiText.Quantity(R.plurals.toast_deleted_many, chosen.size)
                },
                actionLabel = UiText.Res(R.string.action_undo),
                // Clearing deletedAt rather than writing the copies back: the delete
                // is soft now, so the rows never left, and one update per recipe
                // cannot lose anything a rewrite might.
                undo = { viewModelScope.launch { chosen.forEach { repo.restoreDeleted(it.id) } } },
            )
        )
    }

    /**
     * Re-reads every picked recipe that came from a page, one after another.
     *
     * One at a time on purpose. A page behind a bot check needs the WebView, there is
     * only one [nl.potat04.kookboek.data.ChallengeStage] to put it on, and a check that
     * wants a tap has to be answerable. Several at once would race for that one spot.
     */
    fun refreshSelected() = viewModelScope.launch {
        val chosen = selected().filter { it.sourceUrl != null }
        if (chosen.isEmpty()) {
            toasts.send(Toast(UiText.Res(R.string.toast_refresh_no_source)))
            return@launch
        }
        _busy.value = true
        var failed = 0
        for (recipe in chosen) {
            if (repo.refresh(recipe) is ImportResult.Failed) failed++
        }
        _busy.value = false
        clearSelection()
        val done = chosen.size - failed
        toasts.send(
            Toast(
                if (failed == 0) UiText.Quantity(R.plurals.toast_refreshed_many, done)
                else UiText.res(R.string.toast_refreshed_partial, done, failed)
            )
        )
    }

    fun delete(recipe: Recipe) = viewModelScope.launch {
        repo.delete(recipe)
        toasts.send(
            Toast(
                message = UiText.res(R.string.toast_deleted, recipe.titleText()),
                actionLabel = UiText.Res(R.string.action_undo),
                // See deleteSelected: the soft delete left everything in place, so undo
                // is clearing the stamp and not writing the recipe back over itself.
                undo = { viewModelScope.launch { repo.restoreDeleted(recipe.id) } },
            )
        )
    }

    /** From the "recently deleted" screen: back into the cookbook, where it was. */
    fun restoreDeleted(id: String) = viewModelScope.launch { repo.restoreDeleted(id) }

    /** The one delete with no way back, so the screen asks first. */
    fun deleteForever(id: String) = viewModelScope.launch { repo.deleteForever(id) }

    /**
     * Writes the whole cookbook to wherever the file picker pointed.
     *
     * The [ContentResolver] is a parameter and not a field: the ViewModel outlives the
     * screen and has no business holding on to a Context.
     */
    fun exportTo(resolver: ContentResolver, uri: Uri) = viewModelScope.launch {
        val count = runCatching {
            val stream = resolver.openOutputStream(uri) ?: error("no output stream")
            backup.write(stream)
        }.getOrElse {
            toasts.send(Toast(UiText.Res(R.string.backup_toast_export_failed)))
            return@launch
        }
        toasts.send(Toast(UiText.Quantity(R.plurals.backup_toast_exported, count)))
    }

    /** Reads a backup back in and says what it changed. */
    fun restoreFrom(resolver: ContentResolver, uri: Uri) = viewModelScope.launch {
        val opened = runCatching { resolver.openInputStream(uri) ?: error("no input stream") }
            .getOrElse {
                toasts.send(Toast(UiText.Res(R.string.backup_toast_restore_failed)))
                return@launch
            }
        val result = opened.use { backup.read(it) }
        toasts.send(
            Toast(
                result.fold(
                    onSuccess = {
                        UiText.res(
                            R.string.backup_toast_restored,
                            it.added,
                            it.updated,
                            it.skipped,
                        )
                    },
                    onFailure = { error ->
                        UiText.Res(
                            if (error is BackupError.NewerVersion) R.string.backup_toast_restore_newer
                            else R.string.backup_toast_restore_failed
                        )
                    },
                )
            )
        )
    }

    /** Used by the "paste a link" action inside the app. */
    fun importUrl(url: String, onDone: (String?) -> Unit = {}) = viewModelScope.launch {
        _busy.value = true
        val result = repo.import(url)
        _busy.value = false
        when (result) {
            is ImportResult.Saved -> {
                toasts.send(Toast(describe(result.recipe)))
                onDone(result.recipe.id)
            }
            is ImportResult.AlreadySaved -> {
                toasts.send(Toast(UiText.Res(R.string.toast_already_saved)))
                onDone(result.recipe.id)
            }
            is ImportResult.Failed -> {
                toasts.send(Toast(result.reason.text()))
                onDone(null)
            }
        }
    }

    fun refresh(recipe: Recipe) = viewModelScope.launch {
        _busy.value = true
        val result = repo.refresh(recipe)
        _busy.value = false
        toasts.send(
            when (result) {
                is ImportResult.Saved ->
                    Toast(UiText.res(R.string.toast_refreshed, describe(result.recipe)))
                is ImportResult.AlreadySaved -> Toast(UiText.Res(R.string.toast_nothing_changed))
                is ImportResult.Failed -> Toast(result.reason.text())
            }
        )
    }

    /** "12 ingrediënten, 6 stappen" — counted here, worded by the screen. */
    private fun describe(recipe: Recipe): UiText = when {
        recipe.ingredients.isNotEmpty() && recipe.steps.isNotEmpty() -> UiText.Joined(
            listOf(
                UiText.Quantity(R.plurals.count_ingredients, recipe.ingredients.size),
                UiText.Quantity(R.plurals.count_steps, recipe.steps.size),
            )
        )
        recipe.hasContent -> UiText.Res(R.string.toast_partial)
        else -> UiText.Res(R.string.share_summary_link_only)
    }

    /** Pages that gave no title at all still have to be named in a snackbar. */
    private fun Recipe.titleText(): UiText =
        if (title.isBlank()) UiText.Res(R.string.recipe_untitled) else UiText.Raw(title)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as KookboekApp
                KookboekViewModel(app.repository)
            }
        }
    }
}

private fun Set<Int>.toggled(value: Int): Set<Int> =
    if (contains(value)) this - value else this + value
