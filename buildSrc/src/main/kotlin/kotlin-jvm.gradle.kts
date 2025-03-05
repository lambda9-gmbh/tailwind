// The code in this file is a convention plugin - a Gradle mechanism for sharing reusable build logic.
// `buildSrc` is a Gradle-recognized directory and every plugin there will be easily available in the rest of the build.
package buildsrc.convention

import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    // Apply the Kotlin JVM plugin to add support for Kotlin in JVM projects.
    kotlin("jvm")
    id("org.jetbrains.dokka")
    id("com.vanniktech.maven.publish")
}

repositories {
    mavenCentral()
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

    pom {
        name.set(project.name)
        description.set(project.description)
        url.set("https://github.com/lambda9-gmbh/tailwind/")
        licenses {
            license {
                license {
                    name.set("MIT")
                    url.set("https://opensource.org/license/mit")
                }
            }
        }
        developers {
            developer {
                id.set("noobymatze")
                name.set("Matthias Metzger")
                url.set("https://github.com/noobymatze/")
                email.set("mme@lambda9.de")
            }
        }

        scm {
            connection.set("scm:git:git://github.com/lambda9-gmbh/tailwind.git")
            developerConnection.set("scm:git:ssh://github.com/lambda9-gmbh/tailwind.git")
            url.set("https://github.com/lambda9-gmbh/tailwind")
        }
    }

    signAllPublications()
}

dokka {

}

kotlin {
    // Use a specific Java version to make it easier to work in different environments.
    jvmToolchain(17)
}

tasks.withType<Test>().configureEach {
    // Configure all test Gradle tasks to use JUnitPlatform.
    useJUnitPlatform()

    // Log information about all test results, not only the failed ones.
    testLogging {
        events(
            TestLogEvent.FAILED,
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED
        )
    }
}
