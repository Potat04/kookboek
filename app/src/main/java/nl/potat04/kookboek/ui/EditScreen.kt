package nl.potat04.kookboek.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import nl.potat04.kookboek.R
import nl.potat04.kookboek.data.ParseQuality
import nl.potat04.kookboek.data.Recipe
import nl.potat04.kookboek.data.Step
import nl.potat04.kookboek.ui.theme.controlOutline

/**
 * Editing happens in plain multi-line text boxes: one ingredient or step per line.
 * On a phone that beats a list of tiny per-row fields — you can paste, reorder and
 * fix typos without fighting the keyboard.
 */
@Composable
fun EditScreen(
    original: Recipe,
    isNew: Boolean,
    onCancel: () -> Unit,
    onSave: (Recipe) -> Unit,
    contentPadding: PaddingValues,
) {
    var title by remember { mutableStateOf(original.title) }
    var minutes by remember { mutableStateOf(original.totalMinutes?.toString().orEmpty()) }
    var servings by remember { mutableStateOf(original.servings?.toString().orEmpty()) }
    var ingredients by remember { mutableStateOf(original.ingredients.joinToString("\n")) }
    var steps by remember { mutableStateOf(stepsToText(original.steps)) }
    var notes by remember { mutableStateOf(original.notes) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 40.dp,
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel))
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    val parsedIngredients = ingredients.lines()
                        .map(String::trim).filter(String::isNotEmpty)
                    val parsedSteps = textToSteps(steps)
                    val typedServings = servings.trim().toIntOrNull()?.takeIf { it > 0 }
                    // Ticks are stored by position, so they mean nothing once the
                    // lines move. Better to lose them than to strike out the wrong line.
                    val listsChanged = parsedIngredients != original.ingredients ||
                        parsedSteps != original.steps
                    onSave(
                        original.copy(
                            checkedIngredients =
                                if (listsChanged) emptySet() else original.checkedIngredients,
                            checkedSteps =
                                if (listsChanged) emptySet() else original.checkedSteps,
                            title = title.trim(),
                            totalMinutes = minutes.trim().toIntOrNull()?.takeIf { it > 0 },
                            servings = typedServings,
                            // Changing the number makes the site's own wording stale
                            // ("15 stuks" next to a 4), so it goes and the screen phrases
                            // the number in whichever language is set. Comparing against
                            // the original matters: the field starts out pre-filled from
                            // it, so "parses as a number" would be true the instant the
                            // editor opens and any save at all would throw the wording
                            // away — and it is not stored anywhere else.
                            servingsLabel = if (typedServings != original.servings) null
                            else original.servingsLabel,
                            ingredients = parsedIngredients,
                            steps = parsedSteps,
                            notes = notes,
                            quality = when {
                                parsedIngredients.isNotEmpty() && parsedSteps.isNotEmpty() -> ParseQuality.FULL
                                parsedIngredients.isNotEmpty() || parsedSteps.isNotEmpty() -> ParseQuality.PARTIAL
                                else -> ParseQuality.LINK_ONLY
                            },
                        )
                    )
                },
                enabled = title.isNotBlank(),
            ) { Text(stringResource(R.string.action_save)) }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(if (isNew) R.string.edit_new_title else R.string.edit_title),
            style = MaterialTheme.typography.displaySmall,
        )
        Spacer(Modifier.height(20.dp))

        Field(
            label = stringResource(R.string.edit_field_title),
            value = title,
            onValue = { title = it },
            singleLine = true,
        )
        Spacer(Modifier.height(14.dp))

        Row {
            Field(
                label = stringResource(R.string.edit_field_minutes),
                value = minutes,
                onValue = { minutes = it.filter(Char::isDigit).take(4) },
                singleLine = true,
                numeric = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Field(
                label = stringResource(R.string.recipe_servings),
                value = servings,
                onValue = { servings = it.filter(Char::isDigit).take(3) },
                singleLine = true,
                numeric = true,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(14.dp))

        Field(
            label = stringResource(R.string.edit_field_ingredients),
            value = ingredients,
            onValue = { ingredients = it },
            minLines = 6,
        )
        Spacer(Modifier.height(14.dp))

        Field(
            label = stringResource(R.string.edit_field_steps),
            value = steps,
            onValue = { steps = it },
            minLines = 8,
        )
        Spacer(Modifier.height(14.dp))

        Field(
            label = stringResource(R.string.recipe_notes),
            value = notes,
            onValue = { notes = it },
            minLines = 3,
        )
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    numeric: Boolean = false,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number)
        else KeyboardOptions.Default,
        shape = MaterialTheme.shapes.small,
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.controlOutline,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Sections survive a round trip through the editor as "# heading" lines. */
fun stepsToText(steps: List<Step>): String = buildString {
    var section: String? = null
    steps.forEach { step ->
        if (step.section != null && step.section != section) {
            if (isNotEmpty()) append('\n')
            append("# ").append(step.section).append('\n')
        }
        section = step.section
        append(step.text).append('\n')
    }
}.trim()

fun textToSteps(text: String): List<Step> {
    var section: String? = null
    return text.lines().mapNotNull { raw ->
        val line = raw.trim()
        when {
            line.isEmpty() -> null
            line.startsWith("# ") -> { section = line.removePrefix("# ").trim(); null }
            else -> Step(line, section)
        }
    }
}
