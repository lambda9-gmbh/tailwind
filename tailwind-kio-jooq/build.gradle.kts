plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")
    id("org.jetbrains.dokka")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "de.lambda9.tailwind"
            artifactId = "tailwind-kio-jooq"
            version = "1.0.0-SNAPSHOT"

            from(components["java"])
        }
    }
}

dependencies {
    // Apply the kotlinx bundle of dependencies from the version catalog (`gradle/libs.versions.toml`).
    implementation(libs.jooq)
    implementation(libs.hikari)
    implementation(libs.kotlinLogging)
    implementation(project(":tailwind-kio"))
    testImplementation(kotlin("test"))
    testImplementation(libs.flyway)
    testImplementation(libs.flywayPostgres)
    testImplementation(libs.postgres)
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainersJunit)
    testImplementation(libs.testcontainersPostgres)
}