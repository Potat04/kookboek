package nl.potat04.kookboek.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/** How a run of the daily backup ended. */
sealed interface BackupOutcome {
    data class Written(val name: String, val recipes: Int) : BackupOutcome
    /** Switched on without a folder to write to — nothing to do, and nothing wrong. */
    data object NoFolder : BackupOutcome
    /** The grant on the folder is gone. The switch is turned off; the row says why. */
    data object NoPermission : BackupOutcome
    data object Failed : BackupOutcome
}

/**
 * One backup a day into the folder the reader picked, keeping the last week.
 *
 * This is the only scheduled job in the app, and the only reason WorkManager is here:
 * a backup has to happen on days nobody opens the app, which is precisely the day you
 * find out you needed one.
 */
object AutoBackup {

    private const val TAG = "AutoBackup"

    /** Unique, so re-scheduling replaces rather than stacks. */
    const val WORK_NAME = "auto-backup"

    private const val INTERVAL_HOURS = 24L
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    /**
     * One run at a time in this process. The worker and the catch-up at launch can come
     * up together, and [BackupFolder.write] deletes today's file before writing it — two
     * runs across each other leave a truncated file, or the "(1)" copy the retention
     * never prunes.
     */
    private val running = Mutex()

    /** Enqueues the daily run, or cancels it when there is nothing to back up to. */
    fun schedule(context: Context, settings: Settings) {
        val work = WorkManager.getInstance(context)
        if (!settings.autoBackup || settings.backupFolder == null) {
            work.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<BackupWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
            // Writing a few megabytes is not worth waking a phone that is nearly flat.
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        // UPDATE and not REPLACE: changing the folder should not push the next run a
        // whole day out. The worker reads the folder when it runs, so the schedule
        // itself never goes stale.
        work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /**
     * Runs a backup now: today's file into the tree, then the older ones beyond a week.
     *
     * Waits for the cookbook to be read off disk first. The worker can start the process
     * on a day nobody opened the app, and [RecipeRepository.recipes] is empty until the
     * first read lands — backing that up would write an empty cookbook over a good one.
     */
    suspend fun run(context: Context, repo: RecipeRepository, store: SettingsStore): BackupOutcome =
        running.withLock { write(context, repo, store) }

    private suspend fun write(
        context: Context,
        repo: RecipeRepository,
        store: SettingsStore,
    ): BackupOutcome {
        val stored = store.settings.value.backupFolder ?: return BackupOutcome.NoFolder
        val tree = runCatching { Uri.parse(stored) }.getOrNull() ?: return BackupOutcome.NoFolder

        if (!BackupFolder.hasPermission(context, tree)) {
            // Never claim to be backing up when we cannot. The switch goes off and the
            // settings row says the folder is gone.
            Log.w(TAG, "the backup folder is no longer ours to write to")
            store.setAutoBackup(false)
            return BackupOutcome.NoPermission
        }

        repo.loaded.first { it }

        var recipes = 0
        val name = Backup.fileName(System.currentTimeMillis())
        val written = BackupFolder.write(context, tree, name) { out ->
            recipes = Backup(repo).write(out)
        }
        if (!written) return BackupOutcome.Failed

        BackupFolder.prune(context, tree, Backup.KEEP)
        store.setLastBackupAt(System.currentTimeMillis())
        return BackupOutcome.Written(name, recipes)
    }

    /**
     * Catches up at startup when the last backup is more than a day old.
     *
     * WorkManager is good about phones that were off, but not perfect, and this costs
     * one comparison on a launch that already read the cookbook.
     */
    suspend fun catchUp(context: Context, repo: RecipeRepository, store: SettingsStore) {
        val settings = store.settings.value
        if (!settings.autoBackup || settings.backupFolder == null) return
        if (System.currentTimeMillis() - settings.lastBackupAt < DAY_MS) return
        running.withLock {
            // The worker may have been the one holding the lock, in which case today is
            // already backed up and there is nothing left to catch up with.
            if (System.currentTimeMillis() - store.settings.value.lastBackupAt < DAY_MS) return
            write(context, repo, store)
        }
    }
}
