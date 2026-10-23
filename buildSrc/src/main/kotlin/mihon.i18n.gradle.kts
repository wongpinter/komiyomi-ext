import mihon.buildlogic.generatedBuildDir
import mihon.buildlogic.tasks.getLocalesConfigTask
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// KMK -->
plugins {
    id("mihon.library")
    kotlin("multiplatform")
    id("dev.icerock.mobile.multiplatform-resources")
}

val generatedAndroidResourceDir = generatedBuildDir.resolve("android/res")
val isBaseI18nModule = project.name == "i18n"
val packageName = when (project.name) {
    "i18n-kmk" -> "tachiyomi.i18n.kmk"
    "i18n-sy" -> "tachiyomi.i18n.sy"
    else -> "tachiyomi.i18n"
}

kotlin {
    androidTarget()
    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain {
            dependencies {
                api("dev.icerock.moko:resources:0.26.4")
            }
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

android {
    namespace = packageName

    sourceSets {
        named("main") {
            if (isBaseI18nModule) {
                res.srcDirs("src/commonMain/resources", generatedAndroidResourceDir)
            } else {
                res.srcDir("src/commonMain/resources")
            }
        }
    }

    lint {
        disable.addAll(listOf("MissingTranslation", "ExtraTranslation"))
    }
}

multiplatformResources {
    when (project.name) {
        "i18n-kmk" -> resourcesClassName.set("KMR")
        "i18n-sy" -> resourcesClassName.set("SYMR")
    }
    resourcesPackage.set(packageName)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")
}

if (isBaseI18nModule) {
    val localesConfigTask = project.getLocalesConfigTask(generatedAndroidResourceDir)
    tasks.named("preBuild") {
        dependsOn(localesConfigTask)
    }
}
// KMK <--