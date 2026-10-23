import mihon.buildlogic.configureAndroid
import mihon.buildlogic.configureTest

plugins {
    id("com.android.test")
    kotlin("android")

    id("mihon.code.lint")
}

android {
    configureAndroid(this)
    configureTest()
    // KMK -->
    namespace = "tachiyomi.macrobenchmark"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.enabledRules"] = "BaselineProfile"
    }

    buildTypes {
        // This benchmark buildType is used for benchmarking, and should function like your
        // release build (for example, with minification on). It's signed with a debug key
        // for easy local/CI testing.
        create("benchmark") {
            isDebuggable = true
            signingConfig = getByName("debug").signingConfig
            matchingFallbacks.add("release")
        }
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
    // KMK <--
}

 // KMK -->
androidComponents {
    beforeVariants(selector().all()) {
        it.enable = it.buildType == "benchmark"
    }
}
// KMK <--
