// KMK -->
package eu.kanade.tachiyomi.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
internal fun OfflineLibrarySortControl(
    sort: OfflineLibrarySort,
    descending: Boolean,
    onSortChange: (OfflineLibrarySort) -> Unit,
    onDescendingChange: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Box(Modifier.weight(1f)) {
            TextButton(onClick = { expanded = true }) {
                Icon(Icons.Outlined.Sort, null, Modifier.padding(end = 8.dp))
                Text(stringResource(KMR.strings.offline_sort_by, stringResource(sort.label())))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                OfflineLibrarySort.entries.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(stringResource(item.label())) },
                        trailingIcon = { if (item == sort) Icon(Icons.Outlined.Check, null) },
                        onClick = {
                            onSortChange(item)
                            expanded = false
                        },
                    )
                }
            }
        }
        IconButton(onClick = { onDescendingChange(!descending) }) {
            Icon(
                if (descending) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward,
                stringResource(
                    if (descending) KMR.strings.offline_sort_descending else KMR.strings.offline_sort_ascending,
                ),
            )
        }
    }
}

private fun OfflineLibrarySort.label() = when (this) {
    OfflineLibrarySort.TITLE -> KMR.strings.offline_sort_title
    OfflineLibrarySort.FOLDER -> KMR.strings.offline_sort_folder
    OfflineLibrarySort.MODIFIED -> KMR.strings.offline_sort_modified
    OfflineLibrarySort.SIZE -> KMR.strings.offline_sort_size
}
// KMK <--
