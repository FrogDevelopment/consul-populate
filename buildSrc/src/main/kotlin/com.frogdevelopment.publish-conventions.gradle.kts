import org.jreleaser.model.Active
import org.jreleaser.model.Signing.Mode

plugins {
    java
    `maven-publish`
    id("org.jreleaser")
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
            name = "jreleaser"
            url = uri(layout.buildDirectory.dir("staging-deploy"))
        }
    }
}

val isReleaseVersion = """^\d+\.\d+\.\d+$""".toRegex().matches(version.toString())

jreleaser {
    gitRootSearch = true
    dependsOnAssemble = true
    dryrun = !isReleaseVersion

    signing {
        active = Active.ALWAYS
        armored = true
        verify = false
        mode = Mode.MEMORY
    }

    deploy {
        maven {
            mavenCentral {
                create("app") {
                    active = Active.ALWAYS
                    url = "https://central.sonatype.com/api/v1/publisher"
                    applyMavenCentralRules = true
                    stagingRepository(layout.buildDirectory.dir("staging-deploy").get().toString())
                    snapshotSupported = true
                }
            }
        }
    }
}

tasks {
    jreleaserDeploy {
        dependsOn(jreleaserSign)
    }

    jreleaserSign {
        dependsOn(publish)
    }

}
