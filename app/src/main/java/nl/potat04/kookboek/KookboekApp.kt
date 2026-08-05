package nl.potat04.kookboek

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import nl.potat04.kookboek.data.ImageStore
import nl.potat04.kookboek.data.RecipeRepository
import nl.potat04.kookboek.data.RecipeStore
import nl.potat04.kookboek.data.SettingsStore

class KookboekApp : Application() {

    /** Imports outlive the sheet that started them, so they run here and not in a ViewModel. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var repository: RecipeRepository
        private set

    /**
     * Lives here because both windows need it: the share sheet floating over the
     * browser has to come up in the same palette as the app itself.
     */
    lateinit var settings: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        repository = RecipeRepository(RecipeStore(this, scope), ImageStore(this))
        scope.launch { repository.load() }

        // The icon on the home screen follows the palette. Watching the flow rather than
        // hanging this off the settings screen means it is also put right in cases the
        // screen never sees: a restore from backup brings the preferences along while the
        // manifest's default alias is still the enabled one.
        //
        // Safe to do while the app is on screen only because the aliases point at
        // LauncherRouter and not at MainActivity — read that class before changing this.
        scope.launch {
            settings.settings
                .map { it.palette }
                .distinctUntilChanged()
                .collect { applyLauncherIcon(it) }
        }
    }
}
