package nl.potat04.kookboek.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.potat04.kookboek.R
import nl.potat04.kookboek.data.Recipe

/**
 * The ingredients, pulled up over the method so you can check an amount without
 * scrolling away from the step you are on. Same scaling, same ticks as the list
 * above; nothing is folded here, because you opened it to see everything.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IngredientsSheet(
    recipe: Recipe,
    servings: Int?,
    factor: Double,
    onToggle: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            item(key = "sheet-header") {
                Text(
                    stringResource(R.string.recipe_ingredients),
                    style = MaterialTheme.typography.headlineSmall,
                )
                recipe.servingsText(count = servings, factor = factor)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            ingredientLines(
                ingredients = recipe.ingredients,
                checked = recipe.checkedIngredients,
                factor = factor,
                foldTicked = false,
                keyPrefix = "sheet",
                onToggle = onToggle,
                onUnfold = {},
            )
        }
    }
}
