package nl.potat04.kookboek.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.potat04.kookboek.R
import nl.potat04.kookboek.data.Backup
import nl.potat04.kookboek.data.BackupFolder
import nl.potat04.kookboek.data.Settings
import java.text.DateFormat
import java.util.Date

/**
 * Export, restore, the daily backup and the bin.
 *
 * The file pickers live here rather than in the activity: every one of them hands back
 * a Uri that is used once and thrown away, and there is nothing for the rest of the app
 * to know about. What the reader points them at — Drive, Nextcloud, a stick — is the
 * system's business and never ours.
 */
@Composable
fun BackupSection(
    settings: Settings,
    onAutoBackup: (Boolean) -> Unit,
    onBackupFolder: (String?) -> Unit,
    onExport: (Uri) -> Unit,
    onRestore: (Uri) -> Unit,
    onDeleted: () -> Unit,
) {
    val context = LocalContext.current

    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(MIME_ZIP)
    ) { uri -> uri?.let(onExport) }

    val restorer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onRestore) }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            // Without this the grant dies with the activity, and the daily job would
            // wake up to a folder it cannot open.
            val kept = runCatching {
                context.contentResolver.takePersistableUriPermission(uri, BackupFolder.PERSIST_FLAGS)
            }.isSuccess
            if (kept) onBackupFolder(uri.toString())
        }
    }

    ActionRow(
        title = stringResource(R.string.backup_export),
        body = stringResource(R.string.backup_export_body),
        onClick = { exporter.launch(Backup.fileName(System.currentTimeMillis())) },
    )
    Spacer(Modifier.height(18.dp))

    ActionRow(
        title = stringResource(R.string.backup_restore),
        body = stringResource(R.string.backup_restore_body),
        onClick = { restorer.launch(BackupFolder.ZIP_TYPES) },
    )
    Spacer(Modifier.height(18.dp))

    SwitchRow(
        title = stringResource(R.string.backup_auto),
        body = stringResource(R.string.backup_auto_body),
        checked = settings.autoBackup,
        // Nothing to write to yet, so the switch waits for a folder rather than
        // promising a backup that cannot happen.
        enabled = settings.backupFolder != null,
        onChange = onAutoBackup,
    )
    Spacer(Modifier.height(10.dp))
    FolderRow(settings = settings, onPick = { folderPicker.launch(null) })
    Spacer(Modifier.height(18.dp))

    ActionRow(
        title = stringResource(R.string.backup_deleted),
        body = stringResource(R.string.backup_deleted_body),
        onClick = onDeleted,
    )
}

/** The chosen folder, whether we can still reach it, and when it last got a file. */
@Composable
private fun FolderRow(settings: Settings, onPick: () -> Unit) {
    val name = rememberFolderName(settings.backupFolder)
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                name.value ?: stringResource(R.string.backup_folder_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val note = when {
                settings.backupFolder != null && name.value == null ->
                    stringResource(R.string.backup_folder_lost)
                settings.lastBackupAt > 0 ->
                    stringResource(R.string.backup_last, dayText(settings.lastBackupAt))
                else -> stringResource(R.string.backup_last_never)
            }
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onPick) {
            Text(
                stringResource(
                    if (settings.backupFolder == null) R.string.backup_folder_pick
                    else R.string.backup_folder_change
                )
            )
        }
    }
}

/**
 * The folder's name as the picker showed it, or null when the grant is gone. Read off
 * the provider rather than parsed out of the Uri: a tree id is not a name, and a folder
 * that has been renamed should read as its new name.
 */
@Composable
private fun rememberFolderName(stored: String?): State<String?> {
    val context = LocalContext.current
    return produceState<String?>(initialValue = null, stored, context) {
        val uri = stored?.let { runCatching { Uri.parse(it) }.getOrNull() }
        // Off the main thread: asking a provider for a name is a query, and the
        // provider may well be a cloud client that has to think about it.
        value = uri?.let { withContext(Dispatchers.IO) { BackupFolder.displayName(context, it) } }
    }
}

/** A line of settings you tap to do something, with the sentence that explains it. */
@Composable
internal fun ActionRow(title: String, body: String?, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (body != null) {
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** The whole row toggles, not just the switch — a wet finger is not a precise one. */
@Composable
internal fun SwitchRow(
    title: String,
    body: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onChange,
            )
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.onBackground
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (body != null) {
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            // null: the row above already carries the gesture and the semantics.
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

/**
 * A date the way the reader writes dates. [DateFormat] follows `Locale.getDefault()`,
 * which is the app locale once LocaleManager has been told — so this changes language
 * with the rest of the app and needs no string of its own.
 */
internal fun dayText(millis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

private const val MIME_ZIP = "application/zip"
