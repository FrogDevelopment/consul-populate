plugins {
    java
    id("com.frogdevelopment.jreleaser.deploy-convention")
    id("org.sonarqube") version "7.2.0.6526"
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

tasks {
    test {
        finalizedBy(jacocoTestReport)
    }

    jacocoTestReport {
        dependsOn(test)

        reports {
            xml.required.set(true)
            html.required.set(false)
        }
    }
}

