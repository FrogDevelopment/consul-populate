plugins {
    java
    `maven-publish`
    signing
}

java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            versionMapping {
                usage("java-api") {
                    fromResolutionOf("runtimeClasspath")
                }
                usage("java-runtime") {
                    fromResolutionResult()
                }
            }

            pom {
                description = project.description
                url = "https://github.com/FrogDevelopment/consul-populate/wiki"
                inceptionYear = "2024"
                issueManagement {
                    system = "GitHub"
                    url = "https://github.com/FrogDevelopment/consul-populate/issues"
                }
                developers {
                    developer {
                        id = "FrogDevelopper"
                        name = "Le Gall Benoît"
                        email = "legall.benoit@gmail.com"
                        url = "https://github.com/FrogDevelopper"
                        timezone = "Europe/Paris"
                    }
                }
                licenses {
                    license {
                        name = "The Apache License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }
                scm {
                    connection = "scm:git:git://github.com/FrogDevelopment/consul-populate.git"
                    developerConnection = "scm:git:ssh://github.com:FrogDevelopment/consul-populate.git"
                    url = "https://github.com/FrogDevelopment/consul-populate/tree/master"
                }
                distributionManagement {
                    downloadUrl = "https://github.com/FrogDevelopment/consul-populate/releases"
                }
            }
        }
    }

    repositories {
        maven {
            name = "sonatype"
            url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = System.getenv("OSSRH_USERNAME")
                password = System.getenv("OSSRH_TOKEN")
            }
        }

        maven {
            name = "github"
            url = uri("https://maven.pkg.github.com/FrogDevelopment/consul-populate")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
val releaseVersion = """^\d+\.\d+\.\d+$""".toRegex().matches(version.toString())
signing {
    if (releaseVersion) {
        val signingKey: String? by project
        val signingPassword: String? by project
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["mavenJava"])
    }
}

tasks {
    matching { it is PublishToMavenRepository && it.repository.name == "sonatype" }.all {
        onlyIf { releaseVersion }
    }
    matching { it is PublishToMavenRepository && it.repository.name == "github" }.all {
        onlyIf { !releaseVersion }
    }
}
