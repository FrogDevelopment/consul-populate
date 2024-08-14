plugins {
    java
    id("com.frogdevelopment.version-convention")
    id("com.frogdevelopment.jreleaser.deploy-convention")
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

tasks {
    wrapper {
        gradleVersion = "8.10"
        distributionType = Wrapper.DistributionType.ALL
    }
}
