// KMK -->
package eu.kanade.tachiyomi.ui.ebook

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import exh.log.xLogE
import org.readium.r2.navigator.preferences.FontFamily
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages user-sideloaded font files (.ttf, .otf) for the EPUB reader.
 */
internal object EpubCustomFontsManager {
    data class CustomFont(
        val name: String,
        val file: File,
    ) {
        val fontFamily: FontFamily get() = FontFamily(name)
    }

    private val base64Cache = ConcurrentHashMap<String, String>()

    fun getFontsDir(context: Context): File {
        return File(context.filesDir, "custom-fonts").apply {
            if (!exists()) mkdirs()
        }
    }

    fun listCustomFonts(context: Context): List<CustomFont> {
        val dir = getFontsDir(context)
        val files = dir.listFiles { file ->
            file.isFile && (file.extension.equals("ttf", ignoreCase = true) || file.extension.equals("otf", ignoreCase = true))
        } ?: return emptyList()

        return files.map { file ->
            val cleanName = file.nameWithoutExtension.replace(Regex("[-_]"), " ").trim()
            CustomFont(name = cleanName, file = file)
        }.sortedBy { it.name.lowercase() }
    }

    fun isCustomFont(context: Context, family: FontFamily?): Boolean {
        if (family == null) return false
        val customFonts = listCustomFonts(context)
        return customFonts.any { it.fontFamily == family || it.name.equals(family.name, ignoreCase = true) }
    }

    fun findCustomFont(context: Context, family: FontFamily?): CustomFont? {
        if (family == null) return false.let { null }
        val customFonts = listCustomFonts(context)
        return customFonts.firstOrNull { it.fontFamily == family || it.name.equals(family.name, ignoreCase = true) }
    }

    fun importFont(context: Context, uri: Uri): CustomFont? {
        return try {
            val resolver = context.contentResolver
            var displayName: String? = null
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        displayName = cursor.getString(nameIndex)
                    }
                }
            }

            val fileName = displayName ?: ("font_" + System.currentTimeMillis() + ".ttf")
            val targetFile = File(getFontsDir(context), fileName)

            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            base64Cache.remove(targetFile.absolutePath)
            val cleanName = targetFile.nameWithoutExtension.replace(Regex("[-_]"), " ").trim()
            CustomFont(name = cleanName, file = targetFile)
        } catch (e: Exception) {
            xLogE("Failed to import custom font from URI $uri", e)
            null
        }
    }

    fun deleteFont(font: CustomFont): Boolean {
        base64Cache.remove(font.file.absolutePath)
        return font.file.delete()
    }

    fun getFontBase64(font: CustomFont): String? {
        return try {
            base64Cache.getOrPut(font.file.absolutePath) {
                val bytes = font.file.readBytes()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            xLogE("Failed to encode custom font to base64", e)
            null
        }
    }
}
// KMK <--
