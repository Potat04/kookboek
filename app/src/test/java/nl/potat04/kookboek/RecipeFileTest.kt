package nl.potat04.kookboek

import nl.potat04.kookboek.data.Ingredient
import nl.potat04.kookboek.data.Label
import nl.potat04.kookboek.data.Recipe
import nl.potat04.kookboek.data.RecipeFile
import nl.potat04.kookboek.data.RecipeJson
import nl.potat04.kookboek.data.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A `.kookboek` file is the only thing in the app that has to be readable by a copy of
 * the app on someone else's phone, so what goes in has to come out byte for byte —
 * pictures included, and through the lock.
 */
class RecipeFileTest {

    private val picture = ByteArray(2048) { (it % 251).toByte() }
    private val card = byteArrayOf(1, 2, 3, 4, 5)

    private val recipe = Recipe(
        id = "r1",
        title = "Loempia's uit de airfryer",
        sourceUrl = "https://www.leukerecepten.nl/recepten/loempia-maken-in-de-airfryer/",
        siteName = "Leuke Recepten",
        imageFile = "r1.jpg",
        attachmentFile = "r1-card.jpg",
        ingredients = listOf(Ingredient("15 loempiavellen"), Ingredient("125 gr taugé", "Vulling")),
        steps = listOf(Step("Snijd de groenten fijn."), Step("Bak 12 minuten.", "Airfryer")),
        servings = 15,
        servingsLabel = "15 stuks",
        labels = listOf(Label(id = "l1", name = "Doordeweeks")),
        notes = "Volgende keer meer sambal.",
        favorite = true,
        addedAt = 1_700_000_000_000,
    )

    private val pictures = mapOf("r1.jpg" to picture, "r1-card.jpg" to card)

    private fun write(recipes: List<Recipe> = listOf(recipe)): ByteArray =
        ByteArrayOutputStream().also { out ->
            RecipeFile.write(out, recipes, exportedAt = 42) { pictures[it] }
        }.toByteArray()

    private fun read(bytes: ByteArray): Pair<Result<RecipeFile.Content>, Map<String, ByteArray>> {
        val out = mutableMapOf<String, ByteArray>()
        val result = RecipeFile.read(ByteArrayInputStream(bytes)) { name, data -> out[name] = data }
        return result to out
    }

    @Test
    fun `a recipe and its pictures survive the round trip`() {
        val (result, images) = read(write())
        val content = result.getOrThrow()

        assertEquals(RecipeJson.FORMAT, content.document.format)
        assertEquals(42L, content.document.exportedAt)

        val dto = content.document.recipes.single()
        assertEquals(listOf("Doordeweeks"), dto.labels)
        val back = dto.toRecipe(labels = recipe.labels)
        assertEquals(recipe, back)

        assertEquals(setOf("r1.jpg", "r1-card.jpg"), images.keys)
        assertArrayEqualsNamed("r1.jpg", picture, images.getValue("r1.jpg"))
        assertArrayEqualsNamed("r1-card.jpg", card, images.getValue("r1-card.jpg"))
    }

    @Test
    fun `several recipes go in one file`() {
        val other = recipe.copy(id = "r2", title = "Soep", imageFile = null, attachmentFile = null)
        val content = read(write(listOf(recipe, other))).first.getOrThrow()
        assertEquals(listOf("r1", "r2"), content.document.recipes.map { it.id })
    }

    @Test
    fun `a missing picture is left out rather than failing the write`() {
        val bytes = ByteArrayOutputStream().also { out ->
            RecipeFile.write(out, listOf(recipe)) { null }
        }.toByteArray()
        val (result, images) = read(bytes)
        assertTrue(images.isEmpty())
        assertEquals("r1", result.getOrThrow().document.recipes.single().id)
    }

    @Test
    fun `the recipe is not sitting there in plain sight`() {
        val text = write().toString(Charsets.ISO_8859_1)
        assertFalse("the title is readable in the file", text.contains("Loempia"))
        assertFalse("the source url is readable in the file", text.contains("leukerecepten"))
    }

    @Test
    fun `a file that is not one of ours comes back as a result`() {
        val error = read("just some text".toByteArray()).first.exceptionOrNull()
        assertTrue("$error", error is RecipeJson.DecodeError.NotAKookboekFile)
    }

    @Test
    fun `a zip with the right names but no lock on it is refused`() {
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry(RecipeFile.DOCUMENT_ENTRY))
                zip.write(RecipeJson.encode(listOf(recipe)).toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
        val error = read(bytes).first.exceptionOrNull()
        assertTrue("$error", error is RecipeJson.DecodeError.NotAKookboekFile)
    }

    private fun assertArrayEqualsNamed(name: String, expected: ByteArray, actual: ByteArray) =
        assertEquals("$name came back different", expected.toList(), actual.toList())
}
