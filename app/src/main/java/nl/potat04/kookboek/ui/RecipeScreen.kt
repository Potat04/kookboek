package nl.potat04.kookboek.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import nl.potat04.kookboek.R
import nl.potat04.kookboek.data.ParseQuality
import nl.potat04.kookboek.data.Recipe
import nl.potat04.kookboek.ui.theme.controlOutline

// Item keys the scroll position is read against. The pill with the ingredients
// shows while the first visible item is one of the method's.
private const val KEY_METHOD = "method"
private const val KEY_METHOD_END = "method-end"
private const val KEY_STEP = "step:"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecipeScreen(
    recipe: Recipe,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onCook: () -> Unit,
    onToggleFavourite: () -> Unit,
    onToggleIngredient: (Int) -> Unit,
    onToggleStep: (Int) -> Unit,
    onClearChecks: () -> Unit,
    onNotes: (String) -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onSearch: (String) -> Unit,
    onServings: (Int) -> Unit,
    onMadeIt: (note: String?) -> Unit,
    onNotify: (UiText) -> Unit,
    onOpened: () -> Unit,
    haptics: Boolean,
    busy: Boolean,
    contentPadding: PaddingValues,
) {
    KeepScreenOn()
    val context = LocalContext.current
    val title = recipe.displayTitle()

    LaunchedEffect(recipe.id) { onOpened() }

    // The stepper picks up where it was left last time; the page's own number stays
    // the base the amounts are scaled from.
    var servings by remember(recipe.id) { mutableStateOf(recipe.cookedServings ?: recipe.servings) }
    val factor = remember(servings, recipe.servings) {
        val base = recipe.servings
        if (base == null || base <= 0 || servings == null) 1.0 else servings!!.toDouble() / base
    }

    val haptic = LocalHapticFeedback.current
    fun tick() {
        if (haptics) haptic.performHapticFeedback(HapticFeedbackType.Confirm)
    }
    val tickIngredient: (Int) -> Unit = { tick(); onToggleIngredient(it) }
    val tickStep: (Int) -> Unit = { tick(); onToggleStep(it) }

    var foldTicked by rememberSaveable { mutableStateOf(false) }
    var sheetOpen by remember { mutableStateOf(false) }
    var madeItOpen by remember { mutableStateOf(false) }
    var confirmRefetch by remember { mutableStateOf(false) }
    var fullScreen by remember { mutableStateOf<String?>(null) }

    // Refetching over hand-edited lines is the one thing here the snackbar cannot undo.
    val refetch = { if (recipe.editedAt != null) confirmRefetch = true else onRefresh() }

    val noClock = UiText.Res(R.string.recipe_no_clock)
    val onTimer: (Int) -> Unit = { seconds ->
        if (!context.startTimer(seconds, title)) onNotify(noClock)
    }

    val copyIngredients = {
        val text = IngredientsText.clipboard(recipe.ingredients, factor)
        context.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText(title, text))
        onNotify(UiText.Res(R.string.recipe_copied))
    }

    val listState = rememberLazyListState()
    val inMethod by remember(listState) {
        derivedStateOf {
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.key as? String
            first != null && (first == KEY_METHOD || first == KEY_METHOD_END || first.startsWith(KEY_STEP))
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 48.dp,
            ),
        ) {
            item(key = "bar") {
                DetailBar(
                    recipe = recipe,
                    busy = busy,
                    onBack = onBack,
                    onEdit = onEdit,
                    onToggleFavourite = onToggleFavourite,
                    onRefresh = refetch,
                    onCopyIngredients = copyIngredients,
                    onMadeIt = { madeItOpen = true },
                    onDelete = onDelete,
                )
            }

            if (recipe.imageFile != null) {
                item(key = "image") {
                    val open = stringResource(R.string.recipe_picture_open)
                    RecipeImage(
                        fileName = recipe.imageFile,
                        title = recipe.title,
                        corner = 10.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 10f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClickLabel = open) { fullScreen = recipe.imageFile },
                    )
                    Spacer(Modifier.height(20.dp))
                }
            }

            item(key = "head") {
                Text(title, style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(8.dp))
                Byline(recipe, onSearch = onSearch, onWatch = { context.openLink(it) })
                recipe.lastMadeText()?.let {
                    Text(
                        it,
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
                    recipe.timeDetailText()?.let { Tag(it) }
                    // Scale the yield along with the ingredients, or "15 stuks" would sit
                    // there contradicting a stepper that says 17. The count goes in too, so
                    // that the plural agrees with the number actually shown.
                    recipe.servingsText(count = servings ?: recipe.servings, factor = factor)
                        ?.let { Tag(it) }
                    // Every tag the site gave, and each one is a search.
                    recipe.tags.forEach { tag ->
                        val label = stringResource(R.string.recipe_search_for, tag)
                        Tag(
                            tag,
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(onClickLabel = label, role = Role.Button) { onSearch(tag) },
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            if (recipe.quality == ParseQuality.LINK_ONLY) {
                item(key = "unread") {
                    CouldNotRead(
                        hasSource = recipe.sourceUrl != null,
                        onOpen = { recipe.sourceUrl?.let { context.openLink(it) } },
                        onRetry = refetch,
                        onWrite = onEdit,
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }

            if (recipe.ingredients.isNotEmpty()) {
                item(key = "ingredients") {
                    val ticked = recipe.checkedIngredients.isNotEmpty()
                    val anyChecks = ticked || recipe.checkedSteps.isNotEmpty()
                    SectionHeader(
                        title = stringResource(R.string.recipe_ingredients),
                        actions = buildList {
                            if (ticked) add(
                                HeaderAction(
                                    stringResource(
                                        if (foldTicked) R.string.recipe_unfold_ticked
                                        else R.string.recipe_fold_ticked
                                    )
                                ) { foldTicked = !foldTicked },
                            )
                            if (anyChecks) add(
                                HeaderAction(stringResource(R.string.recipe_clear_checks), onClick = onClearChecks),
                            )
                        },
                    )
                    if (recipe.servings != null && recipe.servings > 0) {
                        ServingsStepper(
                            base = recipe.servings,
                            current = servings ?: recipe.servings,
                            onChange = { servings = it; onServings(it) },
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
                ingredientLines(
                    ingredients = recipe.ingredients,
                    checked = recipe.checkedIngredients,
                    factor = factor,
                    foldTicked = foldTicked,
                    keyPrefix = "ingredient",
                    onToggle = tickIngredient,
                    onUnfold = { foldTicked = false },
                )
                item(key = "ingredients-end") { Spacer(Modifier.height(26.dp)) }
            }

            if (recipe.steps.isNotEmpty()) {
                item(key = KEY_METHOD) {
                    // Ordered on purpose: whatever else wants a place in this header
                    // adds an entry here.
                    val methodActions = buildList {
                        // Where the steps begin is where you decide to stand up and cook.
                        add(HeaderAction(stringResource(R.string.cook_open), onClick = onCook))
                    }
                    SectionHeader(stringResource(R.string.recipe_method), actions = methodActions)
                }
                itemsIndexed(recipe.steps, key = { index, _ -> "$KEY_STEP$index" }) { index, step ->
                    val previous = recipe.steps.getOrNull(index - 1)?.section
                    if (step.section != null && step.section != previous) GroupHeading(step.section)
                    StepRow(
                        number = index + 1,
                        text = step.text,
                        done = index in recipe.checkedSteps,
                        onToggle = { tickStep(index) },
                        onTimer = onTimer,
                    )
                }
                item(key = KEY_METHOD_END) {
                    Spacer(Modifier.height(6.dp))
                    // Quiet on purpose: a pencilled date in the margin, not a rating.
                    TextButton(onClick = { madeItOpen = true }) {
                        Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.recipe_made_it), style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }

            item(key = "notes") {
                SectionHeader(stringResource(R.string.recipe_notes))
                NotesField(recipe.notes, onNotes)
                recipe.attachmentFile?.let { file ->
                    Spacer(Modifier.height(16.dp))
                    AttachmentPicture(file) { fullScreen = file }
                }
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

        // Ingredients within reach while you are in the method. Small, in the corner,
        // and gone again the moment you scroll back up.
        AnimatedVisibility(
            visible = inMethod && recipe.ingredients.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
        ) {
            IngredientsPill(onClick = { sheetOpen = true })
        }
    }

    if (sheetOpen) {
        IngredientsSheet(
            recipe = recipe,
            servings = servings ?: recipe.servings,
            factor = factor,
            onToggle = tickIngredient,
            onDismiss = { sheetOpen = false },
        )
    }
    if (madeItOpen) {
        MadeItDialog(
            onConfirm = { madeItOpen = false; onMadeIt(it) },
            onDismiss = { madeItOpen = false },
        )
    }
    if (confirmRefetch) {
        RefetchEditedDialog(
            onConfirm = { confirmRefetch = false; onRefresh() },
            onDismiss = { confirmRefetch = false },
        )
    }
    fullScreen?.let { FullScreenPicture(it) { fullScreen = null } }
}

/** One entry in the overflow menu. A list, so a new entry is one line to add. */
private data class MenuEntry(
    val label: String,
    val icon: ImageVector?,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
private fun DetailBar(
    recipe: Recipe,
    busy: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onToggleFavourite: () -> Unit,
    onRefresh: () -> Unit,
    onCopyIngredients: () -> Unit,
    onMadeIt: () -> Unit,
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
        var menu by remember { mutableStateOf(false) }
        val entries = buildList {
            if (recipe.sourceUrl != null) add(
                MenuEntry(
                    label = stringResource(if (busy) R.string.recipe_refreshing else R.string.recipe_refresh),
                    icon = Icons.Default.Refresh,
                    enabled = !busy,
                    onClick = onRefresh,
                ),
            )
            if (recipe.ingredients.isNotEmpty()) add(
                MenuEntry(
                    label = stringResource(R.string.recipe_copy_ingredients),
                    icon = Icons.AutoMirrored.Filled.List,
                    onClick = onCopyIngredients,
                ),
            )
            add(MenuEntry(stringResource(R.string.recipe_made_it), Icons.Default.Done, onClick = onMadeIt))
            add(MenuEntry(stringResource(R.string.action_delete), Icons.Default.Delete, onClick = onDelete))
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                entries.forEach { entry ->
                    DropdownMenuItem(
                        text = { Text(entry.label) },
                        enabled = entry.enabled,
                        leadingIcon = entry.icon?.let { { Icon(it, contentDescription = null) } },
                        onClick = { menu = false; entry.onClick() },
                    )
                }
            }
        }
    }
}

/**
 * Author and site on one muted line. The site name is a tap away from every recipe
 * off that site; a video link, when the page had one, sits at the end.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Byline(recipe: Recipe, onSearch: (String) -> Unit, onWatch: (String) -> Unit) {
    val site = recipe.siteName?.takeIf { it.isNotBlank() }
    // Sites that publish themselves as the author give "24Kitchen · 24Kitchen".
    val author = recipe.author?.takeIf { it.isNotBlank() && !it.equals(site, ignoreCase = true) }
    val video = recipe.videoUrl?.takeIf { it.isNotBlank() }
    if (site == null && author == null && video == null) return

    val style = MaterialTheme.typography.bodySmall
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    FlowRow(verticalArrangement = Arrangement.Center) {
        var first = true
        @Composable
        fun dot() {
            if (!first) Text(" · ", style = style, color = muted)
            first = false
        }
        author?.let { dot(); Text(it, style = style, color = muted) }
        site?.let {
            dot()
            val label = stringResource(R.string.recipe_search_for, it)
            Text(
                it,
                style = style,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClickLabel = label, role = Role.Button) { onSearch(it) },
            )
        }
        video?.let {
            dot()
            Text(
                stringResource(R.string.recipe_watch_video),
                style = style,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(role = Role.Button) { onWatch(it) },
            )
        }
    }
}

/** A button in a section header. With an [icon] it is an icon button labelled for a screen reader; without, plain text. */
private data class HeaderAction(
    val label: String,
    val icon: ImageVector? = null,
    val onClick: () -> Unit,
)

@Composable
private fun SectionHeader(title: String, actions: List<HeaderAction> = emptyList()) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.weight(1f))
        actions.forEach { action ->
            if (action.icon != null) {
                IconButton(onClick = action.onClick) {
                    Icon(action.icon, contentDescription = action.label)
                }
            } else {
                TextButton(onClick = action.onClick) {
                    Text(action.label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun IngredientsPill(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.controlOutline),
        shadowElevation = 2.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.List,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.recipe_ingredients), style = MaterialTheme.typography.labelLarge)
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
                // The glyph is drawn, not typed, so a screen reader hears nothing from
                // it — the label has to come from here.
                modifier = Modifier.semantics { contentDescription = fewer },
            ) {
                StepperGlyph(plus = false)
            }
            Text(
                "$current",
                style = MaterialTheme.typography.titleLarge,
                // A minimum, not a width: 28dp holds two digits at the normal text size
                // but not at "Extra groot", and the text size multiplies with the
                // system font scale on top of that.
                modifier = Modifier.widthIn(min = 28.dp),
                textAlign = TextAlign.Center,
            )
            val more = stringResource(R.string.recipe_servings_more)
            IconButton(
                onClick = { onChange((current + 1).coerceAtMost(99)) },
                enabled = current < 99,
                modifier = Modifier.semantics { contentDescription = more },
            ) {
                StepperGlyph(plus = true)
            }
        }
    }
}

/**
 * A minus or plus the height of the digits beside it. Drawn rather than typed: a
 * typographic minus is a third the size of the number and reads as a smudge from
 * across the counter. The button around it stays 48dp.
 */
@Composable
private fun StepperGlyph(plus: Boolean) {
    // Digits stand roughly seven tenths of the font size tall.
    val extent = with(LocalDensity.current) { MaterialTheme.typography.titleLarge.fontSize.toDp() } * 0.7f
    val colour = LocalContentColor.current
    Canvas(Modifier.size(extent)) {
        val stroke = 2.dp.toPx()
        val mid = size.height / 2
        drawLine(colour, Offset(0f, mid), Offset(size.width, mid), stroke, StrokeCap.Round)
        if (plus) drawLine(colour, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), stroke, StrokeCap.Round)
    }
}

@Composable
private fun StepRow(
    number: Int,
    text: String,
    done: Boolean,
    onToggle: () -> Unit,
    onTimer: (seconds: Int) -> Unit,
) {
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
        // "20 minuten" in a step is a tap on a timer; the rest of the words select on a
        // long press and tick the step on a short one.
        SelectionContainer {
            DurationText(
                text = text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onBackground,
                ),
                modifier = Modifier.padding(top = 2.dp),
                onTimer = onTimer,
            )
        }
    }
}

@Composable
private fun NotesField(notes: String, onNotes: (String) -> Unit) {
    var draft by remember(notes) { mutableStateOf(notes) }
    var saved by remember { mutableStateOf(false) }
    // Notes save as you type, but only after you pause — no save button to forget.
    LaunchedEffect(draft) {
        if (draft != notes) {
            delay(600)
            onNotes(draft)
            saved = true
            delay(1800)
            saved = false
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
    // Reserved height, so the word appearing does not push the page around.
    Box(
        Modifier
            .fillMaxWidth()
            .height(20.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        AnimatedVisibility(visible = saved, enter = fadeIn(), exit = fadeOut()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(R.string.recipe_notes_saved),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CouldNotRead(
    hasSource: Boolean,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    onWrite: () -> Unit,
) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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

private fun Context.openLink(url: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
