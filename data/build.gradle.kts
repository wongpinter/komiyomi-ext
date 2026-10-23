plugins {
    // KMK -->
    id("mihon.data")
    // KMK <--
}

// KMK -->
extensions.configure<mihon.buildlogic.AndroidLibraryConfig>("komiyomiAndroid") {
    namespace("tachiyomi.data")
    consumerProguardFiles("consumer-rules.pro")
    compilerOptIns("kotlinx.serialization.ExperimentalSerializationApi")
}
// KMK <--

dependencies {
    implementation(projects.sourceApi)
    implementation(projects.domain)
    implementation(projects.core.common)

    implementation(kotlinx.serialization.json)
    implementation(kotlinx.serialization.json.okio)
    implementation(kotlinx.serialization.protobuf)

    api(libs.bundles.sqldelight)
}
