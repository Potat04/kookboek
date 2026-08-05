package nl.potat04.kookboek.ui.theme

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.random.Random

/**
 * A faint speckle tiled over the background so large flat areas read as paper
 * instead of as a flat colour. Generated once at runtime — a fixed seed keeps it
 * identical on every launch, and it costs no asset in the APK.
 */
@Composable
fun rememberGrain(): ShaderBrush {
    val bitmap = remember {
        val size = 128
        val pixels = IntArray(size * size)
        val random = Random(20260805)
        for (i in pixels.indices) {
            // Mostly clear, with the occasional darker fleck: that is what fibre looks like.
            val alpha = when (random.nextInt(100)) {
                in 0..79 -> 0
                in 80..95 -> random.nextInt(6, 14)
                else -> random.nextInt(14, 30)
            }
            pixels[i] = alpha shl 24
        }
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            .apply { setPixels(pixels, 0, size, 0, 0, size, size) }
            .asImageBitmap()
    }
    return remember(bitmap) {
        ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated))
    }
}

fun Modifier.paperGrain(brush: ShaderBrush, alpha: Float = 0.55f): Modifier =
    drawBehind { drawRect(brush = brush, alpha = alpha) }

/** The page every screen sits on. */
@Composable
fun PaperBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val grain = rememberGrain()
    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .paperGrain(grain),
    ) {
        content()
        // The app draws edge to edge, so scrolling content would otherwise slide up
        // into the clock. An opaque strip of the same paper keeps the status bar readable.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(MaterialTheme.colorScheme.background)
                .paperGrain(grain),
        )
    }
}
