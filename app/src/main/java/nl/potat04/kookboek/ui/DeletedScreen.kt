package nl.potat04.kookboek.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.potat04.kookboek.R
import nl.potat04.kookboek.data.Recipe

/**
 * The bin. Everything here still has its rows and its photo; thirty days after the
 * delete, [nl.potat04.kookboek.KookboekApp] throws it out for good on the next launch.
 *
 * "Delete for good" is the one action in the app that asks first. Every other delete
 * has an undo in the snackbar; this one is the undo running out.
 */
@Composable
fun DeletedScreen(
    recipes: List<Recipe>,
    onRestore: (String) -> Unit,
    onDeleteForever: (String) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
    // The id and not the recipe: this survives a rotation, and the row it points at
    // can be restored from another screen while the question is up.
    var confirming by rememberSaveable { mutableStateOf<String?>(null) }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 40.dp,
        ),
    ) {
        item {
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
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.backup_deleted),
                style = MaterialTheme.typography.displaySmall,
            )
            Spacer(Modifier.height(20.dp))
        }

        if (recipes.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.backup_deleted_empty),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.backup_deleted_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(recipes, key = { it.id }) { recipe ->
            DeletedRow(
                recipe = recipe,
                onRestore = { onRestore(recipe.id) },
                onDeleteForever = { confirming = recipe.id },
            )
            Rule(Modifier.padding(vertical = 4.dp))
        }
    }

    confirming?.let { id ->
        val recipe = recipes.firstOrNull { it.id == id } ?: return@let
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = {
                Text(
                    stringResource(R.string.backup_deleted_forever_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Text(stringResource(R.string.backup_deleted_forever_body, recipe.displayTitle()))
            },
            confirmButton = {
                TextButton(onClick = { confirming = null; onDeleteForever(recipe.id) }) {
                    Text(stringResource(R.string.backup_deleted_forever))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun DeletedRow(recipe: Recipe, onRestore: () -> Unit, onDeleteForever: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecipeImage(
            fileName = recipe.imageFile,
            title = recipe.displayTitle(),
            modifier = Modifier.size(52.dp),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(recipe.displayTitle(), style = MaterialTheme.typography.titleMedium)
            // The date is the whole point of this screen: it says how long you have left.
            Text(
                stringResource(R.string.backup_deleted_on, dayText(recipe.deletedAt ?: recipe.addedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
            TextButton(onClick = onRestore) {
                Text(stringResource(R.string.backup_deleted_restore))
            }
            TextButton(onClick = onDeleteForever) {
                Text(
                    stringResource(R.string.backup_deleted_forever),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
