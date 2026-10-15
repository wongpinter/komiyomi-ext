// KMK -->
package eu.kanade.tachiyomi.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
internal fun OfflineLibraryControls(
    status: OfflineReadingFilter,
    favoritesOnly: Boolean,
    listView: Boolean,
    coverSize: Int,
    onStatus: (OfflineReadingFilter) -> Unit,
    onFavoritesOnly: (Boolean) -> Unit,
    onListView: (Boolean) -> Unit,
    onCoverSize: (Int) -> Unit,
) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OfflineReadingFilter.entries.forEach { filter ->
            item(key = filter.name) {
                FilterChip(selected = status == filter, onClick = { onStatus(filter) }, label = { Text(stringResource(filter.label())) })
            }
        }
        item {
            FilterChip(selected = favoritesOnly, onClick = { onFavoritesOnly(!favoritesOnly) }, label = { Text(stringResource(KMR.strings.offline_favorites)) })
        }
        item {
            IconButton(onClick = { onListView(!listView) }) {
                Icon(if (listView) Icons.Outlined.GridView else Icons.Outlined.ViewList, stringResource(if (listView) KMR.strings.offline_grid_view else KMR.strings.offline_list_view))
            }
        }
        if (!listView) {
            item {
                var expanded by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { expanded = true }) { Text(stringResource(KMR.strings.offline_cover_size)) }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf(96 to KMR.strings.offline_covers_small, 128 to KMR.strings.offline_covers_medium, 176 to KMR.strings.offline_covers_large).forEach { (size, label) ->
                            DropdownMenuItem(text = { Text(stringResource(label)) }, trailingIcon = { if (coverSize == size) Icon(Icons.Outlined.Check, null) }, onClick = {
                                onCoverSize(size)
                                expanded = false
                            })
                        }
                    }
                }
            }
        }
    }
}

internal fun OfflineReadingFilter.label() = when (this) {
    OfflineReadingFilter.ALL -> KMR.strings.offline_status_all
    OfflineReadingFilter.UNREAD -> KMR.strings.offline_status_unread
    OfflineReadingFilter.READING -> KMR.strings.offline_status_reading
    OfflineReadingFilter.COMPLETED -> KMR.strings.offline_status_completed
}
// KMK <--
