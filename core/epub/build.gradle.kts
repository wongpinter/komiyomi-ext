// KMK -->
plugins {
    id("mihon.library")
    kotlin("android")
}
extensions.configure<mihon.buildlogic.AndroidLibraryConfig>("komiyomiAndroid") {
    namespace("com.wongpinter.komiyomi.epub")
}
dependencies {
    api(libs.readium.navigator)
    implementation(libs.readium.streamer)
    implementation(platform(kotlinx.coroutines.bom))
    implementation(kotlinx.coroutines.core)
    testImplementation(libs.bundles.test)
    testImplementation(kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
// KMK <--
