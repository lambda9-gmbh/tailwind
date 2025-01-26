plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")
    id("org.jetbrains.dokka")
}

dependencies {
    // Project "app" depends on project "utils". (Project paths are separated with ":", so ":utils" refers to the top-level "utils" project.)
    testImplementation(kotlin("test"))
    implementation(libs.kotlinLogging)
    implementation("org.slf4j:slf4j-api:2.0.5")
}

