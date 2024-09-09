plugins {
    id("io.micronaut.minimal.application") version "4.4.2"
    id ("com.google.cloud.tools.jib") version "3.4.3"
    id("com.frogdevelopment.jreleaser.publish-convention")
}

micronaut {
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("com.frogdevelopment.*")
    }
}

dependencies {
    annotationProcessor(mn.lombok)

    implementation(projects.consulPopulateCore)
    implementation("commons-io:commons-io:2.17.0")

    compileOnly(mn.lombok)

    runtimeOnly(mn.logback.classic)
    runtimeOnly(mn.snakeyaml)

    testImplementation(libs.jetbrains.annotations)
    testImplementation(mn.assertj.core)
    testImplementation(mn.junit.jupiter.params)
    testImplementation(mn.mockito.junit.jupiter)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.consul)
    testImplementation("org.awaitility:awaitility:4.2.2")
    testCompileOnly(mn.lombok)
}

application {
    mainClass.set("com.frogdevelopment.consul.populate.ConsulPopulateApplication")
}

publishing {
    publications {
        named<MavenPublication>("mavenJava") {
            pom {
                name = "Consul Populate - Server"
                description = "Server library for Consul Populate"
            }
        }
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
        image = "frogdevelopment/${name}:${rootProject.version}"
        if (System.getenv("CI") == "true") {
            auth {
                username = System.getenv("DOCKER_USR")
                password = System.getenv("DOCKER_PSW")
            }
        }
    }

    container {
        setMainClass(application.mainClass)

        jvmFlags = listOf("-Xmx128m")
        volumes = listOf("/tmp")
        creationTime = "USE_CURRENT_TIMESTAMP"
        labels.put("frog.image_base", jib.from.image.toString())
    }
}
