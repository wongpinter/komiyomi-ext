buildscript {
    dependencies {
        classpath(libs.android.shortcut.gradle)
        classpath(sylibs.gradleversionsx)
    }
}

plugins {
    alias(libs.plugins.aboutLibraries) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.google.services) apply false
    // KMK -->
    // Moko is applied by the i18n convention plugin to keep its typed DSL on the plugin classpath.
    // KMK <--
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
