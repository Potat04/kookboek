package nl.potat04.kookboek.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nl.potat04.kookboek.R
import nl.potat04.kookboek.data.Ingredient
import nl.potat04.kookboek.data.ParseQuality
import nl.potat04.kookboek.data.Recipe
import nl.potat04.kookboek.data.Step
import nl.potat04.kookboek.ui.theme.controlOutline

/**
 * Editing happens in plain multi-line text boxes: one ingredient or step per line.
 * On a phone that beats a list of tiny per-row fields — you can paste, reorder and
 * fix typos without fighting the keyboard.
 *
 * A picked photo is written to disk the moment it is picked, not on save. That keeps
 * one rule instead of two: a file no recipe points at is an orphan, and the prune at
 * the next launch clears it. Backing out of the editor therefore leaves nothing behind
 * that anybody has to remember to undo.
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
    var ingredients by remember {
        mutableStateOf(TextFieldValue(ingredientsToText(original.ingredients)))
    }
    var steps by remember { mutableStateOf(TextFieldValue(stepsToText(original.steps))) }
    var notes by remember { mutableStateOf(original.notes) }
    var imageFile by remember { mutableStateOf(original.imageFile) }
    var attachmentFile by remember { mutableStateOf(original.attachmentFile) }
    // A replacement picture keeps the old file name, so the screen has to be told to
    // read it again rather than show the copy it decoded a moment ago.
    var pictureStamp by remember { mutableIntStateOf(0) }
    var pictureFailed by remember { mutableStateOf(false) }
    // The recipe as it would be saved, held back until the reader agrees to lose the ticks.
    var confirming by remember { mutableStateOf<Recipe?>(null) }

    val store = LocalImageStore.current
    val scope = rememberCoroutineScope()

    val pickPicture = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) scope.launch {
            val name = store?.saveFromUri(uri, original.id)
            pictureFailed = name == null
            if (name != null) {
                imageFile = name
                pictureStamp++
            }
        }
    }
    val pickCard = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) scope.launch {
            val name = store?.saveFromUri(uri, original.id, CARD_SUFFIX)
            pictureFailed = name == null
            if (name != null) {
                attachmentFile = name
                pictureStamp++
            }
        }
    }

    fun edited(): Recipe {
        val parsedIngredients = textToIngredients(ingredients.text)
        val parsedSteps = textToSteps(steps.text)
        val typedServings = servings.trim().toIntOrNull()?.takeIf { it > 0 }
        // Ticks are stored by position, so they mean nothing once the lines move.
        // Better to lose them than to strike out the wrong line.
        val listsChanged = parsedIngredients != original.ingredients || parsedSteps != original.steps
        return original.copy(
            checkedIngredients = if (listsChanged) emptySet() else original.checkedIngredients,
            checkedSteps = if (listsChanged) emptySet() else original.checkedSteps,
            // A refetch later can then warn before it overwrites hand-typed lines.
            editedAt = if (listsChanged) System.currentTimeMillis() else original.editedAt,
            title = title.trim(),
            totalMinutes = minutes.trim().toIntOrNull()?.takeIf { it > 0 },
            servings = typedServings,
            // Changing the number makes the site's own wording stale ("15 stuks" next
            // to a 4), so it goes and the screen phrases the number in whichever
            // language is set. Comparing against the original matters: the field starts
            // out pre-filled from it, so "parses as a number" would be true the instant
            // the editor opens and any save at all would throw the wording away — and
            // it is not stored anywhere else.
            servingsLabel = if (typedServings != original.servings) null else original.servingsLabel,
            ingredients = parsedIngredients,
            steps = parsedSteps,
            imageFile = imageFile,
            attachmentFile = attachmentFile,
            notes = notes,
            quality = when {
                parsedIngredients.isNotEmpty() && parsedSteps.isNotEmpty() -> ParseQuality.FULL
                parsedIngredients.isNotEmpty() || parsedSteps.isNotEmpty() -> ParseQuality.PARTIAL
                else -> ParseQuality.LINK_ONLY
            },
        )
    }

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
                    val recipe = edited()
                    val hadTicks = original.checkedIngredients.isNotEmpty() ||
                        original.checkedSteps.isNotEmpty()
                    val ticksGoing = hadTicks &&
                        recipe.checkedIngredients.isEmpty() && recipe.checkedSteps.isEmpty()
                    if (ticksGoing) confirming = recipe else onSave(recipe)
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
        Spacer(Modifier.height(18.dp))

        PictureRow(
            fileName = imageFile,
            title = title,
            stamp = pictureStamp,
            size = 84.dp,
            label = stringResource(R.string.edit_photo_label),
            chooseLabel = stringResource(
                if (imageFile == null) R.string.edit_photo_choose else R.string.edit_photo_replace
            ),
            onChoose = { pickPicture.launch(imageRequest()) },
            onRemove = if (imageFile == null) null else { { imageFile = null } },
        )
        Spacer(Modifier.height(14.dp))

        PictureRow(
            fileName = attachmentFile,
            title = title,
            stamp = pictureStamp,
            size = 56.dp,
            label = stringResource(R.string.edit_card_label),
            chooseLabel = stringResource(
                if (attachmentFile == null) R.string.edit_card_choose else R.string.edit_photo_replace
            ),
            onChoose = { pickCard.launch(imageRequest()) },
            onRemove = if (attachmentFile == null) null else { { attachmentFile = null } },
        )
        if (pictureFailed) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.edit_photo_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(18.dp))

        LinesField(
            label = stringResource(R.string.edit_field_ingredients),
            value = ingredients,
            onValue = { ingredients = it },
            minLines = 6,
        )
        HeadingChip { ingredients = ingredients.withHeading() }
        Spacer(Modifier.height(14.dp))

        LinesField(
            label = stringResource(R.string.edit_field_steps),
            value = steps,
            onValue = { steps = it },
            minLines = 8,
        )
        HeadingChip { steps = steps.withHeading() }
        Spacer(Modifier.height(14.dp))

        Field(
            label = stringResource(R.string.recipe_notes),
            value = notes,
            onValue = { notes = it },
            minLines = 3,
        )
    }

    confirming?.let { recipe ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(stringResource(R.string.edit_ticks_title)) },
            text = { Text(stringResource(R.string.edit_ticks_body)) },
            confirmButton = {
                Button(onClick = { confirming = null; onSave(recipe) }) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}

/** The picture, what it is, and the two things you can do with it. */
@Composable
private fun PictureRow(
    fileName: String?,
    title: String,
    stamp: Int,
    size: Dp,
    label: String,
    chooseLabel: String,
    onChoose: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        key(stamp) {
            RecipeImage(fileName = fileName, title = title, modifier = Modifier.size(size))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onChoose) { Text(chooseLabel) }
                if (onRemove != null) {
                    TextButton(onClick = onRemove) {
                        Text(stringResource(R.string.edit_photo_remove))
                    }
                }
            }
        }
    }
}

