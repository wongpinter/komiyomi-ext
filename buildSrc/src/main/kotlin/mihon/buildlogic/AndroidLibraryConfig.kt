package mihon.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import javax.inject.Inject

// KMK -->
open class AndroidLibraryConfig @Inject constructor(
    private val project: Project,
) {
    fun namespace(value: String) = configureAndroid {
        namespace = value
    }

    fun testInstrumentationRunner(value: String) = configureAndroid {
        defaultConfig {
            testInstrumentationRunner = value
        }
    }

    fun consumerProguardFiles(vararg paths: String) = configureAndroid {
        defaultConfig {
            consumerProguardFiles.addAll(paths.map(project::file))
        }
    }

    fun sourceSetResources(name: String, vararg paths: String) = configureAndroid {
        sourceSets.getByName(name).res.srcDirs(*paths)
    }

    fun sourceSetKotlinDirs(name: String, vararg paths: String) = configureAndroid {
        sourceSets.getByName(name).kotlin.srcDirs(*paths)
    }

    fun sourceSetManifest(name: String, path: String) = configureAndroid {
        sourceSets.getByName(name).manifest.srcFile(path)
    }

    fun disableLintChecks(vararg ids: String) = configureAndroid {
        lint {
            disable.addAll(ids)
        }
    }

    fun compilerFreeArgs(vararg args: String) {
        project.tasks.withType<KotlinCompile>().configureEach {
            compilerOptions.freeCompilerArgs.addAll(args.toList())
        }
    }

    fun compilerOptIns(vararg ids: String) {
        project.tasks.withType<KotlinCompile>().configureEach {
            compilerOptions.optIn.addAll(ids.toList())
        }
    }

    private fun configureAndroid(action: LibraryExtension.() -> Unit) {
        project.extensions.configure<LibraryExtension>("android", action)
    }
}
// KMK <--
