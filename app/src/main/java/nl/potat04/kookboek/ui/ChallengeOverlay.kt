package nl.potat04.kookboek.ui

import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.potat04.kookboek.R
import nl.potat04.kookboek.data.ChallengeStage

/**
 * Draws the bot check that [nl.potat04.kookboek.data.PageFetcher] is working through.
 *
 * Every screen that can start an import puts this at the top of its content. It is
 * nothing at all until a site asks for a check.
 *
 * Out of sight is the normal case and lasts a second or two. The WebView sits in a
 * zero-sized clipping box, showing nothing and catching none of the taps meant for the
 * screen underneath, while it is still laid out at a real size so that revealing it
 * costs no relayout. When Cloudflare says its check has gone interactive, the fetcher
 * reveals it and you finish it by hand.
 *
 * The WebView belongs to the fetcher, which destroys it when the fetch ends. This only
 * ever puts it in the view tree and takes it back out.
 */
@Composable
fun ChallengeOverlay() {
    val staged by ChallengeStage.current.collectAsStateWithLifecycle()
    val challenge = staged ?: return
    val visible = challenge.visible

    Box(if (visible) Modifier.fillMaxSize() else Modifier.size(0.dp).clipToBounds()) {
        if (visible) {
            Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
                Column(Modifier.systemBarsPadding().padding(20.dp)) {
                    Text(
                        stringResource(R.string.challenge_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.challenge_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        // One call site on purpose: moving the WebView between two of them would hand
        // AndroidView a view that still has a parent.
        AndroidView(
            factory = { challenge.web },
            modifier = if (visible) {
                Modifier.fillMaxSize().systemBarsPadding().padding(top = 110.dp)
            } else {
                Modifier.requiredSize(OFFSCREEN_WIDTH, OFFSCREEN_HEIGHT)
            },
            onRelease = { (it.parent as? ViewGroup)?.removeView(it) },
        )
    }
}

/** Roughly a phone screen: big enough that the check believes it has somewhere to draw. */
private val OFFSCREEN_WIDTH = 360.dp
private val OFFSCREEN_HEIGHT = 720.dp
