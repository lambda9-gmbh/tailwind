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
            artifactId = "tailwind-kio-compat"
            version = "1.0.0-SNAPSHOT"

            from(components["java"])
        }
    }
}


dependencies {
    implementation(project(":tailwind-kio"))
    testImplementation(kotlin("test"))
    implementation(libs.kotlinLogging)
    implementation("org.slf4j:slf4j-api:2.0.5")
}

