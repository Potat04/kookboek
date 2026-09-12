package nl.potat04.kookboek

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nl.potat04.kookboek.data.ShareFiles
import nl.potat04.kookboek.ui.OpenFileSheet
import nl.potat04.kookboek.ui.OpenState
import nl.potat04.kookboek.ui.theme.KookboekTheme

/**
 * The other end of "send as Kookboek file": someone taps the file in their chat app and
 * lands here.
 *
 * Same little window over whatever they came from as [ShareActivity], and the same
 * promise — it says what is in the file and does nothing to the cookbook until asked.
 * Deliberately registered for our own file type only. Kookboek has no business in the
 * chooser for every text message on the phone.
 */
class OpenFileActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val app = application as KookboekApp
        val uri = fileFromIntent(intent)

        setContent {
            val settings by app.settings.settings.collectAsStateWithLifecycle()
            KookboekTheme(settings = settings, applySystemBars = false) {
                var state by remember { mutableStateOf<OpenState>(OpenState.Reading) }
                val scope = rememberCoroutineScope()

                LaunchedEffect(uri) {
                    state = read(uri)
                }

                OpenFileSheet(
                    state = state,
                    onAdd = {
                        val ready = state as? OpenState.Ready ?: return@OpenFileSheet
                        scope.launch {
                            // On the application scope: adding writes rows and copies
                            // pictures, and the sheet closing must not stop that.
                            app.scope.async { app.repository.addIncoming(ready.incoming) }.await()
                            state = OpenState.Added(ready.incoming.recipes)
                        }
                    },
                    onClose = ::finish,
                    onOpen = ::openInApp,
                )
            }
        }
    }

    private suspend fun read(uri: Uri?): OpenState {
        val app = application as KookboekApp
        if (uri == null) return OpenState.Failed(null)
        // Which recipes are already here is only knowable once the cookbook is read.
        app.repository.loaded.first { it }
        val result = app.repository.readFile(ShareFiles.inbox(this)) {
            contentResolver.openInputStream(uri) ?: error("nothing behind $uri")
        }
        return result.fold(
            onSuccess = { incoming ->
                OpenState.Ready(
                    incoming = incoming,
                    // The bin counts as already here: adding the file writes the row
                    // back into the library, and calling that "Add" would be a lie
                    // about a recipe the reader threw out.
                    existing = incoming.recipes.mapNotNull {
                        app.repository.byId(it.id) ?: app.repository.deletedById(it.id)
                    },
                )
            },
            onFailure = { OpenState.Failed(it) },
        )
    }

    private fun openInApp(recipeId: String?) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .apply { recipeId?.let { putExtra(ShareActivity.EXTRA_OPEN_RECIPE, it) } }
        )
        finish()
    }

    /** Tapping the file gives a VIEW; sending it from a chat app gives a SEND. */
    private fun fileFromIntent(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else -> intent?.data
    }
}
