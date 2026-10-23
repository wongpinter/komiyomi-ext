import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

// KMK -->
plugins {
    id("mihon.library")
    kotlin("multiplatform")
}

kotlin {
    androidTarget()

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xexpect-actual-classes",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}
// KMK <--