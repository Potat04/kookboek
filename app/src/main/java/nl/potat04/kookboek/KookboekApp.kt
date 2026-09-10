package nl.potat04.kookboek

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import nl.potat04.kookboek.data.AutoBackup
import nl.potat04.kookboek.data.ImageStore
import nl.potat04.kookboek.data.PageFetcher
import nl.potat04.kookboek.data.RecipeRepository
import nl.potat04.kookboek.data.RecipeStore
import nl.potat04.kookboek.data.SettingsStore
import nl.potat04.kookboek.data.ShareFiles

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
        repository = RecipeRepository(
            RecipeStore(this, scope),
            ImageStore(this),
            PageFetcher(this),
            ShareFiles.pages(this),
        )
        scope.launch {
            repository.load()
            // The bin is a bin and not an archive. Thirty days is long enough to notice
            // you deleted the wrong thing and short enough that it stays a bin.
            repository.purgeDeleted(BIN_DAYS_MS)
            // WorkManager is good about phones that spent a week switched off, not
            // perfect. This costs one comparison on a launch that just read the disk.
            AutoBackup.catchUp(this@KookboekApp, repository, settings)
        }

        // The daily backup is scheduled from the settings that drive it, not from the
        // screen that changes them: switching the folder, or restoring preferences onto
        // a new phone, both have to land on the scheduler.
        scope.launch {
            settings.settings
                .map { it.autoBackup to it.backupFolder }
                .distinctUntilChanged()
                .collect { AutoBackup.schedule(this@KookboekApp, settings.settings.value) }
        }

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

    private companion object {
        const val BIN_DAYS_MS = 30L * 24 * 60 * 60 * 1000
    }
}
