plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")
    id("org.jetbrains.dokka")
}

mavenPublishing {
    coordinates("de.lambda9.tailwind", "tailwind-kio", project.version as String)
    pom {
        name.set("Tailwind KIO")
        description.set("A library used in the lambda9 GmbH to work with IO")
    }
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(libs.kotlinLogging)
}

