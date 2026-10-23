plugins {
    id("mihon.library")
    kotlin("android")
    kotlin("plugin.serialization")
}

// KMK -->
extensions.configure<mihon.buildlogic.AndroidLibraryConfig>("komiyomiAndroid") {
    namespace("tachiyomi.core.metadata")
    testInstrumentationRunner("androidx.test.runner.AndroidJUnitRunner")
    consumerProguardFiles("consumer-rules.pro")
}
// KMK <--

dependencies {
    implementation(projects.sourceApi)

    implementation(kotlinx.bundles.serialization)
}
