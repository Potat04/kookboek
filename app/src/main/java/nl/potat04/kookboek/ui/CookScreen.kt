package nl.potat04.kookboek.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nl.potat04.kookboek.R
import nl.potat04.kookboek.data.Recipe
import nl.potat04.kookboek.data.Step
import nl.potat04.kookboek.parse.Scaling

/**
 * The same recipe, read with the phone standing on the counter.
 *
 * The recipe screen is a document: photo, headnote, the whole thing in one column,
 * which is right on the sofa and wrong at the hob, where every look down costs a
 * scroll past the picture and then the loss of your place. So this is the other
 * reading. One step to a page in the biggest serif the scale has, the ingredients a
 * thumb's pull from the bottom, and a step counter as the only furniture.
 *
 * Ticking a step stays a deliberate tap. Marking it off as you page past would tick
 * the step you skipped ahead to read, and the ticks are shared with the recipe screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookScreen(
    recipe: Recipe,
    onClose: () -> Unit,
    onToggleStep: (Int) -> Unit,
    onToggleIngredient: (Int) -> Unit,
    onNotify: (UiText) -> Unit,
    contentPadding: PaddingValues,
) {
    KeepScreenOn()
    val context = LocalContext.current
    val steps = recipe.steps

    // A refresh can empty the steps while someone is standing in here.
    LaunchedEffect(steps.isEmpty()) { if (steps.isEmpty()) onClose() }
    if (steps.isEmpty()) return

    val pager = rememberPagerState(
        // Opens where the cooking stopped rather than back at step one.
        initialPage = remember(recipe.id) {
            steps.indices.firstOrNull { it !in recipe.checkedSteps } ?: 0
        },
    ) { steps.size }
    var hint by rememberSaveable(recipe.id) { mutableStateOf(true) }

    // The stepper on the recipe screen writes cookedServings; this only reads it, so
    // both screens show the same amounts without either one owning the number.
    val servings = recipe.cookedServings ?: recipe.servings
    val factor = remember(servings, recipe.servings) {
        val base = recipe.servings
        if (base == null || base <= 0 || servings == null) 1.0 else servings.toDouble() / base
    }

    val title = recipe.displayTitle()
    val onTimer: (Int) -> Unit = { seconds ->
        // Never silent either way: the clock app is told to skip its own screen, so
        // without a word here nothing at all would appear to happen.
        onNotify(
            if (context.startTimer(seconds, title)) UiText.Res(R.string.cook_timer_started)
            else UiText.Res(R.string.cook_timer_unavailable)
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val bottom = contentPadding.calculateBottomPadding()
        // Pulled up all the way the sheet still leaves the step in sight; that is the
        // difference between this and walking back to the recipe screen.
        val sheetMaxHeight = maxHeight * 0.6f
        val bar = @Composable {
            Box(Modifier.padding(top = contentPadding.calculateTopPadding())) {
                // The page as a lambda, so paging redraws the counter and not the
                // sheet, the pager and everything else on the way down.
                CookBar(page = { pager.currentPage + 1 }, total = steps.size, onClose = onClose)
            }
        }

        if (maxWidth > maxHeight) {
            // Turned sideways there is room for both at once, and a sheet you have to
            // pull up over half the width would be the long way round.
            Column(Modifier.fillMaxSize()) {
                bar()
                Row(Modifier.weight(1f)) {
                    IngredientList(
                        recipe = recipe,
                        servings = servings,
                        factor = factor,
                        onToggle = onToggleIngredient,
                        modifier = Modifier
                            .weight(0.42f)
                            .fillMaxHeight()
                            .padding(start = 20.dp, end = 14.dp),
                        contentPadding = PaddingValues(bottom = bottom + 20.dp),
                    )
                    VerticalDivider(color = MaterialTheme.colorScheme.outline)
                    StepPages(
                        steps = steps,
                        checked = recipe.checkedSteps,
                        state = pager,
                        hint = hint,
                        onUsed = { hint = false },
                        onToggleStep = onToggleStep,
                        onTimer = onTimer,
                        modifier = Modifier
                            .weight(0.58f)
                            .fillMaxHeight()
                            .padding(bottom = bottom),
                    )
                }
            }
        } else {
            val sheet = rememberBottomSheetScaffoldState()
            BottomSheetScaffold(
                scaffoldState = sheet,
                // Enough for the handle and the heading; the rest comes up on a pull,
                // and the step behind it never moves.
                sheetPeekHeight = 78.dp + bottom,
                sheetContainerColor = MaterialTheme.colorScheme.surface,
                sheetContentColor = MaterialTheme.colorScheme.onSurface,
                // Material would tint a raised surface toward the accent, and this one
                // has to stay the same paper as the page behind it. See ui.md.
                sheetTonalElevation = 0.dp,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onBackground,
                topBar = bar,
                sheetContent = {
                    IngredientList(
                        recipe = recipe,
                        servings = servings,
                        factor = factor,
                        onToggle = onToggleIngredient,
                        modifier = Modifier
                            .heightIn(max = sheetMaxHeight)
                            .padding(horizontal = 22.dp),
                        contentPadding = PaddingValues(bottom = bottom + 24.dp),
                    )
                },
            ) { inner ->
                StepPages(
                    steps = steps,
                    checked = recipe.checkedSteps,
                    state = pager,
                    hint = hint,
                    onUsed = { hint = false },
                    onToggleStep = onToggleStep,
                    onTimer = onTimer,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inner),
                )
            }
        }
    }
}

/** The way into cook mode, sat in the method heading where the steps begin. */
@Composable
internal fun CookButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.cook_open), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun CookBar(page: () -> Int, total: Int, onClose: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.cook_counter, page(), total),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
        }
    }
}

