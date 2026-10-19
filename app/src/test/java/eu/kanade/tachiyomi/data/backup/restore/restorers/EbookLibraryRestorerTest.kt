// KMK -->
package eu.kanade.tachiyomi.data.backup.restore.restorers

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import eu.kanade.tachiyomi.data.backup.BackupDecoder
import eu.kanade.tachiyomi.data.backup.models.BackupEbook
import eu.kanade.tachiyomi.data.backup.models.BackupEbookLibrary
import eu.kanade.tachiyomi.ui.ebook.EpubStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

class EbookLibraryRestorerTest {
    @TempDir
    lateinit var directory: File

    @Test
    fun `restoring the same included backup twice does not create duplicate books`() = runTest {
        val context = mockk<Context>()
        val preferences = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        val values = mutableMapOf<String, Any>()
        every { context.noBackupFilesDir } returns directory
        every { context.getSharedPreferences(any(), Context.MODE_PRIVATE) } returns preferences
        every { preferences.edit() } returns editor
        every { preferences.all } answers { values.toMap() }
        every { preferences.getString(any(), any()) } answers { values[firstArg()] as? String ?: secondArg() }
        every { preferences.getInt(any(), any()) } answers { values[firstArg()] as? Int ?: secondArg() }
        every { preferences.getBoolean(any(), any()) } answers { values[firstArg()] as? Boolean ?: secondArg() }
        every { preferences.getStringSet(any(), any()) } answers { values[firstArg()] as? Set<String> ?: secondArg() }
        every { editor.putString(any(), any()) } answers {
            values[firstArg()] = secondArg<String>()
            editor
        }
        every { editor.putInt(any(), any()) } answers {
            values[firstArg()] = secondArg<Int>()
            editor
        }
        every { editor.putBoolean(any(), any()) } answers {
            values[firstArg()] = secondArg<Boolean>()
            editor
        }
        every { editor.putStringSet(any(), any()) } answers {
            values[firstArg()] = secondArg<Set<String>>().toSet()
            editor
        }
        every { editor.remove(any()) } answers {
            values.remove(firstArg())
            editor
        }

        val store = EpubStore(context)
        val fileName = "00000000-0000-0000-0000-000000000001.pdf"
        val backupContents = "restored PDF".toByteArray()
        val book = BackupEbook(
            fileName = fileName,
            format = "pdf",
            title = "Test PDF",
            authors = "Test author",
            size = backupContents.size.toLong(),
            bookEntry = "ebooks/books/$fileName",
        )
        val decoder = mockk<BackupDecoder>()
        coEvery { decoder.forEachArchiveEntry(any(), any(), any()) } coAnswers {
            val action = thirdArg<suspend (String, InputStream) -> Unit>()
            action(book.bookEntry!!, ByteArrayInputStream(backupContents))
        }
        val restored = mutableListOf<String?>()

        suspend fun restore() {
            EbookLibraryRestorer(context, store).restore(
                uri = mockk<Uri>(),
                library = BackupEbookLibrary(books = listOf(book)),
                decoder = decoder,
                onBookRestored = { _, error -> restored += error },
            )
        }
        restore()
        restore()

        assertEquals(1, store.importedBooks().size)
        assertEquals(listOf(null, null), restored)
    }
}
// KMK <--
