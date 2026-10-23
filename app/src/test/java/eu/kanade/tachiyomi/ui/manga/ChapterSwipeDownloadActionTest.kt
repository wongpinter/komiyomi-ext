// KMK -->
package eu.kanade.tachiyomi.ui.manga

import eu.kanade.presentation.manga.components.ChapterDownloadAction
import eu.kanade.tachiyomi.data.download.model.Download
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChapterSwipeDownloadActionTest {
    @Test
    fun `missing files start a download even with stale downloaded state`() {
        assertEquals(
            ChapterDownloadAction.START_NOW,
            resolveChapterSwipeDownloadAction(Download.State.DOWNLOADED, isDownloaded = false),
        )
    }

    @Test
    fun `existing files delete even with stale not downloaded state`() {
        assertEquals(
            ChapterDownloadAction.DELETE,
            resolveChapterSwipeDownloadAction(Download.State.NOT_DOWNLOADED, isDownloaded = true),
        )
    }

    @Test
    fun `queued downloads remain cancellable`() {
        assertEquals(
            ChapterDownloadAction.CANCEL,
            resolveChapterSwipeDownloadAction(Download.State.QUEUE, isDownloaded = true),
        )
    }
}
// KMK <--
