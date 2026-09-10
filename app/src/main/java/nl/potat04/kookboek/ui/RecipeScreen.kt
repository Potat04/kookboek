package nl.potat04.kookboek.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nl.potat04.kookboek.R
import nl.potat04.kookboek.data.ParseQuality
import nl.potat04.kookboek.data.Recipe
import nl.potat04.kookboek.parse.Scaling
import nl.potat04.kookboek.ui.theme.controlOutline

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecipeScreen(
    recipe: Recipe,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onToggleFavourite: () -> Unit,
    onToggleIngredient: (Int) -> Unit,
    onToggleStep: (Int) -> Unit,
    onClearChecks: () -> Unit,
    onNotes: (String) -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    busy: Boolean,
    contentPadding: PaddingValues,
) {
    KeepScreenOn()
    val context = LocalContext.current

    var servings by remember(recipe.id) { mutableStateOf(recipe.servings) }
    val factor = remember(servings, recipe.servings) {
        val base = recipe.servings
        if (base == null || base <= 0 || servings == null) 1.0 else servings!!.toDouble() / base
    }
    var confirmDelete by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 48.dp,
        ),
    ) {
        item {
            DetailBar(
                recipe = recipe,
                busy = busy,
                onBack = onBack,
                onEdit = onEdit,
                onToggleFavourite = onToggleFavourite,
                onRefresh = onRefresh,
                onDelete = { confirmDelete = true },
            )
        }

        if (recipe.imageFile != null) {
            item {
                RecipeImage(
                    fileName = recipe.imageFile,
                    title = recipe.title,
                    corner = 10.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 10f),
                )
                Spacer(Modifier.height(20.dp))
            }
        }

        item {
            Text(recipe.displayTitle(), style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(8.dp))
            // Sites that publish themselves as the author give "24Kitchen · 24Kitchen".
            val byline = listOfNotNull(recipe.author, recipe.siteName)
                .distinctBy { it.lowercase() }
                .joinToString(" · ")
            if (byline.isNotBlank()) {
                Text(
                    byline,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!recipe.description.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    recipe.description,
                    // The blurb the site opens with. It used to be 15sp muted italic
                    // sans, which is the hardest thing to read on the whole screen —
                    // a paragraph you actually read deserves reading size and full
                    // ink. The serif italic keeps it apart from the steps without
                    // making it faint.
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(14.dp))
            // Flows onto a second line rather than squeezing: a site tag like
            // "Aziatische recepten" is long, and at the larger text sizes four tags no
            // longer fit across a phone.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                recipe.timeText()?.let { Tag(it) }
                // Scale the yield along with the ingredients, or "15 stuks" would sit
                // there contradicting a stepper that says 17. The count goes in too, so
                // that the plural agrees with the number actually shown.
                recipe.servingsText(count = servings ?: recipe.servings, factor = factor)
                    ?.let { Tag(it) }
                recipe.tags.take(2).forEach { Tag(it) }
            }
            Spacer(Modifier.height(20.dp))
        }

        if (recipe.quality == ParseQuality.LINK_ONLY) {
            item {
                CouldNotRead(
                    recipe = recipe,
                    hasSource = recipe.sourceUrl != null,
                    onOpen = { recipe.sourceUrl?.let { context.openLink(it) } },
                    onRetry = onRefresh,
                    onWrite = onEdit,
                )
                Spacer(Modifier.height(24.dp))
            }
        }

        if (recipe.ingredients.isNotEmpty()) {
            item {
                SectionHeader(
                    title = stringResource(R.string.recipe_ingredients),
                    action = if (recipe.checkedIngredients.isNotEmpty() || recipe.checkedSteps.isNotEmpty())
                        stringResource(R.string.recipe_clear_checks) to onClearChecks else null,
                )
                if (recipe.servings != null && recipe.servings > 0) {
                    ServingsStepper(
                        base = recipe.servings,
                        current = servings ?: recipe.servings,
                        onChange = { servings = it },
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
            itemsIndexed(recipe.ingredients) { index, line ->
                val previous = recipe.ingredients.getOrNull(index - 1)?.section
                if (line.section != null && line.section != previous) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        line.section,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                CheckLine(
                    text = if (factor == 1.0) line.text else Scaling.scale(line.text, factor),
                    checked = index in recipe.checkedIngredients,
                    onToggle = { onToggleIngredient(index) },
                )
            }
            item { Spacer(Modifier.height(26.dp)) }
        }

        if (recipe.steps.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.recipe_method)) }
            itemsIndexed(recipe.steps) { index, step ->
                val previous = recipe.steps.getOrNull(index - 1)?.section
                if (step.section != null && step.section != previous) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        step.section,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                StepRow(
                    number = index + 1,
                    text = step.text,
                    done = index in recipe.checkedSteps,
                    onToggle = { onToggleStep(index) },
                )
            }
            item { Spacer(Modifier.height(26.dp)) }
        }

        item {
            SectionHeader(stringResource(R.string.recipe_notes))
            NotesField(recipe.notes, onNotes)
            Spacer(Modifier.height(24.dp))
            recipe.sourceUrl?.let { url ->
                val where = recipe.siteName
                    ?: stringResource(R.string.recipe_view_original_generic)
                TextButton(onClick = { context.openLink(url) }) {
                    Text(stringResource(R.string.recipe_view_original, where))
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = {
                Text(
                    stringResource(R.string.recipe_delete_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Text(stringResource(R.string.recipe_delete_body, recipe.displayTitle()))
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}

@Composable
private fun DetailBar(
    recipe: Recipe,
    busy: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onToggleFavourite: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onToggleFavourite) {
            Icon(
                if (recipe.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = stringResource(
                    if (recipe.favorite) R.string.recipe_favourite_remove
                    else R.string.recipe_favourite_add
                ),
                tint = if (recipe.favorite) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit))
        }
        val context = LocalContext.current
        val images = LocalImageStore.current
        val scope = rememberCoroutineScope()
        var menu by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (recipe.sourceUrl != null) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (busy) R.string.recipe_refreshing
                                    else R.string.recipe_refresh
                                )
                            )
                        },
                        enabled = !busy,
                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                        onClick = { menu = false; onRefresh() },
                    )
                }
                // Three ways out of the app: to whoever you talk to, to another
                // Kookboek, and onto paper. See ui/Sharing.kt and ui/PrintRecipe.kt.
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.share_as_text)) },
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                    onClick = { menu = false; context.shareRecipeText(listOf(recipe)) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.share_as_file)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
                    onClick = {
                        menu = false
                        // Writing the zip is real work, so it waits for a scope rather
                        // than holding up the menu closing.
                        scope.launch { images?.let { context.shareRecipeFile(listOf(recipe), it) } }
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.share_print)) },
                    leadingIcon = {
                        Icon(painterResource(R.drawable.ic_print), contentDescription = null)
                    },
                    onClick = { menu = false; context.printRecipe(recipe, images) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete)) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: Pair<String, () -> Unit>? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.weight(1f))
        action?.let { (label, onClick) ->
            TextButton(onClick = onClick) {
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ServingsStepper(base: Int, current: Int, onChange: (Int) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.recipe_servings),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (current != base) {
                    Text(
                        stringResource(R.string.recipe_servings_from, base),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val fewer = stringResource(R.string.recipe_servings_fewer)
            IconButton(
                onClick = { onChange((current - 1).coerceAtLeast(1)) },
                enabled = current > 1,
                // The glyph is a typographic minus, which a screen reader reads as
                // nothing at all — the label has to come from somewhere.
                modifier = Modifier.semantics { contentDescription = fewer },
            ) {
                Text("–", style = MaterialTheme.typography.headlineSmall)
            }
            Text(
                "$current",
                style = MaterialTheme.typography.titleLarge,
                // A minimum, not a width: 28dp holds two digits at the normal text size
                // but not at "Extra groot", and the text size multiplies with the
                // system font scale on top of that.
                modifier = Modifier.widthIn(min = 28.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(
                onClick = { onChange((current + 1).coerceAtMost(99)) },
                enabled = current < 99,
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.recipe_servings_more),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun CheckLine(text: String, checked: Boolean, onToggle: () -> Unit) {
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

@Composable
private fun StepRow(number: Int, text: String, done: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 9.dp),
    ) {
        // The badge is sized from the number inside it rather than pinned at 28dp, so it
        // stays a circle around the digits at every text size.
        val badge = with(LocalDensity.current) {
            (MaterialTheme.typography.titleMedium.fontSize.toDp() * 1.75f)
                .coerceAtLeast(28.dp)
        }
        Box(
            Modifier
                .size(badge)
                .background(
                    if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    CircleShape,
                )
                .border(
                    1.dp,
                    if (done) MaterialTheme.colorScheme.primary
                    // Not the card hairline: an empty circle is the only thing saying
                    // this step is still to do.
                    else MaterialTheme.colorScheme.controlOutline,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                Text("$number", style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun NotesField(notes: String, onNotes: (String) -> Unit) {
    var draft by remember(notes) { mutableStateOf(notes) }
    // Notes save as you type, but only after you pause — no save button to forget.
    LaunchedEffect(draft) {
        if (draft != notes) {
            kotlinx.coroutines.delay(600)
            onNotes(draft)
        }
    }
    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        placeholder = { Text(stringResource(R.string.recipe_notes_hint)) },
        minLines = 3,
        shape = MaterialTheme.shapes.small,
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.controlOutline,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CouldNotRead(
    recipe: Recipe,
    hasSource: Boolean,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    onWrite: () -> Unit,
) {
    val context = LocalContext.current
    // The page behind a failed import is kept in the cache, and it is the one thing
    // that turns "this site does not work" into something that can be fixed.
    val keptPage = remember(recipe.id) { context.hasKeptPage(recipe) }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.recipe_unread_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.recipe_unread_body),
                // This is the screen admitting it failed. Reading it should not be
                // the second failure.
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            // Flows: four buttons, one of them a whole sentence, never fit on one line.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (hasSource) {
                    TextButton(onClick = onOpen) {
                        Text(stringResource(R.string.recipe_unread_open))
                    }
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.recipe_unread_retry))
                    }
                }
                TextButton(onClick = onWrite) {
                    Text(stringResource(R.string.recipe_unread_write))
                }
                if (keptPage) {
                    TextButton(onClick = { context.sharePage(recipe) }) {
                        Text(stringResource(R.string.share_help_site))
                    }
                }
            }
        }
    }
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

private fun android.content.Context.openLink(url: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
