package nl.potat04.kookboek.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import nl.potat04.kookboek.KookboekApp

/**
 * The daily backup, as WorkManager sees it. All the thinking is in [AutoBackup]; this
 * only decides what to tell the scheduler.
 *
 * It reaches back to [KookboekApp] for the repository because a worker may be the
 * reason the process started at all, and building a second [RecipeStore] would mean two
 * Room instances on one database file.
 */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? KookboekApp ?: return Result.success()
        return when (AutoBackup.run(applicationContext, app.repository, app.settings)) {
            is BackupOutcome.Written -> Result.success()
            // Nothing to retry: there is no folder, or it is not ours any more. Both
            // are answered on the settings screen, not by trying again in ten minutes.
            BackupOutcome.NoFolder, BackupOutcome.NoPermission -> Result.success()
            BackupOutcome.Failed -> Result.retry()
        }
    }
}
