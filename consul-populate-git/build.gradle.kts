plugins {
    id("io.micronaut.minimal.library") version "4.6.1"
    id("com.frogdevelopment.jreleaser.publish-convention")
    id("com.frogdevelopment.jacoco")
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

    implementation(mn.micronaut.context)
    implementation(mn.micronaut.management)
    implementation(mn.micronaut.validation)
    implementation(mn.micronaut.jackson.databind)
    implementation(projects.consulPopulateCore)
    implementation(libs.jgit)
    implementation(libs.commons.io)
    implementation(libs.commons.lang3)

    compileOnly(mn.lombok)

    runtimeOnly(mn.logback.classic)

    testImplementation(mn.assertj.core)
    testImplementation(mn.junit.jupiter.params)
    testImplementation(mn.mockito.junit.jupiter)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.consul)
    testImplementation(libs.vertx.consul)
    testImplementation(libs.systemlambda)
    testImplementation(mn.logback.classic)

    testRuntimeOnly(mn.junit.jupiter.engine)
    testRuntimeOnly(mn.junit.platform.launcher)
    testRuntimeOnly(mn.snakeyaml)
}

publishing {
    publications {
        named<MavenPublication>("mavenJava") {
            from(components["java"])

            pom {
                name = "Consul Populate - Git"
                description = "Git library for Consul Populate"
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

