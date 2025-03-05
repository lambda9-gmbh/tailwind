plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")
    id("org.jetbrains.dokka")
}

mavenPublishing {
    coordinates("de.lambda9.tailwind", "tailwind-kio-compat", project.version as String)
    pom {
        name.set("Tailwind KIO Compat")
        description.set("A library used for old applications in the lambda9 GmbH.")
    }
}

dependencies {
    implementation(project(":tailwind-kio"))
    testImplementation(kotlin("test"))
    implementation(libs.kotlinLogging)
    testImplementation(libs.slf4j)
}

