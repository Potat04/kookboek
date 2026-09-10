package nl.potat04.kookboek.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How much room the window gives a recipe, decided from the size of the window and
 * nothing else. Kept pure and out of the screen so it can be read, tested and reused
 * without a device, and so the app does not have to carry the window-size-class
 * library for one boolean.
 *
 * [twoPages] is the cookbook lying open: ingredients on the left page, method on the
 * right. It takes 600dp of width standing up, which is a tablet or a large foldable,
 * or 480dp lying down, which is a phone turned sideways and propped against the
 * tiles. Below that the recipe stays the single column it was designed as.
 *
 * [shortHeader] says the window is too shallow to spend height on a picture across
 * the top. A phone in landscape is about 400dp tall, and a 16:10 photo there would
 * eat both pages before the first ingredient.
 */
internal data class PageShape(val twoPages: Boolean, val shortHeader: Boolean) {
    companion object {
        /** Standing up: a tablet, a large foldable, a phone in a desktop window. */
        private val WIDE = 600.dp

        /** Lying down: the same recipe, but the width came from turning the phone. */
        private val WIDE_LANDSCAPE = 480.dp

        /** Under this the header gives up the hero picture for a small square. */
        private val SHALLOW = 480.dp

        fun of(width: Dp, height: Dp): PageShape {
            val landscape = width > height
            return PageShape(
                twoPages = width >= WIDE || (landscape && width >= WIDE_LANDSCAPE),
                shortHeader = height < SHALLOW,
            )
        }
    }
}
