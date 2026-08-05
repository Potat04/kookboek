package nl.potat04.kookboek

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.async
import nl.potat04.kookboek.data.ImportResult
import nl.potat04.kookboek.data.extractUrl
import nl.potat04.kookboek.data.normalizeUrl
import nl.potat04.kookboek.ui.ProvideImageStore
import nl.potat04.kookboek.ui.ShareSheet
import nl.potat04.kookboek.ui.ShareState
import nl.potat04.kookboek.ui.theme.KookboekTheme

/**
 * The entry point for "share a page to Kookboek".
 *
 * It floats over the browser as a small sheet: fetch, show what was found, done.
 * The import runs on the application scope, so dismissing this sheet — or the system
 * killing it — never throws away a half-saved recipe.
 */
class ShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val app = application as KookboekApp
        val url = urlFromIntent(intent)

        setContent {
            KookboekTheme {
                var state by remember { mutableStateOf<ShareState>(ShareState.Working) }

                LaunchedEffect(url) {
                    state = if (url == null) {
                        ShareState.Failed("Hier zat geen link in")
                    } else {
                        when (val result = app.scope.async { app.repository.import(url) }.await()) {
                            is ImportResult.Saved -> ShareState.Done(result.recipe, isNew = true)
                            is ImportResult.AlreadySaved -> ShareState.Done(result.recipe, isNew = false)
                            is ImportResult.Failed -> ShareState.Failed(result.reason)
                        }
                    }
                }

                ProvideImageStore(app.repository.images) {
                    ShareSheet(
                        state = state,
                        onClose = ::finish,
                        onOpen = ::openInApp,
                    )
                }
            }
        }
    }

    private fun openInApp(recipeId: String) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(EXTRA_OPEN_RECIPE, recipeId)
        )
        finish()
    }

    /** Shared text is usually "some words plus a link"; a browser "open with" gives a data URI. */
    private fun urlFromIntent(intent: Intent?): String? {
        if (intent == null) return null
        val candidate = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.dataString
        }
        return normalizeUrl(extractUrl(candidate) ?: candidate)
    }

    companion object {
        const val EXTRA_OPEN_RECIPE = "open_recipe"
    }
}
