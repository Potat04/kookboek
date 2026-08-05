package nl.potat04.kookboek.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.potat04.kookboek.data.ParseQuality
import nl.potat04.kookboek.data.Recipe

sealed interface ShareState {
    data object Working : ShareState
    data class Done(val recipe: Recipe, val isNew: Boolean) : ShareState
    data class Failed(val reason: String) : ShareState
}

/**
 * Deliberately small: you are in the middle of browsing, and this should confirm
 * and get out of the way — while still being honest about what it managed to read.
 */
@Composable
fun ShareSheet(
    state: ShareState,
    onClose: () -> Unit,
    onOpen: (String) -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 12.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(22.dp)) {
                when (state) {
                    ShareState.Working -> Working()
                    is ShareState.Failed -> Failed(state.reason, onClose)
                    is ShareState.Done -> Done(state, onClose, onOpen)
                }
            }
        }
    }
}

@Composable
private fun Working() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            strokeWidth = 2.dp,
            modifier = Modifier.size(20.dp),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text("Recept ophalen…", style = MaterialTheme.typography.titleLarge)
            Text(
                "Even de pagina lezen",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Failed(reason: String, onClose: () -> Unit) {
    Column {
        Text("Niet gelukt", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            reason,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = onClose) { Text("Sluiten") }
        }
    }
}

@Composable
private fun Done(state: ShareState.Done, onClose: () -> Unit, onOpen: (String) -> Unit) {
    val recipe = state.recipe
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = logoPainter(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (state.isNew) "Bewaard in Kookboek" else "Stond er al in",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            RecipeImage(
                fileName = recipe.imageFile,
                title = recipe.title,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    recipe.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    summary(recipe),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (recipe.quality == ParseQuality.FULL)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error,
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onClose) { Text("Klaar") }
            Spacer(Modifier.width(6.dp))
            Button(onClick = { onOpen(recipe.id) }) { Text("Openen") }
        }
    }
}

private fun summary(recipe: Recipe): String = when (recipe.quality) {
    ParseQuality.FULL ->
        "${recipe.ingredients.size} ingrediënten · ${recipe.steps.size} stappen"
    ParseQuality.PARTIAL -> when {
        recipe.ingredients.isEmpty() -> "Geen ingrediënten gevonden — check het even"
        else -> "Geen stappen gevonden — check het even"
    }
    ParseQuality.LINK_ONLY -> "Alleen de link bewaard"
}