@Composable
private fun StepPages(
    steps: List<Step>,
    checked: Set<Int>,
    state: PagerState,
    hint: Boolean,
    onUsed: () -> Unit,
    onToggleStep: (Int) -> Unit,
    onTimer: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val next = stringResource(R.string.cook_next_step)
    val previous = stringResource(R.string.cook_previous_step)

    // A swipe is the other half of the same gesture, so it puts the hint away too.
    val opened = remember { state.currentPage }
    LaunchedEffect(state.currentPage) { if (state.currentPage != opened) onUsed() }

    Box(modifier) {
        HorizontalPager(state = state, modifier = Modifier.fillMaxSize()) { page ->
            val step = steps[page]
            Box(
                Modifier
                    .fillMaxSize()
                    // The halves live on the page and not on a layer over it. A tap the
                    // step text has already taken — a duration — never gets here, and a
                    // drag reaches the pager underneath as a swipe.
                    .pointerInput(page, steps.size) {
                        detectTapGestures { offset ->
                            onUsed()
                            val target = if (offset.x > size.width / 2) page + 1 else page - 1
                            if (target in steps.indices) {
                                scope.launch { state.animateScrollToPage(target) }
                            }
                        }
                    }
                    // A screen reader has no left and right half to aim at.
                    .semantics {
                        customActions = listOfNotNull(
                            if (page < steps.lastIndex) {
                                CustomAccessibilityAction(next) {
                                    scope.launch { state.animateScrollToPage(page + 1) }
                                    true
                                }
                            } else null,
                            if (page > 0) {
                                CustomAccessibilityAction(previous) {
                                    scope.launch { state.animateScrollToPage(page - 1) }
                                    true
                                }
                            } else null,
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 26.dp, vertical = 20.dp),
                ) {
                    step.section?.let { section ->
                        Text(
                            section,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    DurationText(
                        text = step.text,
                        // The whole point of the screen: the step at reading size from
                        // the far side of the counter, in the serif the rest of the
                        // cookbook is set in.
                        style = MaterialTheme.typography.headlineMedium,
                        onTimer = onTimer,
                    )
                    Spacer(Modifier.height(28.dp))
                    StepDone(done = page in checked, onToggle = { onToggleStep(page) })
                }
            }
        }
        if (hint) {
            Text(
                stringResource(R.string.cook_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun StepDone(done: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = done,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        Text(
            stringResource(R.string.cook_step_done),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IngredientList(
    recipe: Recipe,
    servings: Int?,
    factor: Double,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    LazyColumn(modifier = modifier, contentPadding = contentPadding) {
        item {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    stringResource(R.string.recipe_ingredients),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.width(10.dp))
                recipe.servingsText(count = servings, factor = factor)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
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
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(index) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = index in recipe.checkedIngredients,
                    onCheckedChange = { onToggle(index) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
                Text(
                    if (factor == 1.0) line.text else Scaling.scale(line.text, factor),
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (index in recipe.checkedIngredients) {
                        TextDecoration.LineThrough
                    } else null,
                    color = if (index in recipe.checkedIngredients) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 2.dp, end = 4.dp),
                )
            }
        }
    }
}

/** Same reason as on the recipe screen: your hands are in the dough. */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}
