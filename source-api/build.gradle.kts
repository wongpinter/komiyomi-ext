plugins {
    // KMK -->
    id("mihon.source-api")
    // KMK <--
    id("com.github.ben-manes.versions")
}

// KMK -->
dependencies {
    add("commonMainApi", kotlinx.serialization.json)
    add("commonMainApi", libs.injekt)
    add("commonMainApi", libs.rxjava)
    add("commonMainApi", libs.jsoup)

    // SY -->
    add("commonMainApi", projects.i18n)
    add("commonMainApi", projects.i18nSy)
    add("commonMainApi", kotlinx.reflect)
    // SY <--

    add("commonMainImplementation", project.dependencies.platform(compose.bom))
    add("commonMainImplementation", compose.runtime)
    add("androidMainImplementation", projects.core.common)
    add("androidMainApi", libs.preferencektx)

    // Workaround for https://youtrack.jetbrains.com/issue/KT-57605
    add("androidMainImplementation", kotlinx.coroutines.android)
    add("androidMainImplementation", project.dependencies.platform(kotlinx.coroutines.bom))
}
// KMK <--

// KMK -->
extensions.configure<mihon.buildlogic.AndroidLibraryConfig>("komiyomiAndroid") {
    namespace("eu.kanade.tachiyomi.source")
    consumerProguardFiles("consumer-proguard.pro")
}
// KMK <--
