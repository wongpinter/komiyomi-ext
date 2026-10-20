// KMK -->
// Reading layout preference coverage.
@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package eu.kanade.tachiyomi.ui.ebook

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.TextAlign

class EpubLayoutTest {
    @Test
    fun `layout starts with publisher defaults`() {
        val preferences = EpubPreferences(scroll = true)
        EpubLayout.Metric.entries.forEach { assertNull(EpubLayout.value(preferences, it)) }
        assertNull(preferences.textAlign)
        assertNull(preferences.publisherStyles)
    }

    @Test
    fun `every metric supports both boundaries and rejects invalid numbers`() {
        EpubLayout.Metric.entries.forEach { metric ->
            listOf(metric.minimum, metric.maximum).forEach { value ->
                val result = EpubLayout.set(EpubPreferences(), metric, value)
                assertEquals(value, EpubLayout.value(result, metric))
            }
            listOf(metric.minimum - 0.1, metric.maximum + 0.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { value ->
                assertThrows(IllegalArgumentException::class.java) {
                    EpubLayout.set(EpubPreferences(), metric, value)
                }
            }
        }
    }

    @Test
    fun `horizontal margins do not disable publisher styles`() {
        assertNull(EpubLayout.set(EpubPreferences(), EpubLayout.Metric.MARGINS, 1.5).publisherStyles)
        assertEquals(true, EpubLayout.set(EpubPreferences(publisherStyles = true), EpubLayout.Metric.MARGINS, 0.0).publisherStyles)
    }

    @Test
    fun `advanced overrides enable custom layout and preserve unrelated preferences`() {
        val original = EpubPreferences(
            fontFamily = EpubFonts.families.first().fontFamily,
            fontSize = 1.7,
            scroll = true,
            pageMargins = 1.3,
            paragraphIndent = 0.4,
            publisherStyles = true,
        )
        val changed = EpubLayout.set(original, EpubLayout.Metric.LINE_HEIGHT, 1.6)
        assertEquals(original.copy(lineHeight = 1.6, publisherStyles = false), changed)
        assertFalse(requireNotNull(changed.publisherStyles))
    }

    @Test
    fun `clearing one metric retains other overrides and custom mode`() {
        val original = EpubPreferences(lineHeight = 1.8, paragraphSpacing = 0.6, publisherStyles = false)
        val cleared = EpubLayout.set(original, EpubLayout.Metric.LINE_HEIGHT, null)
        assertEquals(original.copy(lineHeight = null), cleared)
    }

    @Test
    fun `alignment supports only actually mapped renderer values`() {
        val original = EpubPreferences(scroll = true, fontSize = 1.8)
        assertEquals(listOf(TextAlign.START, TextAlign.LEFT, TextAlign.RIGHT, TextAlign.JUSTIFY), EpubLayout.alignments)
        EpubLayout.alignments.forEach { value ->
            val expected = original.copy(
                textAlign = value,
                hyphens = if (value == TextAlign.JUSTIFY) true else original.hyphens,
                publisherStyles = false,
            )
            assertEquals(expected, EpubLayout.align(original, value))
        }
        listOf(TextAlign.CENTER, TextAlign.END).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { EpubLayout.align(original, value) }
        }
        val selected = EpubLayout.align(original, TextAlign.JUSTIFY)
        assertEquals(selected.copy(textAlign = null), EpubLayout.align(selected, null))
    }

    @Test
    fun `restore publisher layout clears only layout panel values`() {
        val original = EpubPreferences(
            fontFamily = EpubFonts.families[2].fontFamily,
            fontSize = 1.6,
            scroll = true,
            letterSpacing = 0.2,
        )
        val changed = original.copy(
            pageMargins = 2.0,
            lineHeight = 1.9,
            paragraphSpacing = 0.8,
            paragraphIndent = 1.2,
            textAlign = TextAlign.RIGHT,
            publisherStyles = false,
        )
        assertEquals(original, EpubLayout.reset(changed))
        assertEquals(original, EpubLayout.reset(original))
    }
}
// KMK <--
