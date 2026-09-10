package nl.potat04.kookboek.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import nl.potat04.kookboek.R

/**
 * One picture over everything, on dark ink: pinch to zoom, drag to pan, tap to leave.
 * A [Dialog] rather than a Box in the screen, so it also covers the system bars and
 * Back closes it without the screen having to know.
 */
@Composable
internal fun FullScreenPicture(fileName: String, onDismiss: () -> Unit) {
    val bitmap = rememberRecipeImage(fileName)
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        // At natural size there is nothing to pan towards; snapping back keeps the
        // picture from drifting off after a pinch that ended at 1x.
        offset = if (scale <= 1f) Offset.Zero else offset + pan
    }
    val close = stringResource(R.string.recipe_picture_close)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 1f))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onDismiss() },
                        onDoubleTap = {
                            scale = if (scale > 1f) 1f else 2.5f
                            offset = Offset.Zero
                        },
                    )
                }
                .transformable(transform)
                .semantics { contentDescription = close; role = Role.Button },
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                )
            }
        }
    }
}

/**
 * A photographed recipe card, shown whole under the notes. Not cropped like the
 * hero picture: a card is portrait more often than not, and the handwriting on it
 * is the point.
 */
@Composable
internal fun AttachmentPicture(fileName: String, onOpen: () -> Unit) {
    val bitmap = rememberRecipeImage(fileName) ?: return
    val ratio = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
    val shape = MaterialTheme.shapes.medium
    val open = stringResource(R.string.recipe_picture_open)
    Image(
        bitmap = bitmap,
        contentDescription = stringResource(R.string.recipe_attachment),
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ratio.coerceIn(0.5f, 2f))
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClickLabel = open, onClick = onOpen),
    )
}
