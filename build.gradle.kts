plugins {
}

allprojects {
    val version = (property("version") as? String).let { version ->
        if (version == null || version == "unspecified") "0.1.0-SNAPSHOT"
        else version
    }
    setProperty("version", version)
}

subprojects {
    apply(plugin = "maven-publish")
    apply(plugin = "signing")
    configure<PublishingExtension> {
        repositories {
            maven {
                val releasesRepoUrl = "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
                val snapshotsRepoUrl = "https://s01.oss.sonatype.org/content/repositories/snapshots/"
                url = uri(if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl)

                credentials {
                    username = property("ossrhUsername") as String
                    password = property("ossrhPassword") as String
                }
            }
        }

        publications {
            create<MavenPublication>("maven") {
                groupId = "de.lambda9.tailwind"
                artifactId = project.name
                version = project.version.toString()

                pom {
                    name = project.name
                    url = "https://github.com/lambda9-gmbh/tailwind"
                    licenses {
                        license {
                            name = "MIT"
                            url = "https://opensource.org/license/mit"
                        }
                    }
                    developers {
                        developer {
                            id = "mmetzger"
                            name = "Matthias Metzger"
                            email = "mme@lambda9.de"
                        }
                    }
                    scm {
                        connection = "scm:git:git://github.com/lambda9-gmbh/tailwind.git"
                        developerConnection = "scm:git:ssh://github.com/lambda9-gmbh/tailwind.git"
                        url = "https://github.com/lambda9-gmbh/tailwind"
                    }
                }
            }
        }

    }

    configure<SigningExtension> {
        val signingKey: String? by project
        val signingPassword: String? by project
        if (!signingKey.isNullOrEmpty() && !signingPassword.isNullOrEmpty()) {
            useInMemoryPgpKeys(signingKey, signingPassword)
        }

        val publishing = extensions.getByType(PublishingExtension::class.java)
        sign(publishing.publications["maven"])
    }
}