pluginManagement {
    // KMK -->
    buildscript {
        repositories {
            google()
            mavenCentral()
            maven(url = "https://storage.googleapis.com/r8-releases/raw") {
                content {
                    includeModule("com.android.tools", "r8")
                }
            }
        }
        dependencies {
            // Kotlin 2.4 metadata requires R8 9.1.29 or newer.
            classpath("com.android.tools:r8:9.1.29")
        }
    }
    // KMK <--
    resolutionStrategy {
        eachPlugin {
            val regex = "com.android.(library|application)".toRegex()
            if (regex matches requested.id.id) {
                useModule("com.android.tools.build:gradle:${requested.version}")
            }
        }
    }
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven(url = "https://www.jitpack.io")
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("kotlinx") {
            from(files("gradle/kotlinx.versions.toml"))
        }
        create("androidx") {
            from(files("gradle/androidx.versions.toml"))
        }
        create("compose") {
            from(files("gradle/compose.versions.toml"))
        }
        create("sylibs") {
            from(files("gradle/sy.versions.toml"))
        }
    }
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
        maven(url = "https://www.jitpack.io")
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// KMK -->
rootProject.name = "Komiyomi-Ext"
// KMK <--
include(":app")
include(":core-metadata")
include(":core:archive")
// KMK -->
include(":core:epub")
// KMK <--
include(":core:common")
include(":data")
include(":domain")
include(":i18n")
// KMK -->
include(":i18n-kmk")
include(":flagkit")
// KMK <--
// SY -->
include(":i18n-sy")
// SY <--
include(":macrobenchmark")
include(":presentation-core")
include(":presentation-widget")
include(":source-api")
include(":source-local")
include(":telemetry")
