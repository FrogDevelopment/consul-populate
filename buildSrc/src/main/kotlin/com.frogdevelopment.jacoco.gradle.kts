plugins {
    java
    jacoco
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
