plugins {
    id("io.micronaut.minimal.application") version "5.0.2"
    id("com.frogdevelopment.jreleaser.publish-convention")
    id("com.frogdevelopment.jacoco")
    alias(libs.plugins.jib)
    alias(libs.plugins.shadow)
}

micronaut {
    runtime("none")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("com.frogdevelopment.*")
    }
}

dependencies {
    annotationProcessor(mn.lombok)
    annotationProcessor(mn.picocli.codegen)

    implementation(mn.picocli)
    implementation(mn.micronaut.picocli)
    implementation(projects.consulPopulateCore)

    compileOnly(mn.lombok)

    runtimeOnly(mn.logback.classic)
    runtimeOnly(mn.snakeyaml)

    testImplementation(mn.assertj.core)
    testImplementation(mn.mockito.junit.jupiter)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.consul)
    testImplementation(libs.vertx.consul)
    testImplementation(libs.systemlambda)

    testRuntimeOnly(mn.junit.jupiter.engine)
    testRuntimeOnly(mn.junit.platform.launcher)
}

application {
    mainClass.set("com.frogdevelopment.consul.populate.ConsulPopulateCommand")
}

publishing {
    publications {
        named<MavenPublication>("mavenJava") {
            from(components["shadow"])
            artifact(tasks.javadocJar)
            artifact(tasks.sourcesJar)

            pom {
                name = "Consul Populate - CLI"
                description = "CLI executable for Consul Populate"
            }
        }
    }
}

tasks {
    jar {
        enabled = false
    }

    shadowJar {
        archiveClassifier = ""
    }

    test {
        // https://github.com/stefanbirkner/system-lambda/issues/27
        systemProperty("java.security.manager", "allow")

        // the environment variables test runs the packaged fat JAR in its own JVM
        dependsOn(shadowJar)
        doFirst {
            systemProperty("cli.jar", shadowJar.get().archiveFile.get().asFile.absolutePath)
        }
    }

    jib {

        from {
            image = "eclipse-temurin:21-jre-alpine"
            platforms {
                platform {
                    architecture = "amd64"
                    os = "linux"
                }
                platform {
                    architecture = "arm64"
                    os = "linux"
                }
            }
        }

        to {
            image = "frogdevelopment/consul-populate:${rootProject.version}"
            if (System.getenv("CI") == "true") {
                auth {
                    username = System.getenv("DOCKER_USR")
                    password = System.getenv("DOCKER_PSW")
                }
            }
        }

        container {
            setMainClass(application.mainClass)
        }

    }
}
