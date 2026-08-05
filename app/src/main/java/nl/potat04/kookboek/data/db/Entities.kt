package nl.potat04.kookboek.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import nl.potat04.kookboek.data.ParseQuality
import nl.potat04.kookboek.data.Recipe
import nl.potat04.kookboek.data.Step

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey val id: String,
    val title: String,
    val sourceUrl: String?,
    val siteName: String?,
    val author: String?,
    val description: String?,
    val imageFile: String?,
    val imageUrl: String?,
    val totalMinutes: Int?,
    val servings: Int?,
    val servingsLabel: String?,
    val tags: List<String>,
    val notes: String,
    val favorite: Boolean,
    val addedAt: Long,
    val quality: ParseQuality,
)

/**
 * Ingredients and steps are rows, not a blob inside the recipe: that is the whole
 * point of using a database. [position] preserves the order the recipe was written in,
 * and [checked] rides along with the line it belongs to.
 */
@Entity(
    tableName = "ingredients",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("recipeId")],
)
data class IngredientEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val recipeId: String,
    val position: Int,
    val text: String,
    val checked: Boolean,
)

@Entity(
    tableName = "steps",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("recipeId")],
)
data class StepEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val recipeId: String,
    val position: Int,
    val text: String,
    val section: String?,
    val checked: Boolean,
)

data class RecipeWithParts(
    @Embedded val recipe: RecipeEntity,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val ingredients: List<IngredientEntity>,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val steps: List<StepEntity>,
)

// ------------------------------------------------------------------- mapping

/** @Relation makes no promise about row order, so sort on the column that does. */
fun RecipeWithParts.toDomain(): Recipe {
    val orderedIngredients = ingredients.sortedBy { it.position }
    val orderedSteps = steps.sortedBy { it.position }
    return Recipe(
        id = recipe.id,
        title = recipe.title,
        sourceUrl = recipe.sourceUrl,
        siteName = recipe.siteName,
        author = recipe.author,
        description = recipe.description,
        imageFile = recipe.imageFile,
        imageUrl = recipe.imageUrl,
        ingredients = orderedIngredients.map { it.text },
        steps = orderedSteps.map { Step(it.text, it.section) },
        totalMinutes = recipe.totalMinutes,
        servings = recipe.servings,
        servingsLabel = recipe.servingsLabel,
        tags = recipe.tags,
        notes = recipe.notes,
        favorite = recipe.favorite,
        addedAt = recipe.addedAt,
        checkedIngredients = orderedIngredients.indices
            .filter { orderedIngredients[it].checked }.toSet(),
        checkedSteps = orderedSteps.indices
            .filter { orderedSteps[it].checked }.toSet(),
        quality = recipe.quality,
    )
}

fun Recipe.toEntity() = RecipeEntity(
    id = id,
    title = title,
    sourceUrl = sourceUrl,
    siteName = siteName,
    author = author,
    description = description,
    imageFile = imageFile,
    imageUrl = imageUrl,
    totalMinutes = totalMinutes,
    servings = servings,
    servingsLabel = servingsLabel,
    tags = tags,
    notes = notes,
    favorite = favorite,
    addedAt = addedAt,
    quality = quality,
)

fun Recipe.ingredientRows(): List<IngredientEntity> = ingredients.mapIndexed { index, text ->
    IngredientEntity(
        recipeId = id,
        position = index,
        text = text,
        checked = index in checkedIngredients,
    )
}

fun Recipe.stepRows(): List<StepEntity> = steps.mapIndexed { index, step ->
    StepEntity(
        recipeId = id,
        position = index,
        text = step.text,
        section = step.section,
        checked = index in checkedSteps,
    )
}
