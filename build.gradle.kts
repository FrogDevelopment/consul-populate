plugins {
    java
    id("com.frogdevelopment.jreleaser.deploy-convention")
}

version = "1.2.0-SNAPSHOT"
group = "com.frogdevelopment.consul.populate"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        vendor = JvmVendorSpec.ADOPTIUM
    }
}
