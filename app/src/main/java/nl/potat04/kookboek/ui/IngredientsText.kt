package nl.potat04.kookboek.ui

import nl.potat04.kookboek.data.Ingredient
import nl.potat04.kookboek.parse.Scaling

/**
 * The ingredient list as plain lines, the way it goes onto the clipboard: group
 * headings on their own line, one ingredient per line, amounts scaled to what the
 * stepper says. Pure, so the shape of the text is testable without a screen.
 */
object IngredientsText {

    fun lines(ingredients: List<Ingredient>, factor: Double = 1.0): List<String> = buildList {
        var section: String? = null
        ingredients.forEach { line ->
            if (line.section != null && line.section != section) {
                if (isNotEmpty()) add("")
                add(line.section)
            }
            section = line.section ?: section
            add(if (factor == 1.0) line.text else Scaling.scale(line.text, factor))
        }
    }

    fun clipboard(ingredients: List<Ingredient>, factor: Double = 1.0): String =
        lines(ingredients, factor).joinToString("\n")
}
