package nl.potat04.kookboek.data

import java.text.Normalizer

/**
 * Lower-cases and drops the accents, so that what you type finds what the page wrote.
 *
 * Recipes come off Dutch, French and Italian sites and keep their own spelling: "crème
 * fraîche", "jalapeño", "purée". Nobody reaches for the accent keys on a phone with
 * flour on it, so the recipe and the query both come through here before they meet.
 *
 * NFD splits a letter into its base and its marks and the regex throws the marks away.
 * A letter that is not a base plus a mark is left alone. "ß" stays "ß" and "ø" stays
 * "ø", because there is no accent there to drop, only a different letter.
 */
fun foldForSearch(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase()

private val COMBINING_MARKS = Regex("\\p{Mn}+")
