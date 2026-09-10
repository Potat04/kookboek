package nl.potat04.kookboek.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import nl.potat04.kookboek.R
import nl.potat04.kookboek.data.AppRelease
import nl.potat04.kookboek.data.UpdateError
import nl.potat04.kookboek.data.UpdateManager
import nl.potat04.kookboek.data.UpdateState

/** Keeps the prompt above navigation; downloads belong to the application. */
@Composable
fun UpdateHost(
    updates: UpdateManager,
    content: @Composable (UpdateState, () -> Unit) -> Unit,
) {
    val state by updates.state.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf(false) }
    var promptedVersion by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    var preparingInstall by remember { mutableStateOf(false) }
    var allowed by remember { mutableStateOf(context.packageManager.canRequestPackageInstalls()) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Returning from settings only updates the button. Installation needs another tap.
        allowed = context.packageManager.canRequestPackageInstalls()
    }
    val installer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        allowed = context.packageManager.canRequestPackageInstalls()
        updates.check(manual = false)
    }
    val availableVersion = (state as? UpdateState.Available)?.release?.version
    LaunchedEffect(availableVersion) {
        if (availableVersion != null && availableVersion != promptedVersion) {
            promptedVersion = availableVersion
            open = true
        }
    }

    content(state) {
        open = true
        when (state) {
            UpdateState.Idle, UpdateState.Current, is UpdateState.Failed -> updates.check(manual = true)
            else -> Unit
        }
    }

    if (open) {
        val release = state.release()
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.updates_title)) },
            text = {
                Column(
                    Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(updateStatus(state))
                    if (release != null && release.notes.isNotBlank()) {
                        Text(release.notes, style = MaterialTheme.typography.bodyMedium)
                    }
                    when (val current = state) {
                        UpdateState.Checking -> LinearProgressIndicator(Modifier.fillMaxWidth())
                        is UpdateState.Downloading -> {
                            val progress = current.progress
                            if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                            else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        }
                        is UpdateState.Ready -> Text(stringResource(
                            if (allowed) R.string.updates_install_note else R.string.updates_permission_note,
                        ))
                        else -> Unit
                    }
                }
            },
            confirmButton = {
                when (state) {
                    is UpdateState.Available -> TextButton(onClick = updates::download) {
                        Text(stringResource(R.string.updates_download))
                    }
                    is UpdateState.Downloading -> TextButton(onClick = updates::cancel) {
                        Text(stringResource(R.string.updates_cancel_download))
                    }
                    is UpdateState.Ready -> TextButton(
                        enabled = !preparingInstall,
                        onClick = {
                            if (!context.packageManager.canRequestPackageInstalls()) {
                                try {
                                    permission.launch(Intent(
                                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        Uri.parse("package:${context.packageName}"),
                                    ))
                                } catch (_: ActivityNotFoundException) {
                                    updates.installationFailed()
                                } catch (_: SecurityException) {
                                    updates.installationFailed()
                                }
                            } else {
                                preparingInstall = true
                                scope.launch {
                                    try {
                                        val intent = updates.installIntent()
                                        if (intent != null && open && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                            installer.launch(intent)
                                        }
                                    } catch (_: ActivityNotFoundException) {
                                        updates.installationFailed()
                                    } catch (_: SecurityException) {
                                        updates.installationFailed()
                                    } finally {
                                        preparingInstall = false
                                    }
                                }
                            }
                        },
                    ) {
                        Text(stringResource(if (allowed) R.string.updates_install else R.string.updates_allow_install))
                    }
                    UpdateState.Checking -> Unit
                    else -> TextButton(onClick = { updates.check(manual = true) }) {
                        Text(stringResource(R.string.updates_check))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text(stringResource(R.string.updates_close)) }
            },
        )
    }
}

@Composable
fun UpdateSettings(state: UpdateState, onOpen: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.updates_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.updates_daily_note), style = MaterialTheme.typography.bodySmall)
        if (state != UpdateState.Idle) {
            Text(updateStatus(state), style = MaterialTheme.typography.bodyMedium)
        }
        TextButton(onClick = onOpen) {
            Text(stringResource(when (state) {
                is UpdateState.Available, is UpdateState.Downloading, is UpdateState.Ready -> R.string.updates_view
                else -> R.string.updates_check
            }))
        }
    }
}

private fun UpdateState.release(): AppRelease? = when (this) {
    is UpdateState.Available -> release
    is UpdateState.Downloading -> release
    is UpdateState.Ready -> release
    is UpdateState.Failed -> release
    else -> null
}

@Composable
private fun updateStatus(state: UpdateState): String = when (state) {
    UpdateState.Idle -> stringResource(R.string.updates_daily_note)
    UpdateState.Checking -> stringResource(R.string.updates_checking)
    UpdateState.Current -> stringResource(R.string.updates_current)
    is UpdateState.Available -> stringResource(R.string.updates_available, state.release.version)
    is UpdateState.Downloading -> stringResource(R.string.updates_downloading, state.release.version)
    is UpdateState.Ready -> stringResource(R.string.updates_ready, state.release.version)
    is UpdateState.Failed -> stringResource(when (state.reason) {
        UpdateError.CHECK -> R.string.updates_error_check
        UpdateError.DOWNLOAD -> R.string.updates_error_download
        UpdateError.INVALID_APK -> R.string.updates_error_invalid
        UpdateError.SIGNATURE -> R.string.updates_error_signature
        UpdateError.INSTALL -> R.string.updates_error_install
    })
}
