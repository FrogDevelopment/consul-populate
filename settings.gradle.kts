plugins {
    id("io.micronaut.platform.catalog") version "4.3.8"
}

rootProject.name = "consul-populate"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(
    "consul-populate-cli",
    "consul-populate-core"
)
