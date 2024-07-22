plugins {
    id("io.micronaut.platform.catalog") version "4.4.2"
}

rootProject.name = "consul-populate"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(
    "consul-populate-cli",
    "consul-populate-core"
)
