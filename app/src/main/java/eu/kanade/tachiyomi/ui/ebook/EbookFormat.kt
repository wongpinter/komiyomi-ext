// KMK -->
package eu.kanade.tachiyomi.ui.ebook

import java.io.File

internal enum class EbookFormat(val extension: String, val mimeType: String) {
    EPUB("epub", "application/epub+zip"),
    PDF("pdf", "application/pdf"),
    ;

    companion object {
        fun fromFile(file: File): EbookFormat = fromFileName(file.name)
            ?: throw IllegalArgumentException("Unsupported ebook format: ${file.name}")

        fun fromFileName(name: String?): EbookFormat? = when (name?.substringAfterLast('.', "")?.lowercase()) {
            EPUB.extension -> EPUB
            PDF.extension -> PDF
            else -> null
        }
    }
}
// KMK <--
