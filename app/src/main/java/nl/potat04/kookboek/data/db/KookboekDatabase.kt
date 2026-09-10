package nl.potat04.kookboek.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.serialization.json.Json
import nl.potat04.kookboek.data.ParseQuality

class Converters {

    /**
     * Tags are display-only labels that are never queried on their own, so they stay
     * on the recipe row instead of earning a table. Ingredients and steps, which are
     * ordered and searched, are proper tables.
     */
    @TypeConverter
    fun tagsToJson(tags: List<String>): String = Json.encodeToString(tags)

    @TypeConverter
    fun tagsFromJson(raw: String): List<String> =
        runCatching { Json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())

    @TypeConverter
    fun qualityToName(quality: ParseQuality): String = quality.name

    @TypeConverter
    fun qualityFromName(raw: String): ParseQuality =
        runCatching { ParseQuality.valueOf(raw) }.getOrDefault(ParseQuality.FULL)
}

@Database(
    entities = [
        RecipeEntity::class,
        IngredientEntity::class,
        StepEntity::class,
        LabelEntity::class,
        RecipeLabelEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class KookboekDatabase : RoomDatabase() {
    abstract fun recipes(): RecipeDao
}
