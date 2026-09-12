package nl.potat04.kookboek.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import nl.potat04.kookboek.data.Recipe

@Dao
abstract class RecipeDao {

    /** The cookbook as the reader sees it. Deleted recipes have their own query below. */
    @Transaction
    @Query("SELECT * FROM recipes WHERE deletedAt IS NULL ORDER BY addedAt DESC")
    abstract fun observeAll(): Flow<List<RecipeWithParts>>

    /** Newest deletion first, which is the one the reader most likely regrets. */
    @Transaction
    @Query("SELECT * FROM recipes WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    abstract fun observeDeleted(): Flow<List<RecipeWithParts>>

    /**
     * Writes a recipe and its lines as one unit. The lines are replaced wholesale
     * rather than diffed: a recipe is a handful of rows, and this cannot drift.
     *
     * The labels the recipe wears are written the same way. Any label it carries
     * that the labels table does not know yet is created first, so a recipe coming
     * back from an undo or a backup does not trip over the foreign key.
     */
    @Transaction
    open suspend fun upsert(recipe: Recipe) {
        insertRecipe(recipe.toEntity())
        clearIngredients(recipe.id)
        clearSteps(recipe.id)
        clearRecipeLabels(recipe.id)
        insertIngredients(recipe.ingredientRows())
        insertSteps(recipe.stepRows())
        insertLabelsIfMissing(recipe.labels.map { it.toEntity() })
        insertRecipeLabels(recipe.labelRows())
    }

    // ------------------------------------------------------------- soft delete

    /** Hides the recipe. The row, its lines and its picture all stay for [restoreDeleted]. */
    @Query("UPDATE recipes SET deletedAt = :now WHERE id = :id")
    abstract suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE recipes SET deletedAt = NULL WHERE id = :id")
    abstract suspend fun restoreDeleted(id: String)

    /** Really gone. The cascade takes the lines and the label links with it. */
    @Query("DELETE FROM recipes WHERE id = :id")
    abstract suspend fun deleteForever(id: String)

    @Query("DELETE FROM recipes WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    abstract suspend fun purgeDeletedBefore(cutoff: Long)

    /** Only the first opening counts, so a second one must not move the date. */
    @Query("UPDATE recipes SET openedAt = :now WHERE id = :id AND openedAt IS NULL")
    abstract suspend fun markOpened(id: String, now: Long)

    /**
     * The picture, straight onto the row. A download lands a moment after the recipe was
     * written, and reading the row back out of the mirrored list would mean waiting on a
     * collection that may not have happened yet.
     */
    @Query("UPDATE recipes SET imageFile = :name WHERE id = :id")
    abstract suspend fun setImageFile(id: String, name: String)

    // ------------------------------------------------------------------ labels

    @Query("SELECT * FROM labels ORDER BY position ASC")
    abstract fun observeLabels(): Flow<List<LabelEntity>>

    /**
     * The end of the list as the table has it. Asked here rather than of the mirrored
     * flow, which is a collection behind a label made a moment ago — several labels made
     * in one go would otherwise all land on the same position.
     */
    @Query("SELECT MAX(position) FROM labels")
    abstract suspend fun lastLabelPosition(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertLabel(label: LabelEntity)

    @Update
    abstract suspend fun updateLabels(labels: List<LabelEntity>)

    @Query("UPDATE labels SET name = :name WHERE id = :id")
    abstract suspend fun renameLabel(id: String, name: String)

    @Query("DELETE FROM labels WHERE id = :id")
    abstract suspend fun deleteLabel(id: String)

    @Transaction
    open suspend fun setLabels(recipeId: String, labelIds: Set<String>) {
        clearRecipeLabels(recipeId)
        insertRecipeLabels(labelIds.map { RecipeLabelEntity(recipeId = recipeId, labelId = it) })
    }

    // ---------------------------------------------------------------- plumbing

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertRecipe(recipe: RecipeEntity)

    @Insert
    protected abstract suspend fun insertIngredients(rows: List<IngredientEntity>)

    @Insert
    protected abstract suspend fun insertSteps(rows: List<StepEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertLabelsIfMissing(labels: List<LabelEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertRecipeLabels(rows: List<RecipeLabelEntity>)

    @Query("DELETE FROM ingredients WHERE recipeId = :recipeId")
    protected abstract suspend fun clearIngredients(recipeId: String)

    @Query("DELETE FROM steps WHERE recipeId = :recipeId")
    protected abstract suspend fun clearSteps(recipeId: String)

    @Query("DELETE FROM recipe_labels WHERE recipeId = :recipeId")
    protected abstract suspend fun clearRecipeLabels(recipeId: String)
}
