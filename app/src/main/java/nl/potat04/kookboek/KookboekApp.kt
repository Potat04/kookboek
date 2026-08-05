package nl.potat04.kookboek

import android.app.Activity
import android.app.Application
import android.os.Bundle
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

    /** How many of our activities are on screen. Zero means the app has been left. */
    private var startedActivities = 0

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        repository = RecipeRepository(RecipeStore(this, scope), ImageStore(this))
        scope.launch { repository.load() }
        registerActivityLifecycleCallbacks(LauncherIconSync())
    }

    /**
     * Brings the launcher icon in line with the chosen palette, but only once the app is
     * off screen.
     *
     * Switching the icon means disabling an `activity-alias`, and Android removes any task
     * that is *rooted* at a disabled component. `DONT_KILL_APP` saves the process, not the
     * task. Start Kookboek from the home screen and the task is rooted at the alias — so
     * doing this while the settings screen is open drops the whole app to the background
     * under the reader's finger, a moment after the tap. Which is exactly what it did.
     *
     * Waiting costs nothing anyone can see: launchers cache the icon anyway, so it was
     * never instant.
     *
     * Doing it on the way out also covers the cases the settings screen never hears about,
     * such as a restore from backup, where the preferences arrive while the manifest's
     * default alias is still the enabled one.
     */
    private inner class LauncherIconSync : ActivityLifecycleCallbacks {

        override fun onActivityStarted(activity: Activity) {
            startedActivities++
        }

        override fun onActivityStopped(activity: Activity) {
            startedActivities--
            // A rotation, or the activity restart that a language change causes, stops one
            // instance and starts the next. That is not the app being left.
            if (startedActivities > 0 || activity.isChangingConfigurations) return
            val palette = settings.settings.value.palette
            scope.launch { applyLauncherIcon(palette) }
        }

        override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
