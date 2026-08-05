package nl.potat04.kookboek.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.potat04.kookboek.R
import nl.potat04.kookboek.data.ParseQuality
import nl.potat04.kookboek.data.Recipe

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
    contentPadding: PaddingValues,
) {
    Box(Modifier.fillMaxWidth()) {
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
                Masthead(count = total)
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
                    RecipeCard(recipe) { onOpen(recipe) }
                }
            }
        }

        ExtendedFloatingActionButton(
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
            Text("Toevoegen", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun Masthead(count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = logoPainter(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Kookboek", style = MaterialTheme.typography.displaySmall)
            Text(
                when (count) {
                    0 -> "nog leeg"
                    1 -> "1 recept"
                    else -> "$count recepten"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        placeholder = { Text("Zoek op naam of ingrediënt", style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, Modifier.size(20.dp)) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                Icon(
                    Icons.Default.Clear,
                    contentDescription = "Zoekopdracht wissen",
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
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
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
            label = { Text("Favorieten", style = MaterialTheme.typography.labelLarge) },
            leadingIcon = { Icon(Icons.Default.Favorite, contentDescription = null, Modifier.size(16.dp)) },
            shape = MaterialTheme.shapes.small,
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
            ),
        )

        var open by remember { mutableStateOf(false) }
        Box {
            TextButton(onClick = { open = true }) {
                Text(sort.label, style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, Modifier.size(18.dp))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                SortOrder.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = { onSort(option); open = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecipeCard(recipe: Recipe, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RecipeImage(
                fileName = recipe.imageFile,
                title = recipe.title,
                modifier = Modifier.size(78.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    recipe.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = cardMeta(recipe),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (recipe.quality == ParseQuality.LINK_ONLY) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "alleen de link",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (recipe.favorite) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = "Favoriet",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(18.dp),
                )
            }
        }
    }
}

private fun cardMeta(recipe: Recipe): String = listOfNotNull(
    recipe.siteName,
    recipe.timeLabel(),
    recipe.servingsLabelOrNull(),
).joinToString("  ·  ").ifBlank { "Geen extra info" }

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
        Text("Nog een leeg kookboek", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(10.dp))
        Text(
            "Vind een recept in je browser, tik op Delen en kies Kookboek. " +
                "De app leest het recept van de pagina en bewaart het hier — " +
                "ook als de site later offline gaat.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Of tik op Toevoegen om een link te plakken of zelf een recept te schrijven.",
            style = MaterialTheme.typography.bodySmall,
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
                if (favouritesOnly) "Geen favorieten die hierop passen"
                else "Niets gevonden",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Probeer een ander woord, of zoek op een ingrediënt.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Small rounded tag used on the detail screen. */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
