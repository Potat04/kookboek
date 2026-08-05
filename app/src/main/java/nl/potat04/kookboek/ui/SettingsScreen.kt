package nl.potat04.kookboek.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
            .width(102.dp)
            .clip(shape)
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
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_sample_step),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_sample_meta),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun appVersion(context: android.content.Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull() ?: "?"
