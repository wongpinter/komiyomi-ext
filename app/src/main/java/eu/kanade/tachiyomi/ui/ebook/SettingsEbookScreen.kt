// KMK -->
package eu.kanade.tachiyomi.ui.ebook

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

internal object SettingsEbookScreen : Screen() {
    @Suppress("unused")
    private fun readResolve(): Any = SettingsEbookScreen

    @Composable
    override fun Content() {
        val appContext = LocalContext.current.applicationContext
        val store = remember(appContext) { EpubStore(appContext) }
        val readerPreference = remember { Injekt.get<ReaderPreferences>().keepScreenOn() }
        val keepScreenOn by readerPreference.collectAsState()
        var readerDefaults by remember(store) { mutableStateOf(store.ebookReaderDefaults()) }
        var shelfSettings by remember(store) { mutableStateOf(store.shelfSettings()) }
        val onBack = LocalBackPress.current ?: {}

        EbookSettingsContent(
            readerDefaults = readerDefaults,
            shelfSettings = shelfSettings,
            keepScreenOn = keepScreenOn,
            onReaderDefaultsChange = { updated ->
                store.saveEbookReaderDefaults(updated)
                readerDefaults = updated
            },
            onShelfSettingsChange = { updated ->
                store.saveShelfSettings(updated)
                shelfSettings = updated
            },
            onKeepScreenOnChange = readerPreference::set,
            onBack = onBack,
        )
    }
}
// KMK <--
