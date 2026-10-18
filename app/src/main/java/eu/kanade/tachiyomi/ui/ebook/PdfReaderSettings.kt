// KMK -->
package eu.kanade.tachiyomi.ui.ebook

internal enum class PdfLayoutMode {
    CONTINUOUS,
    PAGINATED,
    ;

    companion object {
        fun fromStoredName(value: String?): PdfLayoutMode = entries.firstOrNull { it.name == value } ?: CONTINUOUS
    }
}

internal enum class PdfFitMode {
    WIDTH,
    PAGE,
    ;

    companion object {
        fun fromStoredName(value: String?): PdfFitMode = entries.firstOrNull { it.name == value } ?: WIDTH
    }
}

internal enum class PdfColorMode {
    ORIGINAL,
    DIMMED,
    INVERT,
    SEPIA,
    GRAYSCALE,
    ;

    companion object {
        fun fromStoredName(value: String?): PdfColorMode = entries.firstOrNull { it.name == value } ?: ORIGINAL
    }
}

internal data class PdfReaderSettings(
    val layout: PdfLayoutMode = PdfLayoutMode.CONTINUOUS,
    val fit: PdfFitMode = PdfFitMode.WIDTH,
    val trimMargins: Boolean = false,
    val color: PdfColorMode = PdfColorMode.ORIGINAL,
    val pageGapDp: Int = 8,
)
// KMK <--
