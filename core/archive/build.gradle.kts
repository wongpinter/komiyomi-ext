plugins {
    id("mihon.library")
    kotlin("android")
    kotlin("plugin.serialization")
}

// KMK -->
extensions.configure<mihon.buildlogic.AndroidLibraryConfig>("komiyomiAndroid") {
    namespace("mihon.core.archive")
}
// KMK <--

dependencies {
    implementation(libs.jsoup)
    implementation(libs.libarchive)
    implementation(libs.unifile)
    // KMK -->
    implementation(projects.core.common)
    implementation(libs.injekt)
    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
    // KMK <--
}
