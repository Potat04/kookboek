package nl.potat04.kookboek.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.potat04.kookboek.R
import nl.potat04.kookboek.data.PaletteId
import nl.potat04.kookboek.data.Settings
import nl.potat04.kookboek.data.TextSize
import nl.potat04.kookboek.data.ThemeMode
import nl.potat04.kookboek.ui.theme.Palettes
import nl.potat04.kookboek.ui.theme.controlOutline
import nl.potat04.kookboek.ui.theme.paletteFor

/**
 * Everything here applies the moment you tap it. There is no Save button on purpose:
 * you are choosing how something looks, and the only useful preview is the real thing.
 */
@Composable
fun SettingsScreen(
    settings: Settings,
    onPalette: (PaletteId) -> Unit,
    onMode: (ThemeMode) -> Unit,
    onTextSize: (TextSize) -> Unit,
    onLanguage: (AppLanguage) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onAutoBackup: (Boolean) -> Unit,
    onBackupFolder: (String?) -> Unit,
    onExport: (Uri) -> Unit,
    onRestore: (Uri) -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    val language = context.appLanguage()

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 40.dp,
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(24.dp))

        Section(R.string.settings_palette)
        PaletteGrid(selected = settings.palette, onSelect = onPalette)
        Spacer(Modifier.height(26.dp))

        Section(R.string.settings_mode)
        ChoiceRow(
            options = ThemeMode.entries,
            selected = settings.mode,
            label = { stringResource(it.labelRes) },
            onSelect = onMode,
        )
        Spacer(Modifier.height(26.dp))

        Section(R.string.settings_text_size)
        ChoiceRow(
            options = TextSize.entries,
            selected = settings.textSize,
            label = { stringResource(it.labelRes) },
            onSelect = onTextSize,
        )
        Spacer(Modifier.height(14.dp))
        TypeSample()
        Spacer(Modifier.height(26.dp))

        Section(R.string.backup_feel)
        SwitchRow(
            title = stringResource(R.string.backup_haptics),
            body = stringResource(R.string.backup_haptics_body),
            checked = settings.hapticFeedback,
            onChange = onHaptics,
        )
        Spacer(Modifier.height(26.dp))

        Section(R.string.settings_language)
        ChoiceRow(
            options = AppLanguage.entries,
            selected = language,
            label = { stringResource(it.labelRes) },
            onSelect = onLanguage,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.settings_language_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(30.dp))
        Section(R.string.backup_section)
        BackupSection(
            settings = settings,
            onAutoBackup = onAutoBackup,
            onBackupFolder = onBackupFolder,
            onExport = onExport,
            onRestore = onRestore,
            onDeleted = onDeleted,
        )

        Spacer(Modifier.height(30.dp))
        Text(
            stringResource(R.string.settings_version, appVersion(context)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Section(labelRes: Int) {
    Text(
        stringResource(labelRes),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

/**
 * Each swatch is a scrap of the palette it stands for: its own paper, its own ink,
 * its own accent, and a sliver of its night side down the right edge. Reading the
 * name off a list would tell you nothing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaletteGrid(selected: PaletteId, onSelect: (PaletteId) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Palettes.forEach { palette ->
            PaletteSwatch(
                paletteId = palette.id,
                selected = palette.id == selected,
                onClick = { onSelect(palette.id) },
            )
        }
    }
}

@Composable
private fun PaletteSwatch(paletteId: PaletteId, selected: Boolean, onClick: () -> Unit) {
    val palette = paletteFor(paletteId)
    val shape = RoundedCornerShape(8.dp)
    val edge = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outline

    Column(
        Modifier
            // The name sets the width. "Sinaasappel" is one long word that cannot wrap,
            // so a fixed 102dp breaks it mid-word at the larger text sizes — but a bare
            // minimum is no good either: inside a FlowRow the column would then take the
            // whole row and the grid becomes a list. IntrinsicSize.Min asks the label how
            // wide it needs to be; the 102dp floor keeps "Inkt" from being a stub.
            .width(IntrinsicSize.Min)
            .widthIn(min = 102.dp)
            .clip(shape)
            // selectable rather than clickable: this is one choice out of six, and a
            // screen reader should say so instead of announcing six buttons.
            .selectable(selected = selected, onClick = onClick)
            .border(if (selected) 2.dp else 1.dp, edge, shape),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(58.dp)
                .background(palette.light.background),
        ) {
            // The night side, so a dark-mode reader can see what they are choosing too.
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxWidth(0.34f)
                    .height(58.dp)
                    .background(palette.dark.background),
            )
            Text(
                "Aa",
                fontFamily = FontFamily.Serif,
                fontSize = 22.sp,
                color = palette.light.onBackground,
                modifier = Modifier.padding(start = 10.dp, top = 8.dp),
            )
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Dot(palette.light.primary)
                Dot(palette.dark.primary)
            }
        }
        Text(
            stringResource(palette.labelRes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun Dot(color: Color) = Box(
    Modifier
        .size(10.dp)
        .clip(CircleShape)
        .background(color),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option), style = MaterialTheme.typography.labelLarge) },
                shape = MaterialTheme.shapes.small,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

/**
 * The three sizes that actually carry the app: a step, a summary line and a caption.
 * Picking a text size blind is guesswork, and the summary line is exactly the one
 * people squint at.
 *
 * The tick box and the step badge are in here too, drawn the way the recipe screen
 * draws them. Both are sized off the type scale, so the thing you are choosing is not
 * only how big the words are but how big the targets under your finger become.
 */
@Composable
private fun TypeSample() {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                stringResource(R.string.settings_sample_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_sample_summary),
                // Set exactly as a real recipe's headnote is, or the preview would be
                // showing you something the app does not do.
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            SampleIngredient()
            Spacer(Modifier.height(4.dp))
            SampleStep()
            Spacer(Modifier.height(8.dp))
            // Built from the same resources a real card uses, so the sample cannot
            // drift away from what it is a sample of. No site name: a real domain in
            // here reads as an endorsement, and a made-up one is somebody's site.
            val meta = listOf(
                stringResource(R.string.recipe_time_minutes, SAMPLE_MINUTES),
                pluralStringResource(R.plurals.recipe_servings_count, SAMPLE_SERVINGS, SAMPLE_SERVINGS),
            ).joinToString("  ·  ")
            Text(
                meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** An ingredient line as the recipe screen sets it, tick box and all. Not tickable here. */
@Composable
private fun SampleIngredient() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = false,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        Text(
            stringResource(R.string.backup_sample_ingredient),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 14.dp),
        )
    }
}

/** A step with its number, the badge sized off the type scale exactly as it is in a recipe. */
@Composable
private fun SampleStep() {
    Row {
        val badge = with(LocalDensity.current) {
            (MaterialTheme.typography.titleMedium.fontSize.toDp() * 1.75f).coerceAtLeast(28.dp)
        }
        Box(
            Modifier
                .size(badge)
                .border(1.dp, MaterialTheme.colorScheme.controlOutline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("1", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.width(14.dp))
        Text(
            stringResource(R.string.settings_sample_step),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

private const val SAMPLE_MINUTES = 30
private const val SAMPLE_SERVINGS = 4

private fun appVersion(context: android.content.Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull() ?: "?"
