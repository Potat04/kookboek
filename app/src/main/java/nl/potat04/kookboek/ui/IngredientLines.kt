package nl.potat04.kookboek.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import nl.potat04.kookboek.R
import nl.potat04.kookboek.data.Ingredient
import nl.potat04.kookboek.parse.Scaling

/**
 * The ingredient list as lazy items, shared by the recipe screen and the pull-up
 * sheet over the method, so a group heading, a tick and a scaled amount look and
 * behave the same in both places.
 *
 * With [foldTicked] the ticked lines leave the list and one row at the end counts
 * them. The lines that remain keep their order; a group whose every line is ticked
 * loses its heading too, since a heading over nothing is just noise.
 */
internal fun LazyListScope.ingredientLines(
    ingredients: List<Ingredient>,
    checked: Set<Int>,
    factor: Double,
    foldTicked: Boolean,
    keyPrefix: String,
    onToggle: (Int) -> Unit,
    onUnfold: () -> Unit,
) {
    val visible = ingredients.indices.filter { !foldTicked || it !in checked }
    // Headings compare against the previous *shown* line, not the previous stored one,
    // otherwise a group folded away in full would take the next group's heading with it.
    val rows = visible.mapIndexed { pos, index ->
        val section = ingredients[index].section
        val previous = visible.getOrNull(pos - 1)?.let { ingredients[it].section }
        index to (section != null && section != previous)
    }
    items(rows, key = { (index, _) -> "$keyPrefix:$index" }) { (index, heading) ->
        val line = ingredients[index]
        if (heading) GroupHeading(line.section!!)
        CheckLine(
            text = if (factor == 1.0) line.text else Scaling.scale(line.text, factor),
            checked = index in checked,
            onToggle = { onToggle(index) },
        )
    }
    val gathered = if (foldTicked) ingredients.indices.count { it in checked } else 0
    if (gathered > 0) {
        item(key = "$keyPrefix:gathered") { GatheredRow(gathered, onUnfold) }
    }
}

/** The recipe's own sub-heading, "Voor de saus": same for ingredients and steps. */
@Composable
internal fun GroupHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
    )
}

@Composable
internal fun CheckLine(text: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                // An empty tick box is a control you have to find with a wet hand, so
                // it gets the muted ink rather than the hairline that edges the cards.
                // The printed rule is deliberately quiet; this must not be.
                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        // Long-press selects the words, a tap still ticks the line: the selection
        // container only claims the press once it has become a long one.
        SelectionContainer {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (checked) TextDecoration.LineThrough else null,
                color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 2.dp, end = 4.dp),
            )
        }
    }
}

/** "3 klaargezet": the folded-away ticks, in one line. Tapping it unfolds them. */
@Composable
private fun GatheredRow(count: Int, onUnfold: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onUnfold)
            .padding(vertical = 8.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            pluralStringResource(R.plurals.recipe_gathered, count, count),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
