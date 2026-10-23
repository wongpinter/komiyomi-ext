plugins {
    id("mihon.library")
    kotlin("android")
}

// KMK -->
extensions.configure<mihon.buildlogic.AndroidLibraryConfig>("komiyomiAndroid") {
    namespace("com.murgupluoglu.flagkit")
}
// KMK <--

dependencies {
    implementation(projects.core.common)
}
