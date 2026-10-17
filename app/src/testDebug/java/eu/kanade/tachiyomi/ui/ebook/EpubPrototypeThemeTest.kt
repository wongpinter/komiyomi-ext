// KMK -->
package eu.kanade.tachiyomi.ui.ebook

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EpubPrototypeThemeTest {
    @Test
    fun `reader chrome uses manga app accents and preserves the selected reading surface`() {
        val appColors = lightColorScheme(
            primary = Color(0xFF385D9B),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD9E2FF),
            onPrimaryContainer = Color(0xFF001A42),
            secondary = Color(0xFF565E71),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFDBE2F9),
            onSecondaryContainer = Color(0xFF131B2C),
            tertiary = Color(0xFF705575),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFFAD7FC),
            onTertiaryContainer = Color(0xFF28132F),
        )

        val darkChrome = EpubReaderTheme.DARK.chromeColorScheme(appColors)

        assertEquals(appColors.primary, darkChrome.primary)
        assertEquals(appColors.secondary, darkChrome.secondary)
        assertEquals(EpubReaderTheme.DARK.colorScheme.surface, darkChrome.surface)
        assertEquals(EpubReaderTheme.DARK.colorScheme.onSurface, darkChrome.onSurface)
    }
}
// KMK <--
