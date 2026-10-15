// KMK -->
package eu.kanade.tachiyomi.ui.home

import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

internal class OfflineLibraryPreferences(private val store: PreferenceStore = Injekt.get()) {
    fun sort() = store.getString("offline_library_sort", OfflineLibrarySort.TITLE.name)
    fun descending() = store.getBoolean("offline_library_sort_descending", false)
    fun favorites() = store.getStringSet("offline_library_favorites", emptySet())
    fun status() = store.getString("offline_library_status", "ALL")
    fun favoritesOnly() = store.getBoolean("offline_library_favorites_only", false)
    fun listView() = store.getBoolean("offline_library_list_view", false)
    fun coverSize() = store.getInt("offline_library_cover_size", 128)
}

internal enum class OfflineLibrarySort {
    TITLE,
    FOLDER,
    MODIFIED,
    SIZE,
}

internal enum class OfflineReadingFilter { ALL, UNREAD, READING, COMPLETED }
// KMK <--
