package eu.kanade.presentation.components

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import eu.kanade.presentation.manga.DownloadAction
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun DownloadDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onDownloadClicked: (DownloadAction) -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = /* KMK --> */ DefaultDropdownMenuOffset, /* KMK <-- */
    // KMK -->
    includeCreateCbz: Boolean = false,
    includeDownloadOptions: Boolean = true,
    // KMK <--
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        content = {
            DownloadDropdownMenuItems(
                onDismissRequest = onDismissRequest,
                onDownloadClicked = onDownloadClicked,
                // KMK -->
                includeCreateCbz = includeCreateCbz,
                includeDownloadOptions = includeDownloadOptions,
                // KMK <--
            )
        },
    )
}

@Composable
private fun DownloadDropdownMenuItems(
    onDismissRequest: () -> Unit,
    onDownloadClicked: (DownloadAction) -> Unit,
    // KMK -->
    includeCreateCbz: Boolean,
    includeDownloadOptions: Boolean,
    // KMK <--
) {
    val options = if (includeDownloadOptions) {
        persistentListOf(
            DownloadAction.NEXT_1_CHAPTER to pluralStringResource(MR.plurals.download_amount, 1, 1),
            DownloadAction.NEXT_5_CHAPTERS to pluralStringResource(MR.plurals.download_amount, 5, 5),
            DownloadAction.NEXT_10_CHAPTERS to pluralStringResource(MR.plurals.download_amount, 10, 10),
            DownloadAction.NEXT_25_CHAPTERS to pluralStringResource(MR.plurals.download_amount, 25, 25),
            // KMK -->
            DownloadAction.ALL_CHAPTERS to stringResource(KMR.strings.download_all_chapters),
            // KMK <--
            DownloadAction.UNREAD_CHAPTERS to stringResource(MR.strings.download_unread),
            DownloadAction.BOOKMARKED_CHAPTERS to stringResource(MR.strings.download_bookmarked),
        )
    } else {
        persistentListOf<Pair<DownloadAction, String>>()
    }
    // KMK -->
    val availableOptions = if (includeCreateCbz) {
        options + (DownloadAction.CREATE_CBZ to stringResource(KMR.strings.create_combined_cbz))
    } else {
        options
    }
    // KMK <--

    // KMK -->
    availableOptions.forEach { (downloadAction, string) ->
        // KMK <--
        DropdownMenuItem(
            text = { Text(text = string) },
            onClick = {
                onDownloadClicked(downloadAction)
                onDismissRequest()
            },
        )
    }
}
