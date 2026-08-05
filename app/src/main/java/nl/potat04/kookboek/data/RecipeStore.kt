package nl.potat04.kookboek.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.potat04.kookboek.data.db.KookboekDatabase
import nl.potat04.kookboek.data.db.toDomain

/**
 * The cookbook, in SQLite through Room.
 *
 * Recipes, ingredients and steps each have their own table, so writing a note or
 * ticking one line touches one row instead of rewriting the entire collection.
 */
class RecipeStore(context: Context, private val scope: CoroutineScope) {

    private val database = Room
        .databaseBuilder(context.applicationContext, KookboekDatabase::class.java, DB_NAME)
        .build()

    private val dao = database.recipes()

    private val _recipes = MutableStateFlow<List<Recipe>>(emptyList())
    val recipes: StateFlow<List<Recipe>> = _recipes.asStateFlow()

    /**
     * False until the first read finishes. Without this an empty list is ambiguous —
     * "you have no recipes" and "we have not looked yet" are very different answers.
     */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    suspend fun load() {
        val firstRow = CompletableDeferred<Unit>()
        scope.launch {
            dao.observeAll().collect { rows ->
                _recipes.value = rows.map { it.toDomain() }
                firstRow.complete(Unit)
            }
        }
        firstRow.await()
        _loaded.value = true
    }

    suspend fun upsert(recipe: Recipe) = dao.upsert(recipe)

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun update(id: String, transform: (Recipe) -> Recipe) {
        val current = byId(id) ?: return
        dao.upsert(transform(current))
    }

    fun byId(id: String?): Recipe? = _recipes.value.firstOrNull { it.id == id }

    private companion object {
        const val DB_NAME = "kookboek.db"
    }
}
