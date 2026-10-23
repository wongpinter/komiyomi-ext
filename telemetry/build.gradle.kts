import mihon.buildlogic.Config

plugins {
    id("mihon.library")
    kotlin("android")
}

// KMK -->
extensions.configure<mihon.buildlogic.AndroidLibraryConfig>("komiyomiAndroid") {
    namespace("mihon.telemetry")
    if (Config.includeTelemetry) {
        sourceSetKotlinDirs("main", "src/firebase/kotlin")
    } else {
        sourceSetKotlinDirs("main", "src/noop/kotlin")
        sourceSetManifest("main", "src/noop/AndroidManifext.xml")
    }
}
// KMK <--

dependencies {
    if (Config.includeTelemetry) {
        implementation(platform(libs.firebase.bom))
        implementation(libs.firebase.analytics)
        implementation(libs.firebase.crashlytics)
    }

    // Better logging (EH)
    implementation(sylibs.xlog)
}
