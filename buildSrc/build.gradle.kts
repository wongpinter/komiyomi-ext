plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(androidx.gradle)
    implementation(kotlinx.gradle)
    // KMK -->
    implementation(kotlinx.serialization.gradle)
    // KMK <--
    implementation(kotlinx.compose.compiler.gradle)
    // KMK -->
    implementation("app.cash.sqldelight:gradle-plugin:${libs.versions.sqldelight.get()}")
    // KMK <--
// KMK -->
    implementation("dev.icerock.moko:resources-generator:${libs.versions.moko.get()}")
    // KMK <--
    implementation(libs.spotless.gradle)
    implementation(gradleApi())

    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
    implementation(files(androidx.javaClass.superclass.protectionDomain.codeSource.location))
    implementation(files(compose.javaClass.superclass.protectionDomain.codeSource.location))
    implementation(files(kotlinx.javaClass.superclass.protectionDomain.codeSource.location))
}

repositories {
    gradlePluginPortal()
    mavenCentral()
    google()
}
