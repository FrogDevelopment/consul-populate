plugins {
    java
    id("com.frogdevelopment.jreleaser.deploy-convention")
    id("org.sonarqube") version "7.1.0.6387"
    jacoco
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

sonar {
    properties {
        property("sonar.projectKey", "FrogDevelopment_consul-populate")
        property("sonar.organization", "frogdevelopment")
        property("sonar.junit.reportPaths", "build/test-results/test/")
        property("sonar.inclusions", "**/src/main/**/*")
        property("sonar.test.exclusions", "**/src/test/**/*")
    }
}

testing {
    suites {
        // Configure the built-in test suite
        val test by getting(JvmTestSuite::class) {
            // Use JUnit Jupiter test framework
            useJUnitJupiter(mn.versions.junit5)
        }
    }
}
