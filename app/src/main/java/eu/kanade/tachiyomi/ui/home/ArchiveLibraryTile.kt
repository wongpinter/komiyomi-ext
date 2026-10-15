// KMK -->
package eu.kanade.tachiyomi.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tachiyomi.domain.chapter.model.ArchiveReadingProgress
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.source.local.io.LocalArchiveEntry

@Composable
internal fun FavoriteButton(favorite: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))) {
        Icon(
            if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
            stringResource(if (favorite) KMR.strings.offline_remove_favorite else KMR.strings.offline_add_favorite),
            tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun archiveProgressLabel(progress: ArchiveReadingProgress?): String = when {
    progress?.completed == true -> stringResource(KMR.strings.offline_status_completed)
    progress?.started != true -> stringResource(KMR.strings.offline_status_unread)
    progress.pageCount > 0 -> stringResource(KMR.strings.offline_read_percent, (progress.fraction * 100).toInt().coerceAtMost(99))
    else -> stringResource(KMR.strings.offline_page_number, progress.lastPage + 1)
}

@Composable
internal fun ArchiveLibraryTile(
    entry: LocalArchiveEntry,
    progress: ArchiveReadingProgress?,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onOpen: () -> Unit,
    enabled: Boolean,
    model: LocalArchiveScreenModel,
    revision: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier.clickable(enabled = enabled, onClick = onOpen)) {
        Box {
            ArchiveCover(entry, model, revision, Modifier.fillMaxWidth())
            FavoriteButton(favorite, Modifier.align(Alignment.TopEnd).padding(4.dp), onFavorite)
        }
        Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
        if (entry.parentPath.isNotEmpty()) {
            Text(entry.parentPath, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(archiveProgressLabel(progress), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        if (progress?.started == true && progress.pageCount > 0) {
            LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
    }
}

@Composable
internal fun ContinueReadingShelf(
    entries: List<LocalArchiveEntry>,
    progress: Map<String, ArchiveReadingProgress>,
    favorites: Set<String>,
    onFavorite: (LocalArchiveEntry) -> Unit,
    onOpen: (LocalArchiveEntry) -> Unit,
    enabled: Boolean,
    model: LocalArchiveScreenModel,
    revision: Int,
) {
    Column {
        Text(stringResource(KMR.strings.offline_continue_reading), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(entries, key = { it.path }) { entry ->
                ArchiveLibraryTile(entry, progress[entry.path], entry.file.uri.toString() in favorites, { onFavorite(entry) }, { onOpen(entry) }, enabled, model, revision, Modifier.width(128.dp))
            }
        }
    }
}
// KMK <--
