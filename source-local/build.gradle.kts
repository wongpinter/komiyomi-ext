plugins {
    // KMK -->
    id("mihon.source-local")
    // KMK <--
}

// KMK -->
dependencies {
    add("commonMainImplementation", projects.sourceApi)
    add("commonMainApi", projects.i18n)
    // SY -->
    add("commonMainApi", projects.i18nSy)
    // SY <--
    add("commonMainImplementation", libs.unifile)

    add("androidMainImplementation", projects.core.archive)
    add("androidMainImplementation", projects.core.common)
    add("androidMainImplementation", projects.coreMetadata)

    // Move ChapterRecognition to separate module?
    add("androidMainImplementation", projects.domain)
    add("androidMainImplementation", kotlinx.bundles.serialization)
}
// KMK <--

// KMK -->
extensions.configure<mihon.buildlogic.AndroidLibraryConfig>("komiyomiAndroid") {
    namespace("tachiyomi.source.local")
    testInstrumentationRunner("androidx.test.runner.AndroidJUnitRunner")
    consumerProguardFiles("consumer-rules.pro")
}
// KMK <--
