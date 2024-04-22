plugins {
    id("io.micronaut.minimal.library") version "4.3.6"
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

    implementation(mn.micronaut.jackson.databind)
    implementation(libs.vertx.consul)
    implementation(mn.snakeyaml)
    implementation(libs.commons.io)

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
