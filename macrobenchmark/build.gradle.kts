plugins {
    // KMK -->
    id("mihon.benchmark")
    // KMK <--
}

dependencies {
    implementation(androidx.test.ext)
    implementation(androidx.test.espresso.core)
    implementation(androidx.test.uiautomator)
    implementation(androidx.benchmark.macro)
}
