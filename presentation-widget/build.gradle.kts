plugins {
    id("mihon.library")
    id("mihon.library.compose")
    kotlin("android")
}

// KMK -->
extensions.configure<mihon.buildlogic.AndroidLibraryConfig>("komiyomiAndroid") {
    namespace("tachiyomi.presentation.widget")
    testInstrumentationRunner("androidx.test.runner.AndroidJUnitRunner")
    consumerProguardFiles("consumer-rules.pro")
}
// KMK <--

dependencies {
    implementation(projects.core.common)
    implementation(projects.domain)
    implementation(projects.presentationCore)
    api(projects.i18n)

    implementation(compose.glance)
    implementation(libs.material)

    implementation(kotlinx.immutables)

    implementation(platform(libs.coil.bom))
    implementation(libs.coil.core)

    api(libs.injekt)
}
