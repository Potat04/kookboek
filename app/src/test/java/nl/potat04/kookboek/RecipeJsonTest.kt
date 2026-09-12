package nl.potat04.kookboek

import nl.potat04.kookboek.data.Ingredient
import nl.potat04.kookboek.data.Label
import nl.potat04.kookboek.data.ParseQuality
import nl.potat04.kookboek.data.Recipe
import nl.potat04.kookboek.data.RecipeJson
import nl.potat04.kookboek.data.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The file format outlives any one version of the app, so what goes out must come
 * back unchanged, and a file from the future must be refused politely rather than
 * half-read.
 */
class RecipeJsonTest {

    private val label = Label(id = "l1", name = "Doordeweeks", position = 0)

    private val recipe = Recipe(
        id = "r1",
        title = "Loempia's uit de airfryer",
        sourceUrl = "https://www.leukerecepten.nl/recepten/loempia-maken-in-de-airfryer/",
        siteName = "Leuke Recepten",
        author = "Sandra Waterschoot",
        description = "Krokant zonder frituren.",
        imageFile = "r1.jpg",
        ingredients = listOf(
            Ingredient("15 loempiavellen"),
            Ingredient("125 gr taugé", section = "Vulling"),
        ),
        steps = listOf(Step("Snijd de groenten fijn."), Step("Bak 12 minuten.", section = "Airfryer")),
        prepMinutes = 20,
        cookMinutes = 30,
        totalMinutes = 50,
        servings = 15,
        servingsLabel = "15 stuks",
        tags = listOf("Aziatisch"),
        labels = listOf(label),
        notes = "Volgende keer meer sambal.",
        favorite = true,
        addedAt = 1_700_000_000_000,
        checkedIngredients = setOf(0),
        quality = ParseQuality.FULL,
        cookedServings = 17,
        lastCookedAt = 1_700_100_000_000,
        editedAt = 1_700_050_000_000,
        videoUrl = "https://example.com/v",
        attachmentFile = "r1-card.jpg",
        openedAt = 1_700_000_500_000,
    )

    @Test
    fun `a recipe survives the round trip`() {
        val text = RecipeJson.encode(listOf(recipe), exportedAt = 42)
        val document = RecipeJson.decode(text).getOrThrow()

        assertEquals(RecipeJson.FORMAT, document.format)
        assertEquals(RecipeJson.VERSION, document.version)
        assertEquals(42L, document.exportedAt)
        assertEquals(1, document.recipes.size)

        val dto = document.recipes.single()
        assertEquals(listOf("Doordeweeks"), dto.labels)

        // Checks and the opened/deleted dates are the reader's session, not the recipe,
        // so they are the only fields expected to come back blank.
        val back = dto.toRecipe(labels = listOf(label))
        assertEquals(
            recipe.copy(checkedIngredients = emptySet(), checkedSteps = emptySet(), openedAt = null),
            back,
        )
    }

    @Test
    fun `labels can be supplied separately from the recipe`() {
        val text = RecipeJson.encode(listOf(recipe), labelsByRecipe = mapOf("r1" to listOf("Feest")))
        assertEquals(listOf("Feest"), RecipeJson.decode(text).getOrThrow().recipes.single().labels)
    }

    @Test
    fun `a document from a newer app is refused with a typed error`() {
        val result = RecipeJson.decode(
            """{"format":"kookboek","version":99,"exportedAt":1,"recipes":[],"somethingNew":true}""",
        )
        val error = result.exceptionOrNull()
        assertTrue("$error", error is RecipeJson.DecodeError.NewerVersion)
        assertEquals(99, (error as RecipeJson.DecodeError.NewerVersion).version)
    }

    @Test
    fun `unknown keys from a later minor version are ignored`() {
        val text = """
            {"format":"kookboek","version":1,"exportedAt":1,"recipes":[
              {"id":"x","title":"Soep","addedAt":5,"mood":"cosy","ingredients":[{"text":"water","emoji":"💧"}]}
            ]}
        """.trimIndent()
        val recipe = RecipeJson.decode(text).getOrThrow().recipes.single().toRecipe()
        assertEquals("Soep", recipe.title)
        assertEquals(listOf(Ingredient("water")), recipe.ingredients)
    }

    @Test
    fun `a picture name that climbs out of the images directory is dropped`() {
        // Nothing stops someone hand-editing a recipes.json and handing it back. A name
        // like this passes an "does the file exist" check and would have the database
        // itself packed into the next backup.
        val text = """
            {"format":"kookboek","version":1,"exportedAt":1,"recipes":[
              {"id":"x","title":"Soep","addedAt":5,
               "imageFile":"../databases/kookboek.db","attachmentFile":"nested/photo.jpg"}
            ]}
        """.trimIndent()
        val back = RecipeJson.decode(text).getOrThrow().recipes.single().toRecipe()
        assertNull(back.imageFile)
        assertNull(back.attachmentFile)
    }

    @Test
    fun `a plain file name is left alone`() {
        assertEquals("r1.jpg", RecipeJson.bareName("r1.jpg"))
        assertNull(RecipeJson.bareName(null))
        assertNull(RecipeJson.bareName("  "))
        assertNull(RecipeJson.bareName(".."))
        assertNull(RecipeJson.bareName("nested\\photo.jpg"))
    }

    @Test
    fun `garbage is not an exception but a result`() {
        assertTrue(RecipeJson.decode("not json at all").exceptionOrNull() is RecipeJson.DecodeError.NotAKookboekFile)
        assertTrue(RecipeJson.decode("""{"format":"other","version":1,"exportedAt":0}""").exceptionOrNull() is RecipeJson.DecodeError.NotAKookboekFile)
    }
}
