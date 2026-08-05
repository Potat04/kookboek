package nl.potat04.kookboek

import nl.potat04.kookboek.parse.Scaling
import org.junit.Assert.assertEquals
import org.junit.Test

class ScalingTest {

    @Test
    fun `doubling whole amounts`() {
        assertEquals("4 eieren", Scaling.scale("2 eieren", 2.0))
        assertEquals("1000 ml bouillon", Scaling.scale("500 ml bouillon", 2.0))
        assertEquals("30 g boter", Scaling.scale("15 g boter", 2.0))
    }

    @Test
    fun `halving produces readable fractions, not decimals`() {
        assertEquals("½ ui", Scaling.scale("1 ui", 0.5))
        assertEquals("1½ el olijfolie", Scaling.scale("3 el olijfolie", 0.5))
        assertEquals("⅓ cup Neutral Oil", Scaling.scale("2/3 cup Neutral Oil", 0.5))
    }

    @Test
    fun `weights above ten stay whole — a scale cannot do two thirds of a gram`() {
        assertEquals("142 gr taugé", Scaling.scale("125 gr taugé", 17.0 / 15))
        assertEquals("71 gr mihoen", Scaling.scale("63 gr mihoen", 17.0 / 15))
    }

    @Test
    fun `ranges keep both ends`() {
        assertEquals("2-4 tbsp Light Soy Sauce", Scaling.scale("1-2 tbsp Light Soy Sauce", 2.0))
    }

    @Test
    fun `only the leading quantity moves`() {
        // The "180" of the oven temperature and the "20" minutes must survive untouched.
        assertEquals(
            "2 el bloem, bak 20 minuten op 180 graden",
            Scaling.scale("1 el bloem, bak 20 minuten op 180 graden", 2.0),
        )
    }

    @Test
    fun `lines without a quantity are left alone`() {
        assertEquals("zout en peper", Scaling.scale("zout en peper", 2.0))
        assertEquals("Chilisaus voor erbij", Scaling.scale("Chilisaus voor erbij", 3.0))
    }

    @Test
    fun `factor of one changes nothing`() {
        assertEquals("2 uien", Scaling.scale("2 uien", 1.0))
    }

    @Test
    fun `understands vulgar fractions and comma decimals`() {
        assertEquals(1.5, Scaling.parseAmount("1½")!!, 0.001)
        assertEquals(0.75, Scaling.parseAmount("3/4")!!, 0.001)
        assertEquals(1.5, Scaling.parseAmount("1 1/2")!!, 0.001)
        assertEquals(2.5, Scaling.parseAmount("2,5")!!, 0.001)
    }
}
