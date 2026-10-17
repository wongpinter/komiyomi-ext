// KMK -->
@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package eu.kanade.tachiyomi.ui.ebook

import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.TextAlign

/** Bounded layout overrides for the debug EPUB reader, isolated from manga preferences. */
internal object EpubPrototypeLayout {
    enum class Metric(val minimum: Double, val maximum: Double, val fallback: Double) {
        MARGINS(0.0, 4.0, 1.0),
        LINE_HEIGHT(1.0, 2.0, 1.2),
        PARAGRAPH_SPACING(0.0, 2.0, 0.0),
        PARAGRAPH_INDENT(0.0, 3.0, 0.0),
    }

    // Readium 3.2 maps CENTER/END to START; don't advertise unsupported alignments.
    val alignments = listOf(TextAlign.START, TextAlign.LEFT, TextAlign.RIGHT, TextAlign.JUSTIFY)

    fun value(preferences: EpubPreferences, metric: Metric): Double? = when (metric) {
        Metric.MARGINS -> preferences.pageMargins
        Metric.LINE_HEIGHT -> preferences.lineHeight
        Metric.PARAGRAPH_SPACING -> preferences.paragraphSpacing
        Metric.PARAGRAPH_INDENT -> preferences.paragraphIndent
    }

    fun set(preferences: EpubPreferences, metric: Metric, value: Double?): EpubPreferences {
        require(value == null || (value.isFinite() && value in metric.minimum..metric.maximum))
        val changed = when (metric) {
            Metric.MARGINS -> preferences.copy(pageMargins = value)
            Metric.LINE_HEIGHT -> preferences.copy(lineHeight = value)
            Metric.PARAGRAPH_SPACING -> preferences.copy(paragraphSpacing = value)
            Metric.PARAGRAPH_INDENT -> preferences.copy(paragraphIndent = value)
        }
        // Readium requires custom styles for these advanced overrides. Margins work independently.
        return if (value != null && metric != Metric.MARGINS) changed.copy(publisherStyles = false) else changed
    }

    fun align(preferences: EpubPreferences, value: TextAlign?): EpubPreferences {
        require(value == null || value in alignments)
        return preferences.copy(
            textAlign = value,
            publisherStyles = if (value != null) false else preferences.publisherStyles,
        )
    }

    /** Reset only this panel's layout overrides; retain fonts, text size, mode and other settings. */
    fun reset(preferences: EpubPreferences) = preferences.copy(
        pageMargins = null,
        lineHeight = null,
        paragraphSpacing = null,
        paragraphIndent = null,
        textAlign = null,
        publisherStyles = null,
    )
}
// KMK <--
