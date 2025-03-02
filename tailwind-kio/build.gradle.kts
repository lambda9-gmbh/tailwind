plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")
    id("org.jetbrains.dokka")
}

publishing {
    publications {
        named<MavenPublication>("maven") {
            pom {
                name = "Tailwind KIO"
                description = "A library used in the lambda9 GmbH to work with IO"
            }
        }
    }
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(libs.kotlinLogging)
    implementation("org.slf4j:slf4j-api:2.0.5")
}

