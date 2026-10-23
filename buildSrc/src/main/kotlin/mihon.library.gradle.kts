// KMK -->
import mihon.buildlogic.AndroidLibraryConfig
// KMK <--
import mihon.buildlogic.configureAndroid
import mihon.buildlogic.configureTest

plugins {
    id("com.android.library")

    id("mihon.code.lint")
}

// KMK -->
val komiyomiAndroid = extensions.create<AndroidLibraryConfig>("komiyomiAndroid", project)
// KMK <--

android {
    configureAndroid(this)
    configureTest()
}
