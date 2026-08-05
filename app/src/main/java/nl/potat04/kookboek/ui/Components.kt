package nl.potat04.kookboek.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.potat04.kookboek.data.ImageStore

val LocalImageStore = staticCompositionLocalOf<ImageStore?> { null }

@Composable
fun ProvideImageStore(store: ImageStore, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalImageStore provides store, content = content)

@Composable
fun rememberRecipeImage(fileName: String?): ImageBitmap? {
    val store = LocalImageStore.current
    var bitmap by remember(fileName) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(fileName, store) {
        bitmap = if (fileName != null && store != null) store.load(fileName) else null
    }
    return bitmap
}

/**
 * A recipe picture, or — when there is none — the first letter set in serif on a
 * tinted block. Missing photos should still look deliberate.
 */
@Composable
fun RecipeImage(
    fileName: String?,
    title: String,
    modifier: Modifier = Modifier,
    corner: Dp = 6.dp,
) {
    val bitmap = rememberRecipeImage(fileName)
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
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

/** A hairline, like a rule printed on a page. */
@Composable
fun Rule(modifier: Modifier = Modifier) = HorizontalDivider(
    modifier = modifier.height(1.dp),
    color = MaterialTheme.colorScheme.outlineVariant,
)
