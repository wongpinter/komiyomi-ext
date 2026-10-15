// KMK -->
package eu.kanade.presentation.reader.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun PageBookmarkControls(
    currentPage: Int,
    totalPages: Int,
    bookmarks: List<Int>,
    onToggle: () -> Unit,
    onRemove: (Int) -> Unit,
    onJump: (Int) -> Unit,
) {
    var showList by remember { mutableStateOf(false) }
    val valid = bookmarks.filter { it in 0 until totalPages }
    val marked = currentPage - 1 in valid
    Row(Modifier.fillMaxWidth()) {
        TextButton(onClick = onToggle, enabled = currentPage in 1..totalPages) {
            Icon(if (marked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, null)
            Text(stringResource(if (marked) KMR.strings.offline_remove_page_bookmark else KMR.strings.offline_bookmark_page))
        }
        TextButton(onClick = { showList = true }) {
            Text(stringResource(KMR.strings.offline_page_bookmark_count, valid.size))
        }
    }
    if (showList) {
        AlertDialog(
            onDismissRequest = { showList = false },
            title = { Text(stringResource(KMR.strings.offline_page_bookmarks)) },
            text = {
                if (valid.isEmpty()) {
                    Text(stringResource(KMR.strings.offline_no_page_bookmarks))
                } else {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(valid, key = { it }) { page ->
                            ListItem(
                                headlineContent = { Text(stringResource(KMR.strings.offline_page_number, page + 1)) },
                                trailingContent = {
                                    IconButton(onClick = { onRemove(page) }) {
                                        Icon(Icons.Outlined.Delete, stringResource(KMR.strings.offline_remove_page_bookmark))
                                    }
                                },
                                modifier = Modifier.clickable {
                                    showList = false
                                    onJump(page)
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showList = false }) { Text(stringResource(MR.strings.action_close)) }
            },
        )
    }
}
// KMK <--
