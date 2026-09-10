package nl.potat04.kookboek

import androidx.compose.ui.unit.dp
import nl.potat04.kookboek.ui.PageShape
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sizes are real ones: a Pixel is 411x869dp, a 7" tablet 600x960dp, a 10" tablet
 * 800x1280dp. Turning any of them is swapping the two numbers.
 */
class PageShapeTest {

    @Test
    fun `a phone standing up reads as one page`() {
        val shape = PageShape.of(411.dp, 869.dp)
        assertFalse(shape.twoPages)
        assertFalse(shape.shortHeader)
    }

    @Test
    fun `the same phone turned sideways opens on two`() {
        val shape = PageShape.of(869.dp, 411.dp)
        assertTrue(shape.twoPages)
        // And the picture has to give up its band across the top.
        assertTrue(shape.shortHeader)
    }

    @Test
    fun `a small phone sideways is still wide enough`() {
        assertTrue(PageShape.of(568.dp, 320.dp).twoPages)
    }

    @Test
    fun `a very narrow window sideways stays one page`() {
        // A freeform or split-screen window can be wider than tall and still too
        // narrow for two columns of ingredients.
        assertFalse(PageShape.of(460.dp, 400.dp).twoPages)
    }

    @Test
    fun `a tablet opens on two pages either way up`() {
        assertTrue(PageShape.of(800.dp, 1280.dp).twoPages)
        assertTrue(PageShape.of(1280.dp, 800.dp).twoPages)
        // Tall enough for the picture in both.
        assertFalse(PageShape.of(800.dp, 1280.dp).shortHeader)
        assertFalse(PageShape.of(1280.dp, 800.dp).shortHeader)
    }

    @Test
    fun `600dp portrait is the first width that opens`() {
        assertFalse(PageShape.of(599.dp, 960.dp).twoPages)
        assertTrue(PageShape.of(600.dp, 960.dp).twoPages)
    }
}
