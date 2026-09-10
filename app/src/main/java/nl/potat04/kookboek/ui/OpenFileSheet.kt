package nl.potat04.kookboek.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.potat04.kookboek.R
import nl.potat04.kookboek.data.IncomingRecipes
import nl.potat04.kookboek.data.Recipe
import nl.potat04.kookboek.data.RecipeJson
import java.io.File

/** Where the sheet for an opened `.kookboek` file has got to. */
sealed interface OpenState {
    data object Reading : OpenState

    /**
     * The file was read and nothing has been stored yet. [existing] are the recipes
     * this phone already has under the same id — the sender's own copies coming home.
     */
    data class Ready(val incoming: IncomingRecipes, val existing: List<Recipe>) : OpenState

    data class Added(val recipes: List<Recipe>) : OpenState
    data class Failed(val error: Throwable?) : OpenState
}

/**
 * The sheet that comes up over the app that handed us the file.
 *
 * Same card as the share sheet: you are somewhere else and this should say what it has,
 * take one tap and get out of the way. Nothing is written to the cookbook until that
 * tap, because a file from a friend is not the same thing as asking for it.
 */
@Composable
fun OpenFileSheet(
    state: OpenState,
    onAdd: () -> Unit,
    onClose: () -> Unit,
    onOpen: (String?) -> Unit,
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
            // No tonalElevation, for the reason ShareSheet spells out.
            shadowElevation = 12.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(22.dp)) {
                when (state) {
                    OpenState.Reading -> Reading()
                    is OpenState.Ready -> Ready(state, onAdd, onClose)
                    is OpenState.Added -> Added(state, onClose, onOpen)
                    is OpenState.Failed -> Failed(state, onClose)
                }
            }
        }
    }
}

@Composable
private fun Reading() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            strokeWidth = 2.dp,
            modifier = Modifier.size(20.dp),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            stringResource(R.string.share_open_reading),
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
private fun Ready(state: OpenState.Ready, onAdd: () -> Unit, onClose: () -> Unit) {
    val recipes = state.incoming.recipes
    val first = recipes.firstOrNull()
    // Everything in the file is already here: this is a copy coming back, so the honest
    // offer is to replace rather than to add.
    val allKnown = state.existing.size == recipes.size && recipes.isNotEmpty()

    Column {
        Text(
            stringResource(
                if (allKnown) R.string.share_already_saved else R.string.share_open_title
            ),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            IncomingPicture(
                file = first?.imageFile?.let { state.incoming.images[it] },
                title = first?.title.orEmpty(),
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    first?.title?.ifBlank { null } ?: stringResource(R.string.recipe_untitled),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (recipes.size > 1) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        pluralStringResource(
                            R.plurals.share_open_count, recipes.size, recipes.size
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onClose) { Text(stringResource(R.string.action_cancel)) }
            Spacer(Modifier.width(6.dp))
            Button(onClick = onAdd, enabled = recipes.isNotEmpty()) {
                Text(
                    stringResource(
                        if (allKnown) R.string.share_open_replace else R.string.share_open_add
                    )
                )
            }
        }
    }
}

@Composable
private fun Added(state: OpenState.Added, onClose: () -> Unit, onOpen: (String?) -> Unit) {
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
                stringResource(R.string.share_saved),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (state.recipes.size == 1) {
                state.recipes.single().title.ifBlank { stringResource(R.string.recipe_untitled) }
            } else {
                pluralStringResource(
                    R.plurals.share_open_count, state.recipes.size, state.recipes.size
                )
            },
            style = MaterialTheme.typography.titleLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onClose) { Text(stringResource(R.string.action_done)) }
            Spacer(Modifier.width(6.dp))
            Button(onClick = { onOpen(state.recipes.singleOrNull()?.id) }) {
                Text(stringResource(R.string.action_open))
            }
        }
    }
}

@Composable
private fun Failed(state: OpenState.Failed, onClose: () -> Unit) {
    Column {
        Text(
            stringResource(R.string.share_failed_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(
                when (state.error) {
                    is RecipeJson.DecodeError.NewerVersion -> R.string.share_open_newer
                    else -> R.string.share_open_failed
                }
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = onClose) { Text(stringResource(R.string.action_close)) }
        }
    }
}

/**
 * A picture that is still in the cache and not in the [nl.potat04.kookboek.data.ImageStore]
 * yet, so it cannot go through [RecipeImage]. Falls back to the same serif initial.
 */
@Composable
private fun IncomingPicture(file: File?, title: String, modifier: Modifier = Modifier) {
    var bitmap by remember(file) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(file) {
        bitmap = file?.let {
            withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }.getOrNull()
            }
        }
    }
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        contentAlignment = Alignment.Center,
    ) {
        val picture = bitmap
        if (picture != null) {
            Image(
                bitmap = picture,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = title.trim().take(1).uppercase().ifBlank { "?" },
                fontFamily = FontFamily.Serif,
                fontSize = 26.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
