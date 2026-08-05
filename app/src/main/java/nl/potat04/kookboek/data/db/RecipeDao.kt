package nl.potat04.kookboek.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import nl.potat04.kookboek.data.Recipe

@Dao
abstract class RecipeDao {

    @Transaction
    @Query("SELECT * FROM recipes ORDER BY addedAt DESC")
    abstract fun observeAll(): Flow<List<RecipeWithParts>>

    @Query("SELECT COUNT(*) FROM recipes")
    abstract suspend fun count(): Int

    @Query("DELETE FROM recipes WHERE id = :id")
    abstract suspend fun delete(id: String)

    /**
     * Writes a recipe and its lines as one unit. The lines are replaced wholesale
     * rather than diffed: a recipe is a handful of rows, and this cannot drift.
     */
    @Transaction
    open suspend fun upsert(recipe: Recipe) {
        insertRecipe(recipe.toEntity())
        clearIngredients(recipe.id)
        clearSteps(recipe.id)
        insertIngredients(recipe.ingredientRows())
        insertSteps(recipe.stepRows())
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertRecipe(recipe: RecipeEntity)

    @Insert
    protected abstract suspend fun insertIngredients(rows: List<IngredientEntity>)

    @Insert
    protected abstract suspend fun insertSteps(rows: List<StepEntity>)

    @Query("DELETE FROM ingredients WHERE recipeId = :recipeId")
    protected abstract suspend fun clearIngredients(recipeId: String)

    @Query("DELETE FROM steps WHERE recipeId = :recipeId")
    protected abstract suspend fun clearSteps(recipeId: String)
}
