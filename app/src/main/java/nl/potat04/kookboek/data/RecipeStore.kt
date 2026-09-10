package nl.potat04.kookboek.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.potat04.kookboek.data.db.KookboekDatabase
import nl.potat04.kookboek.data.db.Migrations
import nl.potat04.kookboek.data.db.toDomain
import nl.potat04.kookboek.data.db.toEntity

/**
 * The cookbook, in SQLite through Room.
 *
 * Recipes, ingredients and steps each have their own table, so writing a note or
 * ticking one line touches one row instead of rewriting the entire collection.
 */
class RecipeStore(context: Context, private val scope: CoroutineScope) {

    private val database = Room
        .databaseBuilder(context.applicationContext, KookboekDatabase::class.java, DB_NAME)
        .addMigrations(*Migrations.ALL)
        .build()

    private val dao = database.recipes()

    private val _recipes = MutableStateFlow<List<Recipe>>(emptyList())
    val recipes: StateFlow<List<Recipe>> = _recipes.asStateFlow()

    /**
     * Recipes the reader deleted and may still want back. They are out of [recipes]
     * and out of search, but their rows and pictures are intact until [purgeDeleted].
     */
    private val _deleted = MutableStateFlow<List<Recipe>>(emptyList())
    val deleted: StateFlow<List<Recipe>> = _deleted.asStateFlow()

    /** The reader's labels in the order they put them, whether or not any recipe wears them. */
    private val _labels = MutableStateFlow<List<Label>>(emptyList())
    val labels: StateFlow<List<Label>> = _labels.asStateFlow()

    /**
     * False until the first read finishes. Without this an empty list is ambiguous —
     * "you have no recipes" and "we have not looked yet" are very different answers.
     */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    suspend fun load() {
        listOf(
            mirror(dao.observeAll()) { rows -> _recipes.value = rows.map { it.toDomain() } },
            mirror(dao.observeDeleted()) { rows -> _deleted.value = rows.map { it.toDomain() } },
            mirror(dao.observeLabels()) { rows -> _labels.value = rows.map { it.toDomain() } },
        ).awaitAll()
        _loaded.value = true
    }

    /** Keeps collecting for the life of the app; completes once the first value has landed. */
    private fun <T> mirror(source: Flow<T>, publish: (T) -> Unit): CompletableDeferred<Unit> {
        val firstRow = CompletableDeferred<Unit>()
        scope.launch {
            source.collect { value ->
                publish(value)
                firstRow.complete(Unit)
            }
        }
        return firstRow
    }

    suspend fun upsert(recipe: Recipe) = dao.upsert(recipe)

    /** The delete the reader sees: it hides the recipe and keeps everything for a change of heart. */
    suspend fun delete(id: String) = softDelete(id)

    suspend fun softDelete(id: String) = dao.softDelete(id, System.currentTimeMillis())

    suspend fun restoreDeleted(id: String) = dao.restoreDeleted(id)

    suspend fun deleteForever(id: String) = dao.deleteForever(id)

    /** Hard-deletes everything that has sat in the deleted list longer than [olderThanMillis]. */
    suspend fun purgeDeleted(olderThanMillis: Long) =
        dao.purgeDeletedBefore(System.currentTimeMillis() - olderThanMillis)

    suspend fun markOpened(id: String) = dao.markOpened(id, System.currentTimeMillis())

    suspend fun update(id: String, transform: (Recipe) -> Recipe) {
        val current = byId(id) ?: return
        dao.upsert(transform(current))
    }

    fun byId(id: String?): Recipe? = _recipes.value.firstOrNull { it.id == id }

    fun deletedById(id: String?): Recipe? = _deleted.value.firstOrNull { it.id == id }

    // ------------------------------------------------------------------ labels

    /** New labels go at the end; the reader can move them afterwards. */
    suspend fun createLabel(name: String): Label {
        val label = Label(
            name = name.trim(),
            position = (_labels.value.maxOfOrNull { it.position } ?: -1) + 1,
        )
        dao.insertLabel(label.toEntity())
        return label
    }

    suspend fun renameLabel(id: String, name: String) = dao.renameLabel(id, name.trim())

    /** The cascade drops the label from every recipe wearing it. */
    suspend fun deleteLabel(id: String) = dao.deleteLabel(id)

    suspend fun setLabels(recipeId: String, labelIds: Set<String>) = dao.setLabels(recipeId, labelIds)

    /**
     * Moves one label to [newPosition] in the list and renumbers the rest, so positions
     * stay a plain 0..n-1 no matter how often things get dragged about.
     */
    suspend fun moveLabel(id: String, newPosition: Int) {
        val current = _labels.value.sortedBy { it.position }
        val moving = current.firstOrNull { it.id == id } ?: return
        val others = current.filter { it.id != id }
        val target = newPosition.coerceIn(0, others.size)
        val reordered = others.take(target) + moving + others.drop(target)
        dao.updateLabels(reordered.mapIndexed { index, label -> label.copy(position = index).toEntity() })
    }

    private companion object {
        const val DB_NAME = "kookboek.db"
    }
}
