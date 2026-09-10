package nl.potat04.kookboek.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.potat04.kookboek.R
import nl.potat04.kookboek.data.ParseQuality
import nl.potat04.kookboek.data.Recipe
import nl.potat04.kookboek.ui.theme.controlOutline

@Composable
fun LibraryScreen(
    recipes: List<Recipe>,
    total: Int,
    query: String,
    favouritesOnly: Boolean,
    sort: SortOrder,
    onQuery: (String) -> Unit,
    onToggleFavourites: () -> Unit,
    onSort: (SortOrder) -> Unit,
    onOpen: (Recipe) -> Unit,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
    selection: Set<String>,
    busy: Boolean,
    onToggleSelected: (Recipe) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onRefreshSelected: () -> Unit,
    contentPadding: PaddingValues,
) {
    val selecting = selection.isNotEmpty()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = contentPadding.calculateTopPadding() + 12.dp,
                bottom = contentPadding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                if (selecting) {
                    SelectionBar(
                        count = selection.size,
                        busy = busy,
                        onClear = onClearSelection,
                        onRefresh = onRefreshSelected,
                        onDelete = onDeleteSelected,
                    )
                } else {
                    Masthead(count = total, onSettings = onSettings)
                }
                Spacer(Modifier.height(14.dp))
            }

            if (total > 0) {
                item {
                    SearchField(query, onQuery)
                    Spacer(Modifier.height(10.dp))
                    FilterRow(favouritesOnly, sort, onToggleFavourites, onSort)
                    Spacer(Modifier.height(6.dp))
                }
            }

            when {
                total == 0 -> item { EmptyLibrary() }
                recipes.isEmpty() -> item { NoMatches(favouritesOnly) }
                else -> items(recipes, key = { it.id }) { recipe ->
                    RecipeCard(
                        recipe = recipe,
                        selected = recipe.id in selection,
                        // While picking, a plain tap keeps picking. Opening a recipe
                        // mid-selection would throw the choice away for a tap that was
                        // meant to add to it.
                        onClick = { if (selecting) onToggleSelected(recipe) else onOpen(recipe) },
                        onLongClick = { onToggleSelected(recipe) },
                    )
                }
            }
        }

        if (!selecting) ExtendedFloatingActionButton(
            onClick = onAdd,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .padding(bottom = contentPadding.calculateBottomPadding()),
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.library_add), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun Masthead(count: Int, onSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = logoPainter(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
            Text(
                if (count == 0) stringResource(R.string.library_empty_count)
                else pluralStringResource(R.plurals.library_count, count, count),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onSettings) {
            Icon(
                Icons.Default.Settings,
                contentDescription = stringResource(R.string.settings_title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun logoPainter(): Painter = painterResource(R.drawable.ic_logo)

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        placeholder = {
            Text(
                stringResource(R.string.library_search_hint),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, Modifier.size(20.dp)) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                Icon(
                    Icons.Default.Clear,
                    contentDescription = stringResource(R.string.library_search_clear),
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onQuery("") },
                )
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(),
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.controlOutline,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        ),
        textStyle = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FilterRow(
    favouritesOnly: Boolean,
    sort: SortOrder,
    onToggleFavourites: () -> Unit,
    onSort: (SortOrder) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = favouritesOnly,
            onClick = onToggleFavourites,
            label = {
                Text(
                    stringResource(R.string.library_favourites),
                    style = MaterialTheme.typography.labelLarge,
                )
            },
            leadingIcon = { Icon(Icons.Default.Favorite, contentDescription = null, Modifier.size(16.dp)) },
            shape = MaterialTheme.shapes.small,
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                // Not primary: the accent is tuned to sit on paper, and on its own
                // container it is the faintest pairing in the palette. The heart
                // matches the word beside it instead.
                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        )

        var open by remember { mutableStateOf(false) }
        Box {
            TextButton(onClick = { open = true }) {
                Text(stringResource(sort.labelRes), style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, Modifier.size(18.dp))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                SortOrder.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(option.labelRes)) },
                        onClick = { onSort(option); open = false },
                    )
                }
            }
        }
    }
}

/**
 * What the masthead turns into once recipes are picked out.
 *
 * It offers what a single recipe's own menu offers, refetch and delete, so that the way
 * to tidy up ten link-only imports is the way you already know for one.
 */
@Composable
private fun SelectionBar(
    count: Int,
    busy: Boolean,
    onClear: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClear) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.library_selection_clear),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            pluralStringResource(R.plurals.library_selected, count, count),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRefresh, enabled = !busy) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = stringResource(R.string.library_selection_refresh),
                tint = if (busy) MaterialTheme.colorScheme.outline
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.library_selection_delete),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun RecipeCard(
    recipe: Recipe,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                shape = MaterialTheme.shapes.medium,
            )
            .combinedClickable(onLongClick = onLongClick, onClick = onClick),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                RecipeImage(
                    fileName = recipe.imageFile,
                    title = recipe.title,
                    modifier = Modifier.size(78.dp),
                )
                // On the picture rather than in the row: a tick beside the text would
                // steal the width that "15 stuks" needs and truncate the yield away.
                if (selected) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(3.dp)
                            .size(24.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    recipe.displayTitle(),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = cardMeta(recipe),
                    // bodySmall, which is now 14sp rather than 13sp, and carried by a
                    // muted ink that reaches 9:1 instead of 5.6:1. Going a step further
                    // to bodyMedium was tried and reverted: it pushed "15 stuks" past
                    // the single line and truncated the yield away, and 16sp sans next
                    // to a 19sp serif title flattens the two into one voice.
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (recipe.quality == ParseQuality.LINK_ONLY) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        stringResource(R.string.library_link_only),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (recipe.favorite) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = stringResource(R.string.library_favourite),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun cardMeta(recipe: Recipe): String = listOfNotNull(
    recipe.siteName,
    recipe.timeText(),
    recipe.servingsText(),
).joinToString("  ·  ").ifBlank { stringResource(R.string.library_no_info) }

@Composable
private fun EmptyLibrary() {
    Column(Modifier.padding(top = 26.dp)) {
        Icon(
            painter = logoPainter(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(76.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.library_empty_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.library_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.library_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NoMatches(favouritesOnly: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(
                    if (favouritesOnly) R.string.library_no_matches_favourites
                    else R.string.library_no_matches
                ),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.library_no_matches_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Small rounded tag used on the detail screen.
 *
 * It carries the time and the yield, which are two of the three things you check
 * before you start cooking — so it gets a hairline of its own and a size you can
 * read, instead of the 11sp whisper it used to be.
 */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
