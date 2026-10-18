// KMK -->
// Reading font preference coverage.
@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package eu.kanade.tachiyomi.ui.ebook

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.FontFamily
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class EpubFontsTest {
    @Test
    fun `six distinct font families preserve source order`() {
        assertEquals(
            listOf("Literata", "Crimson Pro", "Bitter", "Merriweather", "Inter", "Atkinson Hyperlegible Next"),
            EpubFonts.families.map { it.name },
        )
        assertEquals(6, EpubFonts.families.map { it.fontFamily }.distinct().size)
    }

    @Test
    fun `font selection preserves preferences and publisher reset restores embedded fonts`() {
        val original = EpubPreferences(scroll = true, fontSize = 1.7, pageMargins = 1.4, publisherStyles = false)
        EpubFonts.families.forEach { family ->
            val changed = EpubFonts.select(original, family.fontFamily)
            assertEquals(family.fontFamily, changed.fontFamily)
            assertEquals(original.copy(publisherStyles = true), EpubFonts.select(changed, null))
        }
    }

    @Test
    fun `unknown font names cannot enter the local picker`() {
        assertThrows(IllegalArgumentException::class.java) {
            EpubFonts.select(EpubPreferences(), FontFamily("Unknown family"))
        }
    }

    @Test
    fun `only exact bundled TTF paths are served`() {
        val patterns = EpubFonts.servedAssetPatterns
        val paths = EpubFonts.families.flatMap { listOf(it.uprightPath, it.italicPath) }
        // Android's simple glob treats brackets/commas literally, unlike regex character classes.
        val expected = paths.map { it.removeSuffix(".ttf") + '\\' + ".ttf" }
        assertEquals(expected, patterns)
        assertEquals(12, patterns.distinct().size)
        assertFalse(patterns.any { ".*" in it || "\\Q" in it })
        listOf("epub-fonts/manifest.json", "epub-fonts/bitter/OFL.txt", "../secret.ttf", "other/font.ttf").forEach { path ->
            assertFalse(path in paths)
        }
    }

    @Test
    fun `declared font faces and weights match actual bundled variable fonts`() {
        val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .map { File(it, "app/src/debug/assets") }
            .first { File(it, "epub-fonts/manifest.json").isFile }
        EpubFonts.families.forEach { family ->
            listOf(family.uprightPath, family.italicPath).forEach { path ->
                val file = File(root, path)
                assertTrue(file.isFile, path)
                val bytes = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.BIG_ENDIAN)
                val tableCount = bytes.getShort(4).toInt() and 0xffff
                val fvar = (0 until tableCount).map { 12 + it * 16 }
                    .first { bytes.getInt(it) == 0x66766172 }
                val offset = bytes.getInt(fvar + 8)
                val axisOffset = bytes.getShort(offset + 4).toInt() and 0xffff
                val axisCount = bytes.getShort(offset + 8).toInt() and 0xffff
                val axisSize = bytes.getShort(offset + 10).toInt() and 0xffff
                val weight = (0 until axisCount).map { offset + axisOffset + it * axisSize }
                    .first { bytes.getInt(it) == 0x77676874 }
                assertEquals(family.weights.first, bytes.getInt(weight + 4) / 65536, path)
                assertEquals(family.weights.last, bytes.getInt(weight + 12) / 65536, path)
            }
        }
    }
}
// KMK <--
