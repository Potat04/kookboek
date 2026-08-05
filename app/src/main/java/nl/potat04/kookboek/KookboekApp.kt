package nl.potat04.kookboek

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    }
}
