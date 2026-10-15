// KMK -->
package eu.kanade.presentation.reader.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mihon.core.archive.ArchiveSection
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ArchiveChapterNavigator(
    sections: List<ArchiveSection>,
    isRtl: Boolean,
    isVerticalSlider: Boolean,
    currentPage: Int,
    totalPages: Int,
    onPageIndexChange: (Int) -> Unit,
    onNextArchive: () -> Unit,
    enabledNextArchive: Boolean,
    onPreviousArchive: () -> Unit,
    enabledPreviousArchive: Boolean,
) {
    if (sections.isEmpty()) return
    val sectionIndex = sections.indexOfFirst { currentPage - 1 in it.startPage..it.endPage }.coerceAtLeast(0)
    val section = sections[sectionIndex]
    var pendingPage by remember(section, currentPage) {
        mutableIntStateOf((currentPage - section.startPage).coerceIn(1, section.pageCount))
    }
    var showChapters by remember(sections) { mutableStateOf(false) }
    var showPageJump by remember(sections) { mutableStateOf(false) }
    val sectionName = section.name.ifEmpty { stringResource(KMR.strings.archive_root_pages) }
    val chapterLabel = stringResource(KMR.strings.archive_chapters)
    val pageJumpLabel = stringResource(KMR.strings.archive_go_to_page)

    Column(
        modifier = if (isVerticalSlider) Modifier.fillMaxHeight().widthIn(max = 112.dp) else Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (isVerticalSlider) {
            TextButton(onClick = { showChapters = true }, enabled = sections.size > 1) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = chapterLabel)
            }
            Text(sectionName, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = { showPageJump = true }) {
                Text(stringResource(KMR.strings.archive_section_position, pendingPage, section.pageCount))
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = { showChapters = true },
                    enabled = sections.size > 1,
                ) {
                    Icon(Icons.Outlined.FolderOpen, contentDescription = chapterLabel)
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(sectionName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringResource(KMR.strings.archive_total_position, currentPage, totalPages),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                TextButton(onClick = { showPageJump = true }) {
                    Icon(Icons.Outlined.Edit, contentDescription = pageJumpLabel)
                    Text(stringResource(KMR.strings.archive_section_position, pendingPage, section.pageCount))
                }
            }
        }
        Box(modifier = if (isVerticalSlider) Modifier.weight(1f) else Modifier.fillMaxWidth()) {
            ChapterNavigator(
                isRtl = isRtl,
                isVerticalSlider = isVerticalSlider,
                currentPage = pendingPage,
                currentPageText = pendingPage.toString(),
                totalPages = section.pageCount,
                onPageIndexChange = { pageIndex ->
                    val pageNumber = pageIndex + 1
                    pendingPage = pageNumber
                    onPageIndexChange(section.pageIndex(pageNumber))
                },
                enabledPrevious = sectionIndex > 0 || enabledPreviousArchive,
                onPreviousChapter = {
                    if (sectionIndex > 0) onPageIndexChange(sections[sectionIndex - 1].startPage) else onPreviousArchive()
                },
                enabledNext = sectionIndex < sections.lastIndex || enabledNextArchive,
                onNextChapter = {
                    if (sectionIndex < sections.lastIndex) onPageIndexChange(sections[sectionIndex + 1].startPage) else onNextArchive()
                },
            )
        }
    }

    if (showChapters) {
        ArchiveChapterPicker(
            sections = sections,
            currentSectionIndex = sectionIndex,
            onDismiss = { showChapters = false },
            onSelect = {
                showChapters = false
                onPageIndexChange(it.startPage)
            },
        )
    }
    if (showPageJump) {
        ArchivePageJump(
            sectionName = sectionName,
            currentPage = pendingPage,
            pageCount = section.pageCount,
            onDismiss = { showPageJump = false },
            onSelect = {
                showPageJump = false
                onPageIndexChange(section.pageIndex(it))
            },
        )
    }
}

@Composable
internal fun ArchiveChapterPicker(
    sections: List<ArchiveSection>,
    currentSectionIndex: Int,
    onDismiss: () -> Unit,
    onSelect: (ArchiveSection) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val rootName = stringResource(KMR.strings.archive_root_pages)
    val filtered = remember(sections, query, rootName) {
        sections.filter { it.name.ifEmpty { rootName }.contains(query.trim(), ignoreCase = true) || it.path.contains(query.trim(), ignoreCase = true) }
    }
    val listState = rememberLazyListState(currentSectionIndex)
    LaunchedEffect(query) { listState.scrollToItem(if (query.isBlank()) currentSectionIndex else 0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(KMR.strings.archive_chapters)) },
        text = {
            Column {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(KMR.strings.archive_find_chapter)) },
                    singleLine = true,
                )
                LazyColumn(state = listState, modifier = Modifier.heightIn(max = 360.dp).padding(top = 8.dp)) {
                    items(filtered, key = { it.startPage }) { section ->
                        val isCurrent = section == sections[currentSectionIndex]
                        ListItem(
                            modifier = Modifier.clickable(role = Role.Button) { onSelect(section) }.semantics { selected = isCurrent },
                            headlineContent = { Text(section.name.ifEmpty { rootName }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(stringResource(KMR.strings.archive_page_range, section.startPage + 1, section.endPage + 1)) },
                            trailingContent = if (isCurrent) {
                                { Icon(Icons.Outlined.Check, contentDescription = null) }
                            } else {
                                null
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                            ),
                        )
                    }
                    if (filtered.isEmpty()) {
                        item { Text(stringResource(KMR.strings.archive_no_chapters_found), Modifier.padding(16.dp)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.action_cancel)) } },
    )
}

@Composable
private fun ArchivePageJump(
    sectionName: String,
    currentPage: Int,
    pageCount: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    val initialText = currentPage.toString()
    var input by remember { mutableStateOf(TextFieldValue(initialText, TextRange(0, initialText.length))) }
    val focus = remember { FocusRequester() }
    val page = input.text.toIntOrNull()
    val isValid = page != null && page in 1..pageCount
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(KMR.strings.archive_go_to_page)) },
        text = {
            Column {
                Text(sectionName, Modifier.padding(bottom = 8.dp))
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    value = input,
                    onValueChange = { if (it.text.all(Char::isDigit)) input = it },
                    label = { Text(stringResource(KMR.strings.archive_page_number)) },
                    supportingText = { Text(stringResource(KMR.strings.archive_page_range, 1, pageCount)) },
                    singleLine = true,
                    isError = input.text.isNotEmpty() && !isValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { if (isValid) onSelect(page!!) }),
                )
                LaunchedEffect(Unit) { focus.requestFocus() }
            }
        },
        confirmButton = {
            TextButton(enabled = isValid, onClick = { if (isValid) onSelect(page!!) }) { Text(stringResource(MR.strings.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.action_cancel)) } },
    )
}
// KMK <--
