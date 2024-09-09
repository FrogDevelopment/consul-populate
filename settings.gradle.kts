plugins {
    id("io.micronaut.platform.catalog") version "4.4.3"
    id("org.gradle.toolchains.foojay-resolver-convention") version ("0.8.0")
}

rootProject.name = "consul-populate"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(
    "consul-populate-cli",
    "consul-populate-core",
    "consul-populate-server"
)
