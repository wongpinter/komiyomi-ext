// KMK -->
// Reading font resources for the ebook reader.
@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package eu.kanade.tachiyomi.ui.ebook

import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.css.FontStyle
import org.readium.r2.navigator.preferences.FontFamily

/** Bundled offline EPUB fonts, never used as app-wide or manga typefaces. */
internal object EpubFonts {
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
        Family("Crimson Pro", "crimsonpro", "CrimsonPro", "wght", 200..900, FontFamily.SERIF),
        Family("Bitter", "bitter", "Bitter", "wght", 100..900, FontFamily.SERIF),
        Family("Merriweather", "merriweather", "Merriweather", "opsz,wdth,wght", 300..900, FontFamily.SERIF),
        Family("Inter", "inter", "Inter", "opsz,wght", 100..900, FontFamily.SANS_SERIF),
        Family("Atkinson Hyperlegible Next", "atkinsonhyperlegiblenext", "AtkinsonHyperlegibleNext", "wght", 200..800, FontFamily.SANS_SERIF),
    )

    private val retiredFamilies = setOf(
        FontFamily("Source Serif 4"),
        FontFamily("Lora"),
        FontFamily("Source Sans 3"),
    )

    fun normalize(preferences: EpubPreferences): EpubPreferences =
        if (preferences.fontFamily in retiredFamilies) select(preferences, null) else preferences

    // Readium uses Android PATTERN_SIMPLE_GLOB, not regex. No broad asset-directory access.
    val servedAssetPatterns = families.flatMap { listOf(it.uprightPath, it.italicPath) }
        .map { it.replace(".", "\\.") }

    fun configuration() = EpubNavigatorFragment.Configuration {
        // Scroll mode uses a vertical pull at the end of a section to advance.
        disablePageTurnsWhileScrolling = true
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

    /** A null family restores publisher styles so embedded EPUB fonts can be used. */
    fun select(preferences: EpubPreferences, family: FontFamily?): EpubPreferences {
        // KMK -->
        // Allow bundled families, custom sideloaded families, or null (publisher styles)
        // KMK <--
        return preferences.copy(
            fontFamily = family,
            publisherStyles = if (family == null) true else preferences.publisherStyles,
        )
    }
}
// KMK <--
