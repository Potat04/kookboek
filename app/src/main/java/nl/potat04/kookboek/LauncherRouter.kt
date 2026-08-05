package nl.potat04.kookboek

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * What the launcher icons actually point at. It draws nothing and is gone before you see
 * the app.
 *
 * The icon follows the palette through one `activity-alias` per palette (see
 * [applyLauncherIcon]), and switching means disabling the others. Android removes any task
 * that is **rooted** at a component it has just disabled — `DONT_KILL_APP` protects the
 * process, not the task. Point the aliases straight at [MainActivity] and the task you are
 * standing in is rooted at whichever alias you launched from, so changing the palette
 * throws your own session away: the app drops to the background and its card is gone from
 * Recents, exactly as if it had crashed.
 *
 * Doing the switch later, once the app is off screen, only moves when you notice.
 *
 * So the aliases point here, and this starts [MainActivity] in a task of its own. With no
 * affinity of its own ([android:taskAffinity] is empty in the manifest) this activity gets
 * a throwaway task that is finished immediately and kept out of Recents, while the task you
 * keep is rooted at [MainActivity], where no alias can reach it.
 *
 * It wears the same theme as [MainActivity], so the paper-coloured window that Android puts
 * up while the app starts is the same one either way and the hop is invisible.
 */
class LauncherRouter : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // NEW_TASK and nothing else: an existing session is brought forward as it was,
        // rather than being cleared and started over.
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }
}
