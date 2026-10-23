// KMK -->
import mihon.buildlogic.libs

plugins {
    id("mihon.library")
    kotlin("android")
    kotlin("plugin.serialization")
    id("app.cash.sqldelight")
}

sqldelight {
    databases {
        create("Database") {
            packageName.set("tachiyomi.data")
            dialect(libs.sqldelight.dialects.sql)
            schemaOutputDirectory.set(project.file("./src/main/sqldelight"))
        }
    }
}
// KMK <--
