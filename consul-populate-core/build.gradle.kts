plugins {
    id("io.micronaut.minimal.library") version "4.5.3"
    id("com.frogdevelopment.jreleaser.publish-convention")
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
    annotationProcessor(mn.micronaut.validation.processor)

    implementation(mn.micronaut.jackson.databind)
    implementation(mn.micronaut.validation)
    implementation(libs.vertx.consul)
    implementation(libs.commons.io)
    implementation(mn.snakeyaml)

    api("org.eclipse.jgit:org.eclipse.jgit:6.10.0.202406032230-r")

    compileOnly(mn.lombok)

    runtimeOnly(mn.logback.classic)

    testImplementation(libs.jetbrains.annotations)
    testImplementation(mn.assertj.core)
    testImplementation(mn.junit.jupiter.params)
    testImplementation(mn.mockito.junit.jupiter)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.consul)
}

publishing {
    publications {
        named<MavenPublication>("mavenJava") {
            from(components["java"])

            pom {
                name = "Consul Populate - Core"
                description = "Core library for Consul Populate"
            }
        }
    }
}
