// KMK -->
package eu.kanade.tachiyomi.ui.ebook

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.library.LibraryTab
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

data object EbookTab : Tab {
    @Suppress("unused")
    private fun readResolve(): Any = EbookTab

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 3u,
            title = stringResource(KMR.strings.ebook_nav_label),
            icon = rememberVectorPainter(Icons.Outlined.MenuBook),
        )

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val tabNavigator = LocalTabNavigator.current
        val ebookLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            tabNavigator.current = LibraryTab
        }

        LaunchedEffect(Unit) {
            ebookLauncher.launch(Intent(context, EbookActivity::class.java))
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    }
}
// KMK <--
