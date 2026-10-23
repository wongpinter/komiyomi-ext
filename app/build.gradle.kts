import mihon.buildlogic.Config

plugins {
    id("mihon.android.application")
    id("mihon.android.application.compose")
    id("com.github.zellius.shortcut-helper")
    kotlin("plugin.parcelize")
    kotlin("plugin.serialization")
    alias(libs.plugins.aboutLibraries)
    id("com.github.ben-manes.versions")
}

if (Config.includeTelemetry) {
    pluginManager.apply {
        apply(libs.plugins.google.services.get().pluginId)
        apply(libs.plugins.firebase.crashlytics.get().pluginId)
    }
}

shortcutHelper.setFilePath("./shortcuts.xml")

// KMK -->
val epubNavigatorReference by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}
val epubPhotoViewReference by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}
val verifyEpubPhotoView by tasks.registering(mihon.buildlogic.VerifyEpubPhotoView::class) {
    navigatorAar.set(layout.file(providers.provider { epubNavigatorReference.singleFile }))
    photoViewAar.set(layout.file(providers.provider { epubPhotoViewReference.singleFile }))
}
configurations.configureEach {
    if (name.endsWith("RuntimeClasspath")) {
        // Readium embeds the EXACT existing PhotoView 2.3.0 classes; verified before use.
        exclude(group = "com.github.chrisbanes", module = "PhotoView")
    }
}
tasks.matching {
    (it.name.startsWith("check") && it.name.endsWith("DuplicateClasses")) ||
        (it.name.startsWith("test") && it.name.endsWith("UnitTest"))
}.configureEach {
    dependsOn(verifyEpubPhotoView)
}
// KMK <--

dependencies {
    // KMK -->
    add(epubNavigatorReference.name, libs.readium.navigator)
    add(epubPhotoViewReference.name, libs.photoview)
    // KMK <--
    implementation(projects.i18n)
    // KMK -->
    implementation(projects.i18nKmk)
    // KMK <--
    // SY -->
    implementation(projects.i18nSy)
    // SY <--
    implementation(projects.core.archive)
    // KMK -->
    implementation(projects.core.epub)
    // KMK <--
    implementation(projects.core.common)
    implementation(projects.coreMetadata)
    implementation(projects.sourceApi)
    implementation(projects.sourceLocal)
    implementation(projects.data)
    implementation(projects.domain)
    implementation(projects.presentationCore)
    implementation(projects.presentationWidget)
    implementation(projects.telemetry)

    // Compose
    implementation(compose.activity)
    implementation(compose.foundation)
    implementation(compose.material3.core)
    implementation(compose.material.icons)
    implementation(compose.animation)
    implementation(compose.animation.graphics)
    debugImplementation(compose.ui.tooling)
    implementation(compose.ui.tooling.preview)
    implementation(compose.ui.util)

    implementation(androidx.interpolator)

    implementation(androidx.paging.runtime)
    implementation(androidx.paging.compose)

    implementation(androidx.sqlite.bundled)

    // SY -->
    implementation(sylibs.sqlcipher)
    // SY <--

    implementation(kotlinx.reflect)
    implementation(kotlinx.immutables)

    implementation(platform(kotlinx.coroutines.bom))
    implementation(kotlinx.bundles.coroutines)

    // AndroidX libraries
    implementation(androidx.annotation)
    implementation(androidx.appcompat)
    implementation(androidx.biometricktx)
    implementation(androidx.constraintlayout)
    implementation(androidx.corektx)
    implementation(androidx.splashscreen)
    implementation(androidx.recyclerview)
    implementation(androidx.viewpager)
    implementation(androidx.profileinstaller)

    implementation(androidx.bundles.lifecycle)

    // Job scheduling
    implementation(androidx.workmanager)

    // RxJava
    implementation(libs.rxjava)

    // Networking
    implementation(libs.bundles.okhttp)
    implementation(libs.okio)
    implementation(libs.conscrypt.android) // TLS 1.3 support for Android < 10

    // Data serialization (JSON, protobuf, xml)
    implementation(kotlinx.bundles.serialization)

    // HTML parser
    implementation(libs.jsoup)

    // Disk
    implementation(libs.disklrucache)
    implementation(libs.unifile)

    // Preferences
    implementation(libs.preferencektx)

    // Dependency injection
    implementation(libs.injekt)

    // Image loading
    implementation(platform(libs.coil.bom))
    implementation(libs.bundles.coil)
    implementation(libs.subsamplingscaleimageview) {
        exclude(module = "image-decoder")
    }
    implementation(libs.image.decoder)

    // UI libraries
    implementation(libs.material)
    implementation(libs.flexibleAdapter)
    implementation(libs.photoview)
    implementation(libs.directionalviewpager) {
        exclude(group = "androidx.viewpager", module = "viewpager")
    }
    implementation(libs.richeditor.compose)
    implementation(libs.aboutLibraries.compose)
    implementation(libs.bundles.voyager)
    implementation(libs.compose.materialmotion)
    implementation(libs.swipe)
    implementation(libs.compose.webview)
    implementation(libs.compose.grid)
    implementation(libs.reorderable)
    implementation(libs.bundles.markdown)
    implementation(libs.materialKolor)

    // KMK -->
    implementation(libs.palette.ktx)
    implementation(libs.haze)
    implementation(compose.colorpicker)
    implementation(projects.flagkit)
    // KMK <--

    // KMK -->
    implementation("io.legere:pdfiumandroid:2.0.0")
    // KMK <--

    // Logging
    implementation(libs.timber)
    implementation(libs.logcat)

    // Shizuku
    implementation(libs.bundles.shizuku)

    // String similarity
    implementation(libs.stringSimilarity)

    // Tests
    testImplementation(libs.bundles.test)
    // KMK -->
    testImplementation("net.sf.kxml:kxml2:2.3.0")
    // KMK <--
    testRuntimeOnly(libs.junit.platform.launcher)

    // For detecting memory leaks; see https://square.github.io/leakcanary/
    // debugImplementation(libs.leakcanary.android)
    implementation(libs.leakcanary.plumber)

    testImplementation(kotlinx.coroutines.test)

    // SY -->
    // Better logging (EH)
    implementation(sylibs.xlog)

    // RatingBar (SY)
    implementation(sylibs.ratingbar)
    implementation(sylibs.composeRatingbar)

    // Google drive
    implementation(sylibs.google.api.services.drive)

    // ZXing Android Embedded
    implementation(sylibs.zxing.android.embedded)
}

buildscript {
    dependencies {
        classpath(kotlinx.gradle)
    }
}
