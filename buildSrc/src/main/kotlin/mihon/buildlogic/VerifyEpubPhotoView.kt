// KMK -->
package mihon.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/** Fails closed before deduplicating the debug EPUB renderer's embedded PhotoView. */
@CacheableTask
abstract class VerifyEpubPhotoView : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val navigatorAar: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val photoViewAar: RegularFileProperty

    @TaskAction
    fun verify() {
        fun classes(bytes: ByteArray): Map<String, ByteArray> = buildMap {
            ZipInputStream(bytes.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name.endsWith(".class")) put(entry.name, zip.readBytes())
                }
            }
        }
        val original = ZipFile(photoViewAar.get().asFile).use { aar ->
            classes(aar.getInputStream(checkNotNull(aar.getEntry("classes.jar"))).use { it.readBytes() })
        }
        val bundled = ZipFile(navigatorAar.get().asFile).use { aar ->
            val jar = checkNotNull(aar.getEntry("libs/PhotoView-2.3.0.jar")) {
                "Readium packaging changed; review the EPUB prototype PhotoView deduplication."
            }
            classes(aar.getInputStream(jar).use { it.readBytes() })
        }
        check(original.isNotEmpty() && original.keys == bundled.keys) {
            "EPUB prototype PhotoView classes differ from the manga reader dependency."
        }
        check(original.all { (name, bytes) -> bytes.contentEquals(bundled.getValue(name)) }) {
            "EPUB prototype must not replace manga PhotoView with different bytecode."
        }
        logger.lifecycle("Verified all ${original.size} bundled PhotoView classes are byte-identical.")
    }
}
// KMK <--
