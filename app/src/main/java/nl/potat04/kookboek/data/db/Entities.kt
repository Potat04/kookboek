package nl.potat04.kookboek.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import nl.potat04.kookboek.data.Ingredient
import nl.potat04.kookboek.data.Label
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
    // Version 2. All nullable so the migration is a set of ADD COLUMNs and an existing
    // row means exactly what it meant before.
    val cookedServings: Int?,
    val lastCookedAt: Long?,
    val editedAt: Long?,
    val prepMinutes: Int?,
    val cookMinutes: Int?,
    val videoUrl: String?,
    val deletedAt: Long?,
    val attachmentFile: String?,
    val openedAt: Long?,
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
    val section: String?,
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

/**
 * Labels are a table where tags are a JSON column, because a label is shared: renaming
 * it has to reach every recipe wearing it, and the library filters on it.
 */
@Entity(tableName = "labels")
data class LabelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val position: Int,
)

@Entity(
    tableName = "recipe_labels",
    primaryKeys = ["recipeId", "labelId"],
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LabelEntity::class,
            parentColumns = ["id"],
            childColumns = ["labelId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("recipeId"), Index("labelId")],
)
data class RecipeLabelEntity(
    val recipeId: String,
    val labelId: String,
)

data class RecipeWithParts(
    @Embedded val recipe: RecipeEntity,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val ingredients: List<IngredientEntity>,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val steps: List<StepEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = RecipeLabelEntity::class,
            parentColumn = "recipeId",
            entityColumn = "labelId",
        ),
    )
    val labels: List<LabelEntity>,
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
        ingredients = orderedIngredients.map { Ingredient(it.text, it.section) },
        steps = orderedSteps.map { Step(it.text, it.section) },
        prepMinutes = recipe.prepMinutes,
        cookMinutes = recipe.cookMinutes,
        totalMinutes = recipe.totalMinutes,
        servings = recipe.servings,
        servingsLabel = recipe.servingsLabel,
        tags = recipe.tags,
        labels = labels.sortedBy { it.position }.map { it.toDomain() },
        notes = recipe.notes,
        favorite = recipe.favorite,
        addedAt = recipe.addedAt,
        checkedIngredients = orderedIngredients.indices
            .filter { orderedIngredients[it].checked }.toSet(),
        checkedSteps = orderedSteps.indices
            .filter { orderedSteps[it].checked }.toSet(),
        quality = recipe.quality,
        cookedServings = recipe.cookedServings,
        lastCookedAt = recipe.lastCookedAt,
        editedAt = recipe.editedAt,
        videoUrl = recipe.videoUrl,
        deletedAt = recipe.deletedAt,
        attachmentFile = recipe.attachmentFile,
        openedAt = recipe.openedAt,
    )
}

fun LabelEntity.toDomain() = Label(id = id, name = name, position = position)

fun Label.toEntity() = LabelEntity(id = id, name = name, position = position)

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
    cookedServings = cookedServings,
    lastCookedAt = lastCookedAt,
    editedAt = editedAt,
    prepMinutes = prepMinutes,
    cookMinutes = cookMinutes,
    videoUrl = videoUrl,
    deletedAt = deletedAt,
    attachmentFile = attachmentFile,
    openedAt = openedAt,
)

fun Recipe.ingredientRows(): List<IngredientEntity> = ingredients.mapIndexed { index, line ->
    IngredientEntity(
        recipeId = id,
        position = index,
        text = line.text,
        section = line.section,
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

fun Recipe.labelRows(): List<RecipeLabelEntity> =
    labels.map { RecipeLabelEntity(recipeId = id, labelId = it.id) }