/** Explains the "# heading" convention where it is used, by doing it for you. */
@Composable
private fun HeadingChip(onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onClick) {
            Text(stringResource(R.string.edit_add_heading))
        }
    }
}

private fun imageRequest() =
    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

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

/**
 * The two boxes that hold lines. They carry a [TextFieldValue] rather than a `String`
 * because "Add heading" has to know where the caret is.
 */
@Composable
private fun LinesField(
    label: String,
    value: TextFieldValue,
    onValue: (TextFieldValue) -> Unit,
    minLines: Int,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        minLines = minLines,
        shape = MaterialTheme.shapes.small,
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.controlOutline,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun TextFieldValue.withHeading(): TextFieldValue {
    val (typed, caret) = insertHeading(text, selection.start)
    return TextFieldValue(typed, TextRange(caret))
}

/** The suffix that tells the photographed card apart from the recipe's own picture. */
const val CARD_SUFFIX = "-card"

/**
 * Puts a heading marker where the caret is, and returns where the caret goes next.
 *
 * A heading owns its line, so one is started unless the caret already sits at the
 * beginning of one. The caret lands behind the "# " so the reader types the name of
 * the group straight away.
 */
fun insertHeading(text: String, cursor: Int): Pair<String, Int> {
    val at = cursor.coerceIn(0, text.length)
    val marker = if (at == 0 || text[at - 1] == '\n') "# " else "\n# "
    return text.substring(0, at) + marker + text.substring(at) to at + marker.length
}

/** Sections survive a round trip through the editor as "# heading" lines. */
fun stepsToText(steps: List<Step>): String =
    linesToText(steps.map { it.text to it.section })

fun textToSteps(text: String): List<Step> =
    textToLines(text) { line, section -> Step(line, section) }

/** Ingredient groups use the same convention, so a grouped list survives the editor too. */
fun ingredientsToText(ingredients: List<Ingredient>): String =
    linesToText(ingredients.map { it.text to it.section })

fun textToIngredients(text: String): List<Ingredient> =
    textToLines(text) { line, section -> Ingredient(line, section) }

private fun linesToText(lines: List<Pair<String, String?>>): String = buildString {
    var section: String? = null
    lines.forEach { (text, heading) ->
        if (heading != null && heading != section) {
            if (isNotEmpty()) append('\n')
            append("# ").append(heading).append('\n')
        }
        section = heading
        append(text).append('\n')
    }
}.trim()

private fun <T> textToLines(text: String, make: (String, String?) -> T): List<T> {
    var section: String? = null
    return text.lines().mapNotNull { raw ->
        val line = raw.trim()
        when {
            line.isEmpty() -> null
            line.startsWith("# ") -> { section = line.removePrefix("# ").trim(); null }
            else -> make(line, section)
        }
    }
}
