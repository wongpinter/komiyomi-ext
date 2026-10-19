// KMK -->
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package eu.kanade.tachiyomi.ui.ebook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.ebook.EpubStore.EbookReaderDefaults
import eu.kanade.tachiyomi.ui.ebook.EpubStore.ShelfSettings
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.roundToInt

@Composable
internal fun EbookSettingsContent(
    readerDefaults: EbookReaderDefaults,
    shelfSettings: ShelfSettings,
    keepScreenOn: Boolean,
    onReaderDefaultsChange: (EbookReaderDefaults) -> Unit,
    onShelfSettingsChange: (ShelfSettings) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var currentReaderDefaults by remember(readerDefaults) { mutableStateOf(readerDefaults) }
    var currentShelfSettings by remember(shelfSettings) { mutableStateOf(shelfSettings) }
    var currentKeepScreenOn by remember(keepScreenOn) { mutableStateOf(keepScreenOn) }

    fun updateReaderDefaults(updated: EbookReaderDefaults) {
        currentReaderDefaults = updated
        onReaderDefaultsChange(updated)
    }

    fun updateShelfSettings(updated: ShelfSettings) {
        currentShelfSettings = updated
        onShelfSettingsChange(updated)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(KMR.strings.ebook_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(KMR.strings.epub_library_back))
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                stringResource(KMR.strings.ebook_settings_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SettingsSection(stringResource(KMR.strings.ebook_settings_shelf)) {
                ChoiceChips(
                    title = stringResource(KMR.strings.ebook_settings_shelf_layout),
                    selected = currentShelfSettings.layout,
                    options = EpubLibraryLayout.entries.map { it.name to it.label() },
                    onSelect = { updateShelfSettings(currentShelfSettings.copy(layout = it)) },
                )
                SettingsChoiceRow(
                    title = stringResource(KMR.strings.ebook_settings_sort),
                    selected = currentShelfSettings.sort,
                    options = EpubLibrarySort.entries.map { it.name to it.label() },
                    onSelect = { selectedSort ->
                        val sort = EpubLibrarySort.entries.firstOrNull { it.name == selectedSort }
                        if (sort != null) {
                            val descending = if (sort.name == currentShelfSettings.sort) {
                                currentShelfSettings.descending
                            } else {
                                sort != EpubLibrarySort.AUTHOR && sort != EpubLibrarySort.TITLE
                            }
                            updateShelfSettings(currentShelfSettings.copy(sort = sort.name, descending = descending))
                        }
                    },
                )
                SettingsSwitchRow(
                    title = stringResource(KMR.strings.epub_library_sort_descending),
                    checked = currentShelfSettings.descending,
                    onCheckedChange = { updateShelfSettings(currentShelfSettings.copy(descending = it)) },
                )
                SettingsSlider(
                    title = stringResource(KMR.strings.ebook_settings_cover_size, currentShelfSettings.coverSize),
                    value = currentShelfSettings.coverSize.toFloat(),
                    valueRange = 96f..192f,
                    onValueChangeFinished = { size -> updateShelfSettings(currentShelfSettings.copy(coverSize = size)) },
                )
            }

            SettingsSection(stringResource(KMR.strings.ebook_settings_epub)) {
                ChoiceChips(
                    title = stringResource(KMR.strings.epub_reader_theme),
                    selected = currentReaderDefaults.epubTheme,
                    options = EpubReaderTheme.entries.map { it.name to it.label() },
                    onSelect = { updateReaderDefaults(currentReaderDefaults.copy(epubTheme = it)) },
                )
                SettingsChoiceRow(
                    title = stringResource(KMR.strings.ebook_settings_default_font),
                    selected = currentReaderDefaults.epubFontFamily ?: PUBLISHER_FONT,
                    options = listOf(PUBLISHER_FONT to stringResource(KMR.strings.ebook_publisher_font)) +
                        EpubFonts.families.map { it.name to it.name },
                    onSelect = { selectedFont ->
                        updateReaderDefaults(
                            currentReaderDefaults.copy(epubFontFamily = selectedFont.takeUnless { it == PUBLISHER_FONT }),
                        )
                    },
                )
                SettingsSlider(
                    title = stringResource(
                        KMR.strings.ebook_settings_text_size,
                        (currentReaderDefaults.epubFontSize * 100).roundToInt(),
                    ),
                    value = (currentReaderDefaults.epubFontSize * 100).toFloat(),
                    valueRange = EPUB_TEXT_SIZE_RANGE,
                    steps = 24,
                    onValueChangeFinished = { percent ->
                        updateReaderDefaults(currentReaderDefaults.copy(epubFontSize = percent / 100.0))
                    },
                )
                ChoiceChips(
                    title = stringResource(KMR.strings.ebook_layout),
                    selected = if (currentReaderDefaults.epubScroll) EPUB_SCROLL else EPUB_PAGES,
                    options = listOf(
                        EPUB_SCROLL to stringResource(KMR.strings.ebook_scroll),
                        EPUB_PAGES to stringResource(KMR.strings.ebook_pages),
                    ),
                    onSelect = { updateReaderDefaults(currentReaderDefaults.copy(epubScroll = it == EPUB_SCROLL)) },
                )
            }

            SettingsSection(stringResource(KMR.strings.ebook_settings_pdf)) {
                ChoiceChips(
                    title = stringResource(KMR.strings.ebook_pdf_view_mode),
                    selected = currentReaderDefaults.pdf.layout.name,
                    options = PdfLayoutMode.entries.map { it.name to it.label() },
                    onSelect = { mode ->
                        val layout = PdfLayoutMode.fromStoredName(mode)
                        updateReaderDefaults(currentReaderDefaults.copy(pdf = currentReaderDefaults.pdf.copy(layout = layout)))
                    },
                )
                ChoiceChips(
                    title = stringResource(KMR.strings.ebook_pdf_fit_mode),
                    selected = currentReaderDefaults.pdf.fit.name,
                    options = PdfFitMode.entries.map { it.name to it.label() },
                    optionEnabled = { mode ->
                        mode != PdfFitMode.WIDTH.name || currentReaderDefaults.pdf.layout != PdfLayoutMode.CONTINUOUS
                    },
                    onSelect = { mode ->
                        val fit = PdfFitMode.fromStoredName(mode)
                        updateReaderDefaults(currentReaderDefaults.copy(pdf = currentReaderDefaults.pdf.copy(fit = fit)))
                    },
                )
                SettingsSwitchRow(
                    title = stringResource(KMR.strings.ebook_pdf_trim_margins),
                    checked = currentReaderDefaults.pdf.trimMargins,
                    onCheckedChange = { enabled ->
                        updateReaderDefaults(currentReaderDefaults.copy(pdf = currentReaderDefaults.pdf.copy(trimMargins = enabled)))
                    },
                )
                ChoiceChips(
                    title = stringResource(KMR.strings.ebook_pdf_color_filter),
                    selected = currentReaderDefaults.pdf.color.name,
                    options = PdfColorMode.entries.map { it.name to it.label() },
                    onSelect = { mode ->
                        val color = PdfColorMode.fromStoredName(mode)
                        updateReaderDefaults(currentReaderDefaults.copy(pdf = currentReaderDefaults.pdf.copy(color = color)))
                    },
                )
                SettingsSlider(
                    title = stringResource(KMR.strings.ebook_pdf_page_spacing, currentReaderDefaults.pdf.pageGapDp),
                    value = currentReaderDefaults.pdf.pageGapDp.toFloat(),
                    valueRange = PDF_PAGE_SPACING_RANGE,
                    onValueChangeFinished = { spacing ->
                        updateReaderDefaults(currentReaderDefaults.copy(pdf = currentReaderDefaults.pdf.copy(pageGapDp = spacing)))
                    },
                )
            }

            SettingsSection(stringResource(KMR.strings.ebook_settings_behavior)) {
                SettingsSwitchRow(
                    title = stringResource(MR.strings.pref_keep_screen_on),
                    summary = stringResource(KMR.strings.ebook_settings_keep_screen_on_summary),
                    checked = currentKeepScreenOn,
                    onCheckedChange = { enabled ->
                        currentKeepScreenOn = enabled
                        onKeepScreenOnChange(enabled)
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = {
        Text(title, style = MaterialTheme.typography.titleMedium)
        HorizontalDivider()
        content()
    })
}

@Composable
private fun ChoiceChips(
    title: String,
    selected: String,
    options: List<Pair<String, String>>,
    optionEnabled: (String) -> Boolean = { true },
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        options.chunked(if (options.size > 3) 2 else 3).forEach { rowOptions ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowOptions.forEach { (value, label) ->
                    FilterChip(
                        selected = selected == value,
                        onClick = { onSelect(value) },
                        enabled = optionEnabled(value),
                        label = { Text(label) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsChoiceRow(
    title: String,
    selected: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second.orEmpty()
    androidx.compose.foundation.layout.Box {
        Surface(
            onClick = { expanded = true },
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(selectedLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = if (selected == value) ({ Icon(Icons.Outlined.Check, contentDescription = null) }) else null,
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    summary: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f).clickable(role = Role.Switch) { onCheckedChange(!checked) }.padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (summary != null) {
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChangeFinished: (Int) -> Unit,
) {
    var sliderValue by remember(value) { mutableFloatStateOf(value) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onValueChangeFinished(sliderValue.roundToInt()) },
            valueRange = valueRange,
            steps = steps,
        )
    }
}

@Composable
private fun EpubLibraryLayout.label(): String = when (this) {
    EpubLibraryLayout.GRID -> stringResource(KMR.strings.epub_library_view_grid)
    EpubLibraryLayout.LIST -> stringResource(KMR.strings.epub_library_view_list)
    EpubLibraryLayout.COVERS -> stringResource(KMR.strings.epub_library_view_covers)
}

@Composable
private fun EpubLibrarySort.label(): String = when (this) {
    EpubLibrarySort.RECENT -> stringResource(KMR.strings.epub_library_sort_recent)
    EpubLibrarySort.ADDED -> stringResource(KMR.strings.epub_library_sort_added)
    EpubLibrarySort.AUTHOR -> stringResource(KMR.strings.epub_library_sort_author)
    EpubLibrarySort.TITLE -> stringResource(KMR.strings.epub_library_sort_title)
    EpubLibrarySort.FILE_SIZE -> stringResource(KMR.strings.epub_library_sort_file_size)
    EpubLibrarySort.CHAPTER_COUNT -> stringResource(KMR.strings.epub_library_sort_chapters)
    EpubLibrarySort.PROGRESS -> stringResource(KMR.strings.epub_library_sort_progress)
}

@Composable
private fun EpubReaderTheme.label(): String = stringResource(
    when (this) {
        EpubReaderTheme.LIGHT -> KMR.strings.epub_reader_theme_light
        EpubReaderTheme.SEPIA -> KMR.strings.epub_reader_theme_sepia
        EpubReaderTheme.DARK -> KMR.strings.epub_reader_theme_dark
    },
)

@Composable
private fun PdfLayoutMode.label(): String = stringResource(
    when (this) {
        PdfLayoutMode.CONTINUOUS -> KMR.strings.ebook_pdf_continuous
        PdfLayoutMode.PAGINATED -> KMR.strings.ebook_pdf_paginated
    },
)

@Composable
private fun PdfFitMode.label(): String = stringResource(
    when (this) {
        PdfFitMode.WIDTH -> KMR.strings.ebook_pdf_fit_width
        PdfFitMode.PAGE -> KMR.strings.ebook_pdf_fit_page
    },
)

@Composable
private fun PdfColorMode.label(): String = stringResource(
    when (this) {
        PdfColorMode.ORIGINAL -> KMR.strings.ebook_pdf_color_original
        PdfColorMode.DIMMED -> KMR.strings.ebook_pdf_color_dimmed
        PdfColorMode.INVERT -> KMR.strings.ebook_pdf_color_invert
        PdfColorMode.SEPIA -> KMR.strings.ebook_pdf_color_sepia
        PdfColorMode.GRAYSCALE -> KMR.strings.ebook_pdf_color_grayscale
    },
)

private const val PUBLISHER_FONT = "PUBLISHER"
private const val EPUB_SCROLL = "SCROLL"
private const val EPUB_PAGES = "PAGES"
private val EPUB_TEXT_SIZE_RANGE = 50f..300f
private val PDF_PAGE_SPACING_RANGE = 0f..24f
// KMK <--
