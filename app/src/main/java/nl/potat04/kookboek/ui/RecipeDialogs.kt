package nl.potat04.kookboek.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import nl.potat04.kookboek.R
import nl.potat04.kookboek.ui.theme.controlOutline

/**
 * "Made it": stamps today and takes one optional line for the notes. No rating, no
 * count. A paper cookbook gets a pencilled date in the margin and that is enough.
 */
@Composable
internal fun MadeItDialog(onConfirm: (note: String?) -> Unit, onDismiss: () -> Unit) {
    var note by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.recipe_made_it), style = MaterialTheme.typography.titleLarge)
        },
        text = {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = { Text(stringResource(R.string.recipe_made_hint)) },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.controlOutline,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(note.trim().ifEmpty { null }) }) {
                Text(stringResource(R.string.recipe_made_it))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

/**
 * Fetching again throws away hand-edited ingredients and steps. That is the one
 * thing on this screen the snackbar cannot undo, so it gets asked first.
 */
@Composable
internal fun RefetchEditedDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.recipe_refetch_edited_title),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = { Text(stringResource(R.string.recipe_refetch_edited_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.recipe_refetch_anyway)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}
