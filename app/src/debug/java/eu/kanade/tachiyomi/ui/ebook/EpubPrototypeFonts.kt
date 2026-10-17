// KMK -->
@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package eu.kanade.tachiyomi.ui.ebook

import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.css.FontStyle
import org.readium.r2.navigator.preferences.FontFamily

/** Bundled offline EPUB fonts, never used as app-wide or manga typefaces. */
internal object EpubPrototypeFonts {
    data class Family(
        val name: String,
        val directory: String,
        val stem: String,
        val axes: String,
        val weights: IntRange,
        val fallback: FontFamily,
    ) {
        val fontFamily = FontFamily(name)
        val uprightPath = "epub-fonts/$directory/$stem[$axes].ttf"
        val italicPath = "epub-fonts/$directory/$stem-Italic[$axes].ttf"
    }

    val families = listOf(
        Family("Literata", "literata", "Literata", "opsz,wght", 200..900, FontFamily.SERIF),
        Family("Source Serif 4", "sourceserif4", "SourceSerif4", "opsz,wght", 200..900, FontFamily.SERIF),
        Family("Lora", "lora", "Lora", "wght", 400..700, FontFamily.SERIF),
        Family("Atkinson Hyperlegible Next", "atkinsonhyperlegiblenext", "AtkinsonHyperlegibleNext", "wght", 200..800, FontFamily.SANS_SERIF),
        Family("Source Sans 3", "sourcesans3", "SourceSans3", "wght", 200..900, FontFamily.SANS_SERIF),
    )

    // Readium uses Android PATTERN_SIMPLE_GLOB, not regex. No broad asset-directory access.
    val servedAssetPatterns = families.flatMap { listOf(it.uprightPath, it.italicPath) }
        .map { it.replace(".", "\\.") }

    fun configuration() = EpubNavigatorFragment.Configuration {
        servedAssets += servedAssetPatterns
        families.forEach { family ->
            // No preloads: fonts are fetched by the WebView only when needed.
            addFontFamilyDeclaration(family.fontFamily, alternates = listOf(family.fallback)) {
                addFontFace {
                    // Readium encodes decoded paths itself, including brackets and commas.
                    addSource(family.uprightPath)
                    setFontStyle(FontStyle.NORMAL)
                    setFontWeight(family.weights)
                }
                addFontFace {
                    addSource(family.italicPath)
                    setFontStyle(FontStyle.ITALIC)
                    setFontWeight(family.weights)
                }
            }
        }
    }

    /** A null family returns to the publisher/default font without resetting other settings. */
    fun select(preferences: EpubPreferences, family: FontFamily?): EpubPreferences {
        require(family == null || families.any { it.fontFamily == family })
        return preferences.copy(fontFamily = family)
    }
}
// KMK <--
