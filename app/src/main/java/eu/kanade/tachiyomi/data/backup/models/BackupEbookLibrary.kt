// KMK -->
package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupEbookLibrary(
    @ProtoNumber(1) val books: List<BackupEbook> = emptyList(),
    @ProtoNumber(2) val folderSources: List<BackupEbookFolderSource> = emptyList(),
    @ProtoNumber(3) val shelfSettings: BackupEbookShelfSettings = BackupEbookShelfSettings(),
)

@Serializable
data class BackupEbook(
    @ProtoNumber(1) val fileName: String,
    @ProtoNumber(2) val format: String,
    @ProtoNumber(3) val title: String,
    @ProtoNumber(4) val authors: String,
    @ProtoNumber(5) val sourceUri: String? = null,
    @ProtoNumber(6) val folderOwners: List<BackupEbookFolderOwner> = emptyList(),
    @ProtoNumber(7) val size: Long = 0L,
    @ProtoNumber(8) val lastOpened: Long = 0L,
    @ProtoNumber(9) val chapterCount: Int = 0,
    @ProtoNumber(10) val category: String? = null,
    @ProtoNumber(11) val epubLocatorJson: String? = null,
    @ProtoNumber(12) val epubPreferencesJson: String? = null,
    @ProtoNumber(13) val epubTheme: String? = null,
    @ProtoNumber(14) val epubBookmarkJson: String? = null,
    @ProtoNumber(15) val pdfPage: Int = 0,
    @ProtoNumber(16) val pdfLayout: String? = null,
    @ProtoNumber(17) val pdfFit: String? = null,
    @ProtoNumber(18) val pdfTrimMargins: Boolean = false,
    @ProtoNumber(19) val pdfColor: String? = null,
    @ProtoNumber(20) val pdfPageGapDp: Int = 8,
    @ProtoNumber(21) val pdfBookmarks: List<Int> = emptyList(),
    @ProtoNumber(22) val bookEntry: String? = null,
    @ProtoNumber(23) val coverEntry: String? = null,
    @ProtoNumber(24) val metadataEntry: String? = null,
)

@Serializable
data class BackupEbookFolderOwner(
    @ProtoNumber(1) val folderUri: String,
    @ProtoNumber(2) val category: String,
)

@Serializable
data class BackupEbookFolderSource(
    @ProtoNumber(1) val uri: String,
    @ProtoNumber(2) val name: String,
)

@Serializable
data class BackupEbookShelfSettings(
    @ProtoNumber(1) val query: String = "",
    @ProtoNumber(2) val filter: String = "ALL",
    @ProtoNumber(3) val sort: String = "RECENT",
    @ProtoNumber(4) val descending: Boolean = true,
    @ProtoNumber(5) val layout: String = "GRID",
    @ProtoNumber(6) val coverSize: Int = 96,
)
// KMK <--
